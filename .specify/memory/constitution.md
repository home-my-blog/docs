# MyBlog Constitution

## Core Principles

### I. 요구사항 문서가 기준이다 (Requirements Are the Source of Truth)

`docs/`의 요구사항 문서(요구사항.md, 요구사항-공통.md, 상세/)가 무엇을 만들지의 기준이다(MUST).
spec, plan, tasks, 코드, 테스트는 근거가 되는 원본 ID(`CF-05-3`, `BM-06-3`, `NF-07` 등)를 적어
추적할 수 있어야 한다(MUST). 원본과 구현이 다르면 코드를 원본에 맞추거나, 원본을 먼저 고친 뒤
구현한다(MUST). 화면 문구는 원본의 `안내 문구` 표를 그대로 쓴다(SHOULD).

### II. 권한과 공개 범위는 서버가 지킨다 (Server-Enforced Access)

글·댓글·분류·블로그·관리 화면의 수정·삭제·접근 권한은 서버가 요청마다 검사한다(MUST, NF-02).
화면에서 버튼을 숨기는 것은 보조일 뿐이다. 비공개 글은 작성자 외 누구에게도 목록, 검색, 이전/다음 글,
홈, 주제 화면, 통계, 주소 접근 어디에서도 보이면 안 되고(MUST, CF-13-4), 없는 글과 같은 응답을
돌려준다(MUST, CF-09-4). 공개 글만 고르는 조건은 한 곳에 두고 모든 방문자용 조회가 그것을 쓴다(MUST).

### III. 단순하게 시작한다 (Simplicity)

서버는 기능별 패키지로 나눈 **단일 Spring Boot 서버**다(MUST). MSA, JWT, 메시지 큐(RabbitMQ·Kafka),
검색 전용 서버(Elasticsearch)는 쓰지 않는다([기술스택-아키텍처.md 4장](../../docs/기술스택-아키텍처.md)).
새 인프라 구성 요소는 spec의 요구사항을 그것 없이 지킬 수 없다는 근거를 plan.md에 적을 때만
들인다(MUST). 잃어도 되는 짧은 값만 Redis에 두고, 로그인은 Redis 없이도 동작해야 한다(MUST).

### IV. 핵심 흐름은 테스트로 지킨다 (Test the Critical Paths)

가입·이메일 인증·로그인·로그아웃, 글 쓰기·수정·삭제, 비공개 글 차단, 남의 자원에 대한 권한 거절은
서버 통합 테스트로 덮는다(MUST). 입력 규칙(글자 수, 형식, 비밀번호 규칙)은 단위 테스트로 덮는다(MUST).
P1 사용자 시나리오는 브라우저 종단 테스트로 확인한다(SHOULD). 테스트가 실패한 채로 main에 머지하지
않고, 테스트를 끄거나 건너뛰어 통과시키지 않는다(MUST).

### V. 안전한 기본값 (Secure by Default)

비밀번호는 BCrypt로만 저장하고 로그·오류 메시지에 남기지 않는다(MUST, NF-01). 세션 쿠키는 HttpOnly,
Secure, SameSite=Lax이고, 로그인 때 세션 ID를 새로 발급하며, 상태를 바꾸는 요청은 CSRF 토큰을
검사한다(MUST, NF-10~12). 본문·댓글은 화면에 낼 때 스크립트가 실행되지 않게 하고, SQL은 파라미터
바인딩으로만 만든다(MUST, NF-07). 오류 응답은 계정 존재 여부나 내부 구조를 드러내지 않는다(MUST,
NF-06). 비밀 값(DB·SMTP·MinIO 비밀번호)은 코드가 아니라 환경 변수로 받는다(MUST).

### VI. 기본값은 한곳에서 바꾼다 (Configurable Defaults)

글자 수, 용량, 잠금 시간, 인증번호 유효 시간, 페이지 크기, 인기 검색어 가중치 같은 숫자는 코드에
흩어 두지 않고 설정 한곳(`application.yml`의 `myblog.*`)에서 읽는다(MUST, NF-08). 화면에 같은 숫자가
필요하면 서버가 내려 주는 값을 쓴다(SHOULD).

## 기술·운영 제약

- 기술 스택은 [기술스택-아키텍처.md](../../docs/기술스택-아키텍처.md)를 따른다: React(Vite) 화면,
  Spring Boot(Java) 서버와 Spring Security, PostgreSQL(세션 포함), Redis(인증번호 등 짧은 값),
  이미지 저장소(MinIO 가안, `ImageStorage`로 감싸 서버 디스크로 교체 가능), SMTP 메일, Flyway,
  Docker Compose. 이 문서의 상태가 바뀌면 plan.md를 함께 고친다.
- DB 구조 변경은 Flyway 마이그레이션 파일로만 한다. 운영 DB를 손으로 고치지 않는다.
- 문서(spec, plan, tasks)와 화면 문구는 한국어, 코드·식별자·커밋 메시지는 영어로 쓴다.
- 날짜와 "하루"의 기준은 한국 시간(Asia/Seoul)이다.

## 개발 흐름

- 기능은 Spec Kit 흐름을 따른다: `/speckit-specify` → (필요하면 `/speckit-clarify`) →
  `/speckit-plan` → `/speckit-tasks` → `/speckit-implement`.
- 모든 변경은 브랜치와 PR로 들어오고, CI(빌드, 테스트, 린트)가 통과해야 머지한다.
- plan.md의 Constitution Check에서 위반이 있으면 Complexity Tracking 표에 이유를 적는다.

## Governance

이 문서는 다른 모든 개발 관행보다 우선한다. 고치려면 PR로 변경 내용과 이유를 제안하고 저장소 소유자의
승인을 받는다. 버전은 의미적 버전을 따른다: 원칙을 없애거나 뜻을 바꾸면 MAJOR, 원칙이나 절을 더하면
MINOR, 문구만 다듬으면 PATCH. PR 리뷰는 이 원칙을 지키는지 확인한다.

**Version**: 1.0.0 | **Ratified**: 2026-10-08 | **Last Amended**: 2026-10-08
