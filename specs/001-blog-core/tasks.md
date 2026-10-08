---

description: "MyBlog 1차 개발 범위 작업 목록"
---

# Tasks: MyBlog 1차 개발 범위

**Input**: `/specs/001-blog-core/`의 plan.md, spec.md, research.md, data-model.md, contracts/

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api.md, contracts/screens.md

**Tests**: 헌법 IV에 따라 인증·글·권한·비공개 차단은 통합 테스트, 입력 규칙은 단위 테스트를 쓴다.
테스트 이름에는 원본 ID를 단다(예: `CF_13_4_privatePostHiddenFromOthers`).

**Organization**: 사용자 시나리오(US1~US8)별로 묶었다. 각 단계 끝의 Checkpoint에서 그 시나리오를 따로 확인할 수 있다.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일을 다루고 앞선 미완료 작업에 기대지 않아 동시에 할 수 있다
- **[Story]**: 해당 사용자 시나리오 (US1~US8)
- 경로: 서버 `backend/src/main/java/com/myblog/`(아래 `be/`), 서버 테스트 `backend/src/test/java/com/myblog/`(아래 `bt/`),
  마이그레이션 `backend/src/main/resources/db/migration/`(아래 `mig/`), 화면 `frontend/src/`(아래 `fe/`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 프로젝트 뼈대와 개발 인프라

- [ ] T001 저장소 루트에 `docker-compose.yml` 작성: `postgres:16`(5432, `POSTGRES_PASSWORD=${DB_PASSWORD}`, pg_trgm 사용), `redis:7`(6379), MinIO(9000/9001, 버전 고정, [04-MinIO](../../docs/가이드/04-MinIO-현황과-선택.md)) — 볼륨 포함
- [ ] T002 [P] 저장소 루트에 `.env.example`(DB_PASSWORD, MINIO_USER, MINIO_PASSWORD, MYBLOG_MAIL_MODE=log, SMTP_HOST/PORT/USERNAME/PASSWORD)와 `.gitignore`(`.env`, `build/`, `node_modules/`, `.idea/`) 작성
- [ ] T003 `backend/`에 Spring Boot 3.5 / Java 21 / Gradle Kotlin DSL 프로젝트 생성(`backend/build.gradle.kts`): web, security, session-jdbc, data-jpa, validation, data-redis, mail, flyway, postgresql, AWS SDK v2 s3, testcontainers(postgresql, junit), spring-security-test
- [ ] T004 [P] `frontend/`에 Vite + React 19 + TypeScript 프로젝트 생성(`frontend/package.json`): react-router, @tanstack/react-query, react-markdown, remark-gfm, recharts, vitest, @testing-library/react, eslint
- [ ] T005 [P] `frontend/vite.config.ts`에 `/api` → `http://localhost:8080` 프록시 설정 (research §2)
- [ ] T006 [P] 저장소 루트에 Playwright 설정 `e2e/playwright.config.ts`(데스크톱 + 360px 모바일 뷰포트 프로젝트 두 개)
- [ ] T007 [P] GitHub Actions `.github/workflows/ci.yml`: backend(`./gradlew test`), frontend(`npm ci && npm run lint && npm run typecheck && npm test`) 작업
- [ ] T008 [P] 기존 HTML 시안의 CSS를 `fe/styles/`로 옮기고 `fe/main.tsx`에서 불러오기 (시안 파일 위치는 수연님 확인 필요)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 시나리오가 기대는 설정·보안·공통 코드·기본 표

**⚠️ CRITICAL**: 이 단계를 끝내기 전에는 사용자 시나리오 작업을 시작하지 않는다

- [ ] T009 `backend/src/main/resources/application.yml`에 DB·Redis·S3·메일 연결(환경 변수)과 `myblog.*` 기본값 전체 작성: 원본 각 파일 '기본값' 표의 값(닉네임 2~10, 비밀번호 8~10, 인증번호 10분/1분/하루 5/5회/30분, 잠금 5회·10분, 세션 7일, 제목 100, 본문 10000, 블로그 이름 30·소개 200, 분류 20, 댓글 500·5초, 태그 5개·15자, 이미지 5MB·10장, 페이지 10, 미리보기 100·50, 검색어 2~50, 조회 중복 30분, 인기 검색어 10개·5초·가중치 3/1/5·1시간, default-topic=hobby) (NF-08)
- [ ] T010 `be/common/MyBlogProperties.java`: `@ConfigurationProperties("myblog")` record 묶음과 `GET /api/config` 컨트롤러 `be/common/ConfigController.java` (FR-039)
- [ ] T011 [P] `be/common/error/`: `ErrorCode` enum(contracts/api.md의 코드 전부), `ApiException`, `GlobalExceptionHandler`(Bean Validation 오류 → `VALIDATION_FAILED` + `fields`, 내부 오류는 문구만, NF-06)
- [ ] T012 [P] `be/common/Messages.java`: 원본 '안내 문구' 표 전체를 상수로 (FR-040)
- [ ] T013 [P] `be/common/PageResponse.java`: `{items,page,size,totalItems,totalPages}`와 범위를 넘으면 마지막 페이지로 맞추는 `PageRequests.clamp()` (CF-10-4)
- [ ] T014 [P] `be/common/KoreanClock.java`: Asia/Seoul 기준 오늘 날짜·자정까지 남은 시간 (BM-06-6)
- [ ] T015 `mig/V1__core_schema.sql`: `topic`(+시드 5개), `member`, `blog`, `category`(부분 고유 인덱스), `post`(인덱스·CHECK), `CREATE EXTENSION pg_trgm` — data-model.md 칸·제약 그대로
- [ ] T016 `mig/V2__spring_session.sql`: Spring Session JDBC PostgreSQL 스키마
- [ ] T017 `be/config/SecurityConfig.java`: 세션 기반 인증, `CookieCsrfTokenRepository.withHttpOnlyFalse()`, 세션 고정 방지(`changeSessionId`), 401 JSON 응답, 공개 GET 허용 목록, 나머지 인증 필요 (NF-10~12)
- [ ] T018 `be/config/SessionConfig.java`: `@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 7일)`, 쿠키 `MYBLOG_SESSION`(HttpOnly, SameSite=Lax, Secure는 프로필별, maxAge 7일) (CF-02-10)
- [ ] T019 [P] `be/config/RedisConfig.java`: `StringRedisTemplate`, Redis 장애 시 예외를 `SERVICE_UNAVAILABLE`로 바꾸는 헬퍼 `be/common/RedisGuard.java`
- [ ] T020 [P] `be/member/Member.java`(JPA 엔티티), `MemberRepository.java`(`findByEmail`, `existsByNicknameKey`)
- [ ] T021 [P] `be/blog/Topic.java`, `Blog.java`, `Category.java` 엔티티와 리포지토리
- [ ] T022 [P] `be/post/Post.java` 엔티티(visibility enum, `contentUpdatedAt`), `PostRepository.java`
- [ ] T023 `be/post/PostQueryService.java`: 공개 범위 조건을 한 곳에 둔다 — `publicOnly()`(visibility=PUBLIC)와 `visibleTo(viewerId)`(PUBLIC 또는 author=viewer). 다른 패키지는 글을 이 서비스로만 조회한다 (헌법 II, CF-13-4)
- [ ] T024 [P] `be/common/CurrentMember.java`: 세션에서 로그인 회원 id를 꺼내는 `@AuthenticationPrincipal` 래퍼
- [ ] T025 [P] `bt/support/IntegrationTest.java`: Testcontainers(PostgreSQL 16, Redis 7, MinIO) 공용 베이스, `MockMvc` + CSRF·세션 헬퍼, 테스트용 메일 캡처(`CapturingMailSender`)
- [ ] T026 [P] `fe/api/client.ts`: fetch 래퍼(쿠키 포함, `X-XSRF-TOKEN` 헤더, 401이면 로그인 창 이벤트, 오류 응답 타입), `fe/api/types.ts`(contracts/api.md 응답 타입)
- [ ] T027 [P] `fe/messages.ts`: 원본 '안내 문구' 표 (FR-040)
- [ ] T028 `fe/App.tsx`: React Router 주소 전체(contracts/screens.md), QueryClient, 앱 시작 시 `GET /api/csrf`·`/api/config`·`/api/auth/me`
- [ ] T029 `fe/components/Header.tsx`, `TopicNav.tsx`: 로고·로그인/회원가입 또는 사용자 메뉴 자리·검색창 자리·주제 메뉴, 현재 메뉴 강조 (FR-029, 요구사항.md 3.1)
- [ ] T030 [P] `fe/components/Pagination.tsx`(`‹ 1 2 ›`, 현재 강조, 처음·끝에서 이전/다음 비활성, CF-10-3), `fe/components/NotFound.tsx`("존재하지 않는 …입니다")

**Checkpoint**: 빈 서버·화면이 뜨고 CSRF·세션 쿠키가 오가며 `/api/config`가 기본값을 돌려준다

---

## Phase 3: User Story 1 - 이메일 인증으로 가입하고 로그인하기 (Priority: P1) 🎯 MVP

**Goal**: 실제 가입·인증·로그인·로그아웃·비밀번호 찾기 (FR-001~007)

**Independent Test**: spec US1의 Independent Test

### Tests for User Story 1

- [ ] T031 [P] [US1] `bt/member/PasswordPolicyTest.java`: CF-01-4 정규식 경계(7·8·10·11자, 영문/숫자/특수문자 누락, 공백, 허용 외 특수문자), CF-01-3 닉네임 규칙
- [ ] T032 [P] [US1] `bt/member/VerificationCodeGeneratorTest.java`: 6자리, 영문 대문자+숫자, O·0·I·1 없음 (CF-01-12)
- [ ] T033 [P] [US1] `bt/member/SignupFlowIT.java`: 인증 없이 가입 거절(CF-01-20), 이미 가입된 이메일에 메일 안 보냄(CF-14-1), 10분 만료·1회용·새 번호 시 이전 무효·1분/하루 5번·5회 오입력 폐기(CF-01-14~17), 인증 후 30분 초과 거절, 사이에 가입한 이메일 거절(CF-01-21), 가입 시 블로그·"미분류" 생성(CF-03-2, 3), 비밀번호가 해시로만 저장(CF-01-8)
- [ ] T034 [P] [US1] `bt/member/LoginFlowIT.java`: 없는 이메일과 틀린 비밀번호의 같은 문구(CF-02-3), 5회 실패 잠금·10분 후 해제·성공 시 카운트 0(CF-02-5~7), 로그인 전후 세션 ID 다름(NF-12), 로그아웃 후 이전 쿠키 무효(CF-02-11), 쿠키 속성(NF-10)
- [ ] T035 [P] [US1] `bt/member/PasswordResetIT.java`: 가입/미가입 이메일의 같은 응답과 같은 제한(CF-25-3), 변경 후 모든 세션 삭제(CF-25-8), 잠금 해제(CF-25-9), signup 인증으로 reset 불가(용도 구분)

### Implementation for User Story 1

- [ ] T036 [P] [US1] `be/member/PasswordPolicy.java`, `NicknamePolicy.java`, `EmailNormalizer.java` (CF-01-2~4)
- [ ] T037 [P] [US1] `be/member/VerificationCodeGenerator.java`: `SecureRandom` 6자리 (CF-01-12, NF-05)
- [ ] T038 [P] [US1] `be/mail/MailSender.java` 인터페이스, `LogMailSender.java`(로그 출력), `SmtpMailSender.java`(`@Async`, 연결 확인), `myblog.mail.mode`로 선택, `be/config/AsyncConfig.java` (research §4)
- [ ] T039 [P] [US1] `be/mail/MailTemplates.java`: 가입·비밀번호 찾기 인증번호 메일(서비스 이름, 번호, 유효 시간, "본인이 요청하지 않았다면…"), 비밀번호 변경 알림 메일 (CF-01-12, CF-25-4, CF-15-16)
- [ ] T040 [US1] `be/member/EmailVerificationService.java`: research §3의 Redis 키로 발송·확인·인증됨 표시·소비, 용도(`signup`/`reset`) 구분, reset의 계정 유무 숨기기 (CF-01-11~21, CF-25-2~5)
- [ ] T041 [US1] `be/member/SignupService.java`: 인증됨 표시 재확인, 이메일·닉네임 중복 재확인, BCrypt 저장, 블로그("{닉네임}의 블로그", 기본 주제)·"미분류" 생성을 한 트랜잭션으로, 인증됨 표시 삭제 (CF-01-9, 20, 21, CF-03-2, 3)
- [ ] T042 [US1] `be/member/LoginService.java`: `AuthenticationManager` 호출, 실패 카운트·잠금(CF-02-5~9), 성공 시 `SecurityContextRepository` 저장과 세션 principal 이름 = 회원 id
- [ ] T043 [US1] `be/member/MemberSessionService.java`: `FindByIndexNameSessionRepository`로 회원의 세션 전부/현재 제외 삭제 (CF-15-15, CF-25-8, CF-15-20에서 재사용)
- [ ] T044 [US1] `be/member/PasswordResetService.java`: 인증 확인 → 해시 교체·카운트/잠금 초기화(한 트랜잭션) → 모든 세션 삭제 → 인증됨 표시 삭제 → 알림 메일 (CF-25-6~10)
- [ ] T045 [US1] `be/member/AuthController.java`: `/api/auth/verifications`, `/confirm`, `/signup`, `/login`, `/logout`, `/me`, `/password-reset`, `GET /api/csrf` — contracts/api.md 그대로, 중복 제출 방지(요청 키 + Redis 5초, CF-01-10)
- [ ] T046 [P] [US1] `fe/components/AuthModal.tsx`: `로그인 | 회원가입` 탭, 바깥·`×`로 닫기 (요구사항.md 3.9)
- [ ] T047 [US1] `fe/components/SignupForm.tsx`: 닉네임·이메일 → `인증번호 받기`(형식이 맞을 때만) → 번호 입력·`확인` → 이메일 잠금·`이메일 변경`(인증 취소) → 비밀번호·확인, 입력 중 규칙 충족 표시, 칸별 오류·첫 오류 칸으로 커서·비밀번호 칸 비우기, 버튼 연타 방지 (CF-01-1~10, 13, 19)
- [ ] T048 [US1] `fe/components/LoginForm.tsx`: 로그인, 잠금 남은 시간 안내, "비밀번호를 잊으셨나요?" 링크, 성공 시 이전 화면으로 (CF-02-1~2, 6, CF-25-1)
- [ ] T049 [US1] `fe/components/UserMenu.tsx`: `내 블로그` / `글쓰기` / `마이페이지` / `블로그 관리` / `로그아웃`, 로그아웃 시 로그인 필요 화면이면 첫 화면으로 (CF-02-11)
- [ ] T050 [US1] `fe/api/useRequireLogin.ts`: 회원 전용 동작에서 로그인 창을 띄우고 로그인 후 하려던 동작으로 복귀 (CF-16-1)
- [ ] T051 [US1] `fe/pages/PasswordResetPage.tsx`: 이메일 → 인증 → 새 비밀번호 → 완료 안내 후 로그인 화면 (CF-25)
- [ ] T052 [US1] `e2e/us1-signup-login.spec.ts`: 서버 로그 대신 테스트용 엔드포인트(테스트 프로필 전용)로 인증번호를 읽어 가입→로그인→재방문 유지→로그아웃

**Checkpoint**: 실제 계정으로 가입·로그인·로그아웃·비밀번호 찾기가 동작한다 (데모 계정 제거)

---

## Phase 4: User Story 2 - 내 블로그에 글 쓰고 관리하기 (Priority: P1)

**Goal**: 글 작성·수정·삭제, 공개 범위, 분류 관리 (FR-011~015, FR-017)

**Independent Test**: spec US2의 Independent Test

### Tests for User Story 2

- [ ] T053 [P] [US2] `bt/post/PostCommandIT.java`: 제목·본문 경계(CF-05-3, 4), 남의 블로그에 쓰기·남의 글 수정/삭제 → 404(CF-05-1, 12, NF-02), 내용이 그대로면 수정 시각 유지(CF-05-13), 삭제 시 댓글·좋아요·태그 연결·이미지 함께 삭제(CF-05-15)
- [ ] T054 [P] [US2] `bt/post/PostVisibilityIT.java`: 비공개 글이 다른 회원·비로그인에게 상세 404, 목록·검색·이전/다음·카테고리 개수·태그 목록·홈·주제에 없음 (CF-13-4, CF-09-4, SC-004)
- [ ] T055 [P] [US2] `bt/blog/CategoryIT.java`: 블로그 안 이름 중복(대소문자 무시), 글 있는 분류 삭제 불가(DB RESTRICT 포함), "미분류" 삭제 불가·이름 변경 가능, 순서 변경, 남의 분류 수정 거절 (CF-08-1~8)

### Implementation for User Story 2

- [ ] T056 [US2] `be/blog/CategoryService.java`, `CategoryController.java`: 추가(맨 아래, 색 자동 배정)·이름 변경·위/아래 이동·삭제 규칙, 방문자/주인별 글 개수 (CF-08, BM-04-3, CF-08-9)
- [ ] T057 [US2] `be/blog/BlogService.java`, `BlogController.java`: `GET /api/blogs/{id}`, `/about`, `PATCH`(이름 1~30·소개 0~200·about·주제, 주인만) (CF-04, BM-07, 요구사항.md 3.6)
- [ ] T058 [US2] `be/post/PostCommandService.java`: 작성(자기 블로그만, 분류 소속 확인, 작성 시각 자동), 수정(작성자만, 실제 변경 시에만 `contentUpdatedAt`), 삭제(연관 데이터 한 트랜잭션, 이미지 파일은 커밋 후) (CF-05)
- [ ] T059 [US2] `be/post/PostController.java`: `POST /api/blogs/{id}/posts`, `PUT/DELETE /api/posts/{id}`, `GET /api/posts/{id}/edit`, `GET /api/me/last-category` (CF-05-5)
- [ ] T060 [P] [US2] `fe/pages/PostEditorPage.tsx`: 제목·본문(마크다운 입력 + 미리보기 탭)·분류 선택(기본값 마지막 분류)·공개 여부(기본 공개), 칸별 오류, 이탈 확인, 연타 방지, 저장 실패 시 입력 유지, 비공개→공개 확인 문구 (CF-05-2~11, CF-13-6)
- [ ] T061 [P] [US2] `fe/components/MarkdownView.tsx`: react-markdown + remark-gfm, `skipHtml`, 링크·이미지 주소 허용 목록 (research §6, NF-07)
- [ ] T062 [US2] `fe/pages/manage/CategoriesPage.tsx`: 분류 목록(이름·글 개수·색 점), 추가·이름 변경·위/아래·삭제, 삭제 불가 안내 문구 상시 표시 (BM-04)

**Checkpoint**: 회원이 분류를 만들고 공개/비공개 글을 쓰고 고치고 지울 수 있다

---

## Phase 5: User Story 3 - 블로그와 글 읽기, 검색하기 (Priority: P1)

**Goal**: 블로그 글 목록, 글 상세, 글 검색을 실제 데이터로 (FR-016, FR-018~020)

**Independent Test**: spec US3의 Independent Test

### Tests for User Story 3

- [ ] T063 [P] [US3] `bt/post/PostListIT.java`: 최신순·같은 시각이면 나중 글 위(CF-10-1), 10개씩·범위 초과 시 마지막 페이지(CF-10-2, 4), 미리보기 100자·줄바꿈→공백·마크다운 기호 제거(CF-10-5), 분류 필터, 주인은 비공개 포함(CF-10-6)
- [ ] T064 [P] [US3] `bt/post/PostDetailIT.java`: 이전/다음 글이 같은 블로그 공개 글 기준이고 없으면 null(CF-09-2), 같은 블로그 다른 글 3개, 수정 시각 노출(CF-09-1)
- [ ] T065 [P] [US3] `bt/search/PostSearchIT.java`: 2~50자, 여러 단어 AND, 대소문자 무시, `%`·`_`·`\` 이스케이프, 비공개 제외 (CF-11-1~9)

### Implementation for User Story 3

- [ ] T066 [US3] `be/post/PostQueryService.java`에 블로그 글 목록·상세·이전/다음·같은 블로그 다른 글 조회 추가 (T023의 조건만 사용)
- [ ] T067 [US3] `be/post/ExcerptMaker.java`: 마크다운 기호 제거, 줄바꿈→공백, 100자 + "…" (CF-10-5)
- [ ] T068 [US3] `be/post/PostReadController.java`: `GET /api/blogs/{id}/posts`, `GET /api/posts/{id}` (contracts/api.md 응답 모양)
- [ ] T069 [US3] `mig/V3__search_indexes.sql`: `post.title`, `post.body` trigram GIN 인덱스
- [ ] T070 [US3] `be/search/KeywordQuery.java`(정규화·단어 분리·LIKE 이스케이프)와 `be/search/PostSearchRepository.java`(단어별 `ILIKE … ESCAPE` AND, 공개 글만) (research §7)
- [ ] T071 [P] [US3] `fe/pages/BlogPage.tsx`: 블로그 이름·소개, 글 목록(분류·작성일·제목·미리보기, "N개의 글", 비공개 표시), 오른쪽 프로필·분류 목록(전체 + 개수), 빈 목록 문구(내 블로그면 "첫 글을 써 보세요" + 글쓰기), `?category&page` 유지 (요구사항.md 3.4, CF-10)
- [ ] T072 [P] [US3] `fe/pages/BlogAboutPage.tsx`: 주인 이름·한 줄 소개·소개 글 (3.6)
- [ ] T073 [P] [US3] `fe/pages/PostDetailPage.tsx`: 경로(홈›주제›블로그›분류), 분류·제목·블로그·작성/수정 시각, 대표 사진, 본문, 이전/다음 글, 블로그 소개 카드, 다른 글 3개, "글 목록으로"(분류 목록), 작성자에게만 수정·삭제 (3.5, CF-09)
- [ ] T074 [US3] `fe/pages/SearchPage.tsx`: 검색어 유지, "검색 결과 N건", 결과 없음·짧은 검색어 안내 (CF-11-6~8). (통합 검색은 US8에서 확장)
- [ ] T075 [US3] `e2e/us2-us3-write-read.spec.ts`: 글쓰기 → 비로그인으로 목록·상세·검색 확인, 비공개 글 차단, 360px 가로 스크롤 없음 (SC-004, SC-007)

**Checkpoint**: US1~US3만으로 블로그 서비스가 실제로 동작한다 (MVP)

---

## Phase 6: User Story 4 - 댓글·좋아요·태그·이미지·신고 (Priority: P2)

**Goal**: FR-021~025

**Independent Test**: spec US4의 Independent Test

### Tests for User Story 4

- [ ] T076 [P] [US4] `bt/comment/CommentIT.java`: 비회원 작성 거절, 1~500자·공백만 불가, 오래된 순, 5초 연속 등록 거절, 작성자·블로그 주인만 삭제, 탈퇴 작성자는 author null (CF-18)
- [ ] T077 [P] [US4] `bt/post/LikeTagReportIT.java`: 좋아요 토글·자기 글 불가(CF-19), 태그 5개·15자·`#` 제거·중복 불가·태그 목록은 공개 글만(CF-20), 신고 사유·중복 불가·자기 글 불가(CF-21)
- [ ] T078 [P] [US4] `bt/image/ImageUploadIT.java`: 형식은 확장자가 아니라 매직 넘버로 판별, 5MB 초과 거절, 글당 10장 초과 거절, 글 삭제 시 파일 삭제, 연결 안 된 이미지 24시간 정리 (CF-22)

### Implementation for User Story 4

- [ ] T079 [US4] `mig/V4__interactions.sql`: `comment`, `post_like`, `tag`, `post_tag`, `post_report`, `post_image` (data-model.md)
- [ ] T080 [P] [US4] `be/comment/CommentService.java`, `CommentController.java`: 작성(5초 쿨다운 Redis 키), 오래된 순 목록(`canDelete` 포함), 삭제 권한 (CF-18)
- [ ] T081 [P] [US4] `be/post/LikeService.java`, `ReportService.java`와 컨트롤러 (CF-19, CF-21)
- [ ] T082 [P] [US4] `be/post/TagService.java`: 이름 정규화, 글 저장 시 태그 연결, `GET /api/tags/{name}/posts` (CF-20)
- [ ] T083 [P] [US4] `be/image/ImageStorage.java`, `S3ImageStorage.java`(MinIO path-style), `DiskImageStorage.java`, `be/config/S3Config.java`, `myblog.image.storage`로 선택 (research §5)
- [ ] T084 [US4] `be/image/ImageService.java`, `ImageController.java`: 업로드 검증·저장·`GET /api/images/{key}`, 글 저장 시 연결·10장 검사, `@Scheduled` 미연결 이미지 정리
- [ ] T085 [US4] `PostCommandService`에 태그·이미지·대표 사진 연결 추가 (T058 확장)
- [ ] T086 [P] [US4] `fe/components/CommentSection.tsx`: 비회원 안내+로그인 버튼, 입력·등록, 목록(탈퇴한 사용자), 삭제 확인 (CF-18)
- [ ] T087 [P] [US4] `fe/components/LikeButton.tsx`, `ReportDialog.tsx`, `TagList.tsx` (CF-19~21)
- [ ] T088 [US4] `PostEditorPage.tsx`에 태그 입력(최대 5개 칩)과 이미지 업로드(본문에 `![](/api/images/...)` 삽입, 대표 사진 선택, 오류 문구) 추가 (CF-20, CF-22)
- [ ] T089 [P] [US4] `fe/pages/TagPostsPage.tsx`: 태그별 공개 글 목록

**Checkpoint**: 글에 댓글·좋아요·태그·이미지·신고가 실제로 동작한다

---

## Phase 7: User Story 5 - 마이페이지 (Priority: P2)

**Goal**: FR-008~010

**Independent Test**: spec US5의 Independent Test

### Tests for User Story 5

- [ ] T090 [P] [US5] `bt/member/MyPageIT.java`: 닉네임 변경(내 닉네임 그대로는 중복 아님), 이메일 변경 불가, 비밀번호 변경 시 현재 오입력 합산 잠금·같은 비밀번호 거절·다른 세션만 삭제(CF-15-10~15)
- [ ] T091 [P] [US5] `bt/member/WithdrawIT.java`: 비밀번호·확인 체크 필수, 블로그·글·분류·좋아요 삭제, 남의 글 댓글·커뮤니티 글은 author null, 모든 세션 삭제, 같은 이메일·닉네임 재가입 가능 (CF-15-17~21)

### Implementation for User Story 5

- [ ] T092 [US5] `be/member/MyPageService.java`: 내 정보 조회·수정, 비밀번호 변경(현재 확인, 카운트 합산, 다른 세션 삭제, 알림 메일) (CF-15-1~16)
- [ ] T093 [US5] `be/member/WithdrawService.java`: research §11 순서로 한 트랜잭션, 커밋 후 이미지 파일 삭제, 모든 세션 삭제
- [ ] T094 [US5] `be/member/MyPageController.java`: `GET/PATCH /api/me`, `PUT /api/me/password`, `DELETE /api/me`
- [ ] T095 [US5] `fe/pages/MyPage.tsx`: 내 정보(수정·저장 버튼 활성 조건·이탈 확인), 비밀번호 변경, 탈퇴(삭제·보존 안내, 체크, 최종 확인, 완료 후 첫 화면 문구), 내 블로그 바로가기 (CF-15)

**Checkpoint**: 회원이 계정을 스스로 관리할 수 있다

---

## Phase 8: User Story 6 - 블로그 관리와 통계 (Priority: P3)

**Goal**: FR-026~028

**Independent Test**: spec US6의 Independent Test

### Tests for User Story 6

- [ ] T096 [P] [US6] `bt/stats/ViewCountIT.java`: 30분 안 재조회 미집계, 하루 1번 방문자, 한국 자정 기준, 주인 본인·비공개 글 미집계, Redis 장애 시 글 읽기는 성공 (BM-06-3~6)
- [ ] T097 [P] [US6] `bt/manage/ManageAccessIT.java`: 다른 회원의 관리 API 거절(BM-01-2), 새 댓글 수에서 내 댓글 제외·댓글 관리 조회 후 0 (BM-05-4~6)

### Implementation for User Story 6

- [ ] T098 [US6] `mig/V5__stats.sql`: `daily_stat`(UNIQUE NULLS NOT DISTINCT), `blog.comments_seen_at`이 V1에 없으면 추가
- [ ] T099 [US6] `be/stats/VisitorIdFilter.java`: `MYBLOG_VID` 쿠키 발급(1년), 회원이면 `m:{id}` (research §9)
- [ ] T100 [US6] `be/stats/ViewRecorder.java`: Redis 중복 키 확인 후 `daily_stat` upsert와 `post.view_count` 증가, `GET /api/posts/{id}`에서 호출 (BM-06-3, 4, 7)
- [ ] T101 [US6] `be/stats/DashboardService.java`, `StatsService.java`: 오늘·어제·누적, 30일 일별, 최근 7일 인기 공개 글 5개, 최근 글 5개, 7/30일 일별 조회·방문·댓글 (BM-02, BM-06)
- [ ] T102 [US6] `be/manage/ManageController.java`: `/api/manage/dashboard`, `/posts`(공개 여부·분류 필터, 조회수·댓글 수), `/comments`(최신순, 50자, `isNew`, 조회 시 `comments_seen_at` 갱신), `/stats` (BM-02~06)
- [ ] T103 [US6] `/api/auth/me`에 `newCommentCount` 추가 (BM-05-5)
- [ ] T104 [P] [US6] `fe/pages/manage/ManageLayout.tsx`: 왼쪽 메뉴(대시보드·글·분류·댓글(숫자)·통계·설정), 블로그 이름·내 블로그 보기·글쓰기 (BM-01)
- [ ] T105 [P] [US6] `fe/pages/manage/DashboardPage.tsx`(숫자 카드, 30일 그래프, 인기 글, 최근 글, 새 댓글 강조), `PostsPage.tsx`(필터·보기/수정/삭제·빈 상태), `CommentsPage.tsx`(NEW, 글 제목→댓글 위치, 삭제), `StatsPage.tsx`(7/30일, 조회·방문 그래프 + 댓글 그래프, 툴팁), `SettingsPage.tsx`(이름·소개 n/200·about·주제) (BM-02~07)

**Checkpoint**: 블로그 주인이 관리 화면에서 운영과 통계를 볼 수 있다

---

## Phase 9: User Story 7 - 홈과 주제별 둘러보기 (Priority: P3)

**Goal**: FR-030~033

**Independent Test**: spec US7의 Independent Test

- [ ] T106 [P] [US7] `bt/home/HomeIT.java`: 대표 글 3개(모자라면 최근 7일 인기 공개 글로 채움), 최신 공개 글 6개, 블로그 카드의 공개 글 수·최근 글 날짜, 주제 화면 블로그·글 9개·커뮤니티 3개, 없는 주제 404 (요구사항.md 3.2, 3.3)
- [ ] T107 [US7] `be/home/HomeService.java`, `TopicService.java`, `HomeController.java`: `GET /api/home`, `/api/topics`, `/api/topics/{code}` (PostQueryService의 공개 조건 사용)
- [ ] T108 [P] [US7] `fe/pages/HomePage.tsx`: 오늘의 이슈(큰 카드 1 + 작은 카드 2, 사진 유무별 모양), 새로 올라온 글 6, 블로그 둘러보기, 커뮤니티 5 + 전체 보기, 인기 검색어 카드 자리 (3.2)
- [ ] T109 [P] [US7] `fe/pages/TopicPage.tsx`: 제목·설명·"블로그 N개 · 글 N개", 블로그·글 9개·커뮤니티 3개, 빈 상태 문구, 없는 주제 문구 (3.3)
- [ ] T110 [P] [US7] `fe/components/BlogCard.tsx`, `PostCard.tsx`, `FeaturedCard.tsx`

**Checkpoint**: 홈과 주제 화면이 샘플 데이터 대신 실제 데이터로 보인다

---

## Phase 10: User Story 8 - 커뮤니티와 실시간 인기 검색어 (Priority: P3)

**Goal**: FR-034~036

**Independent Test**: spec US8의 Independent Test

### Tests for User Story 8

- [ ] T111 [P] [US8] `bt/community/CommunityIT.java`: 주제 탭 필터·10개씩·댓글 수·조회 수(30분 중복 제외), 비회원 작성 거절, 작성자만 수정·삭제
- [ ] T112 [P] [US8] `bt/search/TrendingIT.java`: 점수 = 검색×3 + 조회×1 + 댓글×5(최근 1시간), 상위 10개, UP/DOWN/NEW/SAME와 칸 수, 가중치 설정 변경 반영 (요구사항.md 3.8)
- [ ] T113 [P] [US8] `bt/search/UnifiedSearchIT.java`: 블로그(이름·주인·소개·주제·분류), 글(제목·분류·본문·블로그 이름·주제), 커뮤니티(제목·주제·작성자·본문) 대상, 공개 글만, 검색 기록 저장

### Implementation for User Story 8

- [ ] T114 [US8] `mig/V6__community_search.sql`: `community_post`, `community_comment`, `search_log`, trigram 인덱스
- [ ] T115 [US8] `be/community/CommunityService.java`, `CommunityController.java`: 목록·상세(조회 기록)·작성·수정·삭제·댓글 (contracts/api.md)
- [ ] T116 [US8] `be/search/UnifiedSearchService.java`, `SearchController.java`: 블로그·글·커뮤니티 검색, `search_log` 기록 (3.8)
- [ ] T117 [US8] `be/search/TrendingScheduler.java`: `@Scheduled(fixedRateString = myblog.trending.interval)` 점수 계산, 직전 순위 비교, 메모리 보관, `GET /api/search/trending`; 30일 지난 `search_log` 정리 (research §8)
- [ ] T118 [P] [US8] `fe/pages/CommunityListPage.tsx`, `CommunityDetailPage.tsx`, `CommunityWritePage.tsx`: 탭, 표(주제·제목+댓글 수·작성자·날짜·조회), "첫 댓글을 남겨 보세요", 로그인 유도 (3.7)
- [ ] T119 [P] [US8] `fe/components/TrendingList.tsx`: 1~10위(1~3위 강조), ▲N/▼N/NEW/-, 순위 이동 애니메이션, 마지막 갱신 시각, 5초 폴링 — 홈 카드·검색창 상자·검색 결과 오른쪽 세 곳에서 같은 쿼리 공유 (3.8)
- [ ] T120 [US8] `Header.tsx` 검색창 포커스 시 `TrendingList` 상자, `SearchPage.tsx`를 블로그·글·커뮤니티 구역으로 확장 (3.8)

**Checkpoint**: 커뮤니티 쓰기와 인기 검색어가 데모가 아니라 실제로 동작한다

---

## Phase 11: Polish & Cross-Cutting Concerns

- [ ] T121 [P] `bt/security/XssAndCsrfIT.java`: CSRF 헤더 없는 쓰기 403, 본문·댓글의 `<script>`가 응답에 그대로(화면이 글자로 표시), 검색 특수문자 (NF-07, NF-11)
- [ ] T122 [P] `fe/` 전 화면 360px 점검과 Playwright 모바일 프로젝트 스냅샷 (NF-03, SC-007)
- [ ] T123 [P] 글 목록·상세 응답 시간 측정(공개 글 1,000개 시드) — 2초 이내 확인, 느리면 인덱스 보강 (NF-09)
- [ ] T124 [P] 루트 `README.md`: 프로젝트 소개, `docs/` 안내, quickstart 링크
- [ ] T125 `docs/요구사항.md` 3.8·3.9의 `[데모]` 표시와 `상세/` 각 파일의 상태 표시를 실제 구현 상태로 갱신 (헌법 I)
- [ ] T126 quickstart.md의 손으로 확인하는 시나리오 1~8과 보안 확인을 처음부터 끝까지 수행

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → 사용자 시나리오들 → **Polish (Phase 11)**

### User Story Dependencies

- **US1 (P1)**: Foundational 이후 바로. 다른 시나리오는 로그인 회원이 필요하므로 US1이 먼저다.
- **US2 (P1)**: US1 이후(가입 시 블로그·"미분류" 생성에 기댐).
- **US3 (P1)**: US2의 글 데이터가 있어야 의미 있다. 서버 조회 코드(T066~T070)는 US2와 동시에 해도 된다.
- **US4 (P2)**: US3의 글 상세 화면 위에 붙는다.
- **US5 (P2)**: US1만 있으면 된다. 탈퇴(T093)는 US4의 표가 있으면 함께 정리한다.
- **US6 (P3)**: US3(조회 기록 지점), US4(댓글) 이후.
- **US7 (P3)**: US3 이후. 커뮤니티 구역은 US8 전에는 빈 상태로 보인다.
- **US8 (P3)**: US3 이후. 인기 검색어의 조회·댓글 점수는 US4·US6의 데이터를 쓴다.

### Within Each User Story

- 테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다.
- 마이그레이션 → 엔티티 → 서비스 → 컨트롤러 → 화면 순서.

### Parallel Opportunities

- Phase 1의 T002·T004~T008, Phase 2의 [P] 작업은 동시에 할 수 있다.
- 각 시나리오 안에서 테스트 [P] 작업끼리, 서버 [P]와 화면 [P] 작업끼리 동시에 할 수 있다.

## Parallel Example: User Story 1

```bash
# 테스트 먼저 함께:
T031 PasswordPolicyTest, T032 VerificationCodeGeneratorTest, T033 SignupFlowIT, T034 LoginFlowIT, T035 PasswordResetIT
# 서로 다른 파일의 구현 함께:
T036 PasswordPolicy/NicknamePolicy, T037 VerificationCodeGenerator, T038 MailSender, T039 MailTemplates, T046 AuthModal
```

## Implementation Strategy

### MVP First (US1 → US2 → US3)

1. Phase 1~2를 끝낸다.
2. US1(실제 인증) → US2(글쓰기) → US3(읽기·검색)을 끝낸다.
3. **멈추고 확인**: quickstart 시나리오 1~3, 보안 확인. 이 상태가 원본 공통 필수 기능(CF-01~13)을 모두 채운 MVP다(SC-009).

### Incremental Delivery

4. US4 → US5 (공통 권장 기능)
5. US6 → US7 → US8 (블로그 관리와 MyBlog 개인 기능)
6. 각 단계마다 PR 하나, CI 통과 후 머지.

## Notes

- 원본 `docs/`와 다르게 구현해야 할 일이 생기면 원본을 먼저 고친다 (헌법 I).
- 기본값 숫자는 `application.yml`의 `myblog.*` 밖에 두지 않는다 (헌법 VI).
- `확인 필요` 항목(블로그 주제 기본값, 오늘의 이슈 선정, 커뮤니티 권한)은 spec Assumptions에서 바뀌면 관련 작업(T041, T107, T115)을 고친다.
