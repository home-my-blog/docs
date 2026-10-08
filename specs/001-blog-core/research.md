# Research: MyBlog 1차 개발 범위

기술 선택의 대부분은 [기술스택-아키텍처.md](../../docs/기술스택-아키텍처.md)에서 이미 정했다(React,
Spring Boot, PostgreSQL, Redis, MinIO, SMTP, Flyway, Docker Compose, 세션 방식, BCrypt). 여기에는
그 문서가 **미정·가안으로 남긴 것**과 **구현에 필요한 세부 결정**만 적는다.

## 1. 버전과 빌드 도구

- **Decision**: Java 21(LTS), Spring Boot 3.5.x, Gradle(Kotlin DSL). 화면은 Node.js 22, React 19,
  Vite, TypeScript.
- **Rationale**: 현재 지원 중인 LTS·안정 버전이다. Java 21의 record·switch 패턴으로 DTO와 검증 코드가
  짧아진다. Gradle은 Spring Initializr 기본값이고 IntelliJ(수연님 환경)와 잘 맞는다.
- **Alternatives considered**: Java 17(가능하지만 새로 시작하면 21이 유리), Maven(팀원이 Maven이면 바꿔도
  무방), JavaScript만 쓰는 React(타입이 없으면 API 응답 모양 실수가 늘어남)

## 2. 세션과 CSRF를 React와 함께 쓰는 방법

- **Decision**: Spring Session JDBC(세션은 PostgreSQL), 쿠키 이름 `MYBLOG_SESSION`, `HttpOnly`,
  `Secure`(배포), `SameSite=Lax`, 최대 비활성 7일 + 쿠키 만료 7일(쓸 때마다 갱신). CSRF는
  `CookieCsrfTokenRepository.withHttpOnlyFalse()`로 `XSRF-TOKEN` 쿠키를 내려 주고, React의 fetch 래퍼가
  `X-XSRF-TOKEN` 헤더에 실어 보낸다. 개발 중에는 Vite 프록시로 화면과 API를 같은 주소(`localhost:5173`)로
  묶는다. 로그인은 Spring Security의 폼 로그인 대신 `POST /api/auth/login` 컨트롤러에서
  `AuthenticationManager`를 직접 호출하고 `SecurityContextRepository`에 저장한다(JSON 응답과 잠금 규칙을
  직접 다루기 위해).
- **Rationale**: 기술스택 2.5와 01-인증-인가의 "확정" 결정을 그대로 쓰면서, SPA에서 쿠키 세션과 CSRF가
  동작하게 하는 표준 구성이다. `FindByIndexNameSessionRepository`로 회원 기준 세션을 찾아 지울 수 있어
  CF-15-15, CF-25-8을 지킨다.
- **Alternatives considered**: JWT(기술스택 4장에서 제외), CSRF 끄기(NF-11 위반)

## 3. 이메일 인증 키 설계 (Redis)

- **Decision**: 키에 용도(`signup`/`reset`)를 붙인다.
  - `verify:{purpose}:{email}:code` → 인증번호, TTL 10분
  - `verify:{purpose}:{email}:fails` → 틀린 횟수, TTL 10분, 5가 되면 code 삭제
  - `verify:{purpose}:{email}:cooldown` → TTL 60초 (1분에 1번)
  - `verify:{purpose}:{email}:daily:{yyyyMMdd}` → 하루 발송 수, TTL 24시간 (하루 5번)
  - `verify:{purpose}:{email}:verified` → 인증됨 표시, TTL 30분
  - 이메일은 소문자·앞뒤 공백 제거 후 키에 쓴다(CF-01-2).
  - 비밀번호 찾기에서 가입되지 않은 이메일은 code를 저장하지 않지만 cooldown·daily는 똑같이 올린다
    (CF-25-3, 계정 유무 숨기기).
- **Rationale**: 기술스택 2.4와 01-인증-인가 '구현 방식'의 키 설계를 구체화했다. 만료를 Redis가 처리해 정리
  작업이 필요 없다.
- **Alternatives considered**: DB 표에 인증번호 저장(만료 정리 작업 필요)

## 4. 메일 발송

- **Decision**: `MailSender` 인터페이스에 두 구현을 둔다. `SmtpMailSender`(Spring Mail, `@Async`)와
  `LogMailSender`(개발용, 서버 로그에 인증번호 출력). `myblog.mail.mode=log|smtp`로 고른다. 발송 계정
  (Gmail 앱 비밀번호 / 네이버 SMTP)은 `SMTP_HOST`, `SMTP_USERNAME`, `SMTP_PASSWORD` 환경 변수로 받는다.
- **Rationale**: 기술스택 2.7의 "개발 중에는 로그 출력"을 지키고, 발송 계정이 미정이어도 개발을 진행할 수
  있다. 비동기 발송이 실패하면 사용자는 결과를 바로 알 수 없으므로, 인증번호 요청 API는 SMTP 연결 확인
  (`JavaMailSender` 연결 테스트)이 실패하면 즉시 `MAIL_SEND_FAILED`를 돌려준다(CF-01-18).
- **Alternatives considered**: RabbitMQ로 발송(기술스택 2.8에서 "안 씀"), 외부 메일 API(외부 서비스 규칙
  예외를 늘림)

## 5. 이미지 저장

- **Decision**: `ImageStorage` 인터페이스(`put`, `delete`, `publicUrl`)와 두 구현 `S3ImageStorage`(AWS SDK
  v2, MinIO에 path-style로 연결)·`DiskImageStorage`. `myblog.image.storage=s3|disk`. 형식은 파일 앞부분의
  매직 넘버로 판별(jpg·png·gif·webp), 5MB, 글당 10장. 파일 이름은 `posts/{yyyy}/{MM}/{UUID}.{ext}`.
  이미지는 서버의 `GET /api/images/{key}`가 저장소에서 읽어 내려 준다(MinIO 포트를 밖에 열지 않기 위해).
  글을 지우면 `PostImage` 기록과 저장소 파일을 함께 지운다(파일 삭제는 트랜잭션 커밋 후).
  업로드했지만 글에 연결되지 않은 이미지는 24시간 뒤 스케줄러가 지운다.
- **Rationale**: 04-MinIO-현황과-선택.md의 권고(S3 표준 SDK, 인터페이스로 감싸기, 외부 포트 닫기)를 따른다.
- **Alternatives considered**: MinIO 공개 URL 직접 노출(포트를 열어야 함), presigned URL(배포 시 검토)

## 6. 글 본문과 화면 표시

- **Decision**: 본문은 마크다운 원문으로 저장(1~10,000자). 화면은 react-markdown + remark-gfm으로 그리고
  원시 HTML은 렌더링하지 않는다(`skipHtml`). 이미지·링크 주소는 `http(s):`와 `/api/images/`만 허용한다.
  목록의 본문 앞부분(100자)은 서버가 마크다운 기호를 걷어 낸 평문으로 만든다.
- **Rationale**: 기술스택 5장 "글 편집기: 마크다운 + 이미지 업로드 확정"과 NF-07을 함께 지킨다. 서버에
  HTML을 저장하지 않아 정화 규칙을 한 곳(화면 렌더러)에서만 관리한다.
- **Alternatives considered**: 서버에서 HTML로 바꿔 저장(정화 라이브러리와 저장 형식이 늘어남),
  WYSIWYG 편집기(원본 6장 "편집기 고급 기능 제외")

## 7. 검색

- **Decision**: PostgreSQL `pg_trgm` GIN 인덱스 + `ILIKE`. 검색어를 공백으로 나눠 각 단어를
  `ILIKE '%' || :word || '%' ESCAPE '\'`로 AND 결합하고, `%`·`_`·`\`는 이스케이프한다(CF-11-9). 통합
  검색은 블로그·글·커뮤니티를 각각 위 방식으로 찾아 나눠 돌려준다(요구사항.md 3.8의 대상 필드).
- **Rationale**: 04-탐색 '구현 방식'(단순 포함 검색)과 같다. 트라이그램 인덱스로 한국어 부분 일치도
  인덱스를 탄다.
- **Alternatives considered**: `tsvector`(한국어 형태소 없음), Elasticsearch(기술스택 4장 제외)

## 8. 실시간 인기 검색어

- **Decision**: 검색할 때마다 `search_log`에 정규화한 검색어(소문자, 공백 하나로)를 남긴다.
  `@Scheduled(fixedRate = 5s)`가 최근 1시간의 점수를 계산한다: 검색어별 검색 수×3 + 그 검색어가 제목에
  들어간 공개 글·커뮤니티 글의 최근 1시간 조회 수×1 + 댓글 수×5. 상위 10개와 직전 순위를 비교해
  ▲N/▼N/NEW/-를 붙이고 메모리(`AtomicReference`)에 둔다. 화면은 `GET /api/search/trending`을 5초마다
  부른다. 가중치·개수·주기·기간은 `myblog.trending.*`.
- **Rationale**: 기술스택 3장 G 흐름 그대로다. 서버 1대라 메모리로 충분하고, 서버를 다시 켜면 첫 계산에서
  복구된다.
- **Alternatives considered**: Redis Sorted Set(서버가 여러 대일 때 검토), WebSocket 푸시(3장 G "푸시 안 함")

## 9. 조회수와 방문자

- **Decision**: 글 상세 API가 조회를 기록한다. 방문자 구분값은 로그인 회원이면 `m:{memberId}`, 아니면
  `MYBLOG_VID` 쿠키(UUID, 1년)의 `v:{uuid}`. Redis `view:{postId}:{vid}`(TTL 30분)가 없으면 조회 +1,
  `visit:{blogId}:{vid}:{yyyyMMdd}`(TTL 한국 시간 자정까지)가 없으면 방문자 +1. 숫자는 `daily_stat`
  (blog_id, post_id null 가능, stat_date)에 `INSERT … ON CONFLICT DO UPDATE`로 바로 더하고 `post.view_count`도
  함께 올린다. 블로그 주인 본인 조회와 비공개 글은 세지 않는다. Redis가 꺼져 있으면 조회 기록을 건너뛴다
  (글 읽기는 실패하지 않는다).
- **Rationale**: 06-블로그관리-통계 '구현 방식'과 기술스택 3장 F를 따른다. 규모가 작아 바로 DB에 쓴다
  (기술스택 5장 추천).
- **Alternatives considered**: Redis에 모았다가 주기적으로 DB 반영(느려지면 전환)

## 10. 페이지 나누기

- **Decision**: 모든 목록 API는 `page`(1부터)·`size`(기본 `myblog.page-size`=10)를 받고
  `{ items, page, size, totalItems, totalPages }`를 돌려준다. `page`가 `totalPages`보다 크면 마지막 페이지를
  돌려주고 실제 `page` 값을 응답에 담는다(CF-10-4).
- **Rationale**: 원본이 페이지 번호 방식(CF-10-2, 커뮤니티 3.7)이다.

## 11. 탈퇴 처리

- **Decision**: 한 트랜잭션에서 내 블로그의 글(→ 댓글·좋아요·태그 연결·이미지 기록), 분류, 블로그,
  내가 누른 좋아요, 내 신고를 지우고, 남의 글에 단 댓글·커뮤니티 글·커뮤니티 댓글은 `author_id`를 NULL로
  바꾼 뒤 회원을 지운다. 그 회원의 세션을 모두 지운다. 이미지 파일은 커밋 후 삭제한다. 화면은 작성자가
  NULL이면 "탈퇴한 사용자"로 보여 준다.
- **Rationale**: CF-15-18, CF-18-6, 02-계정관리의 "DB에서 작성자 칸이 비어도 되게 설계". 회원 행을 지우므로
  같은 이메일·닉네임으로 재가입할 수 있다(CF-15-21).
- **Alternatives considered**: 탈퇴 플래그로 남기기(재가입 시 고유 제약과 충돌, 개인정보를 계속 보관)

## 12. 오늘의 이슈와 블로그 주제

- **Decision**: `post.featured`(기본 false)를 두고, 운영자가 SQL로 켠다(관리자 화면 없음, CF-23-1). 홈은
  `featured = true`인 공개 글을 작성 시각 내림차순 3개 보여 주고, 모자라면 최근 7일 조회수 상위 공개 글로
  채운다. 블로그는 `topic_id`를 가지며 가입 시 기본 주제는 `myblog.default-topic`(= `hobby`),
  블로그 설정에서 바꾼다. 주제 목록은 `topic` 표(시드: travel·food·hobby·exercise·dev)로 관리한다.
- **Rationale**: spec Assumptions의 기본값. 원본 요구사항.md 7장의 미정 항목이 정해지면 이 부분만 바꾼다.

## 13. 테스트와 CI

- **Decision**: 서버는 `./gradlew test`(JUnit 5, Testcontainers로 PostgreSQL·Redis·MinIO를 띄움, 메일은
  `LogMailSender`의 테스트용 캡처). 화면은 `npm run lint`, `npm run typecheck`, `npm test`(Vitest).
  종단은 `npx playwright test`(docker compose + 서버 + 화면을 띄운 상태). GitHub Actions에서 backend·frontend
  작업을 따로 돌리고, e2e는 main 머지 전에 돌린다.
- **Rationale**: 헌법 IV. Testcontainers로 실제와 같은 DB·Redis에서 권한·비공개 규칙을 검증한다.
- **Alternatives considered**: H2 테스트 DB(PostgreSQL 전용 문법 `pg_trgm`, `ON CONFLICT`를 못 씀)

## 14. 배포

- **Decision**: 이번 범위는 로컬 개발 환경까지. 배포는 [05-배포-준비.md](../../docs/가이드/05-배포-준비.md)에서
  환경이 정해지면 별도 기능으로 다룬다. 다만 서버는 `bootJar`, 화면은 `vite build` 결과를 서버의 정적 파일로
  함께 낼 수 있게 만들어 둔다(배포 단위 하나).
- **Rationale**: 기술스택 5장 "배포 환경 미정". 일정(2주) 안에서 기능을 먼저 끝낸다.
