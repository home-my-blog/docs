# Implementation Plan: MyBlog 1차 개발 범위

**Branch**: `001-blog-core` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-blog-core/spec.md`

## Summary

여러 회원이 블로그를 하나씩 운영하는 티스토리형 서비스를 만든다. 기술은 수연님의
[기술스택-아키텍처.md](../../docs/기술스택-아키텍처.md) 가안을 그대로 따른다: React(Vite) 화면이 REST
API(JSON)로 **단일 Spring Boot 서버**를 부르고, 서버는 PostgreSQL(데이터 + 세션), Redis(인증번호 등
짧은 값), 이미지 저장소(MinIO, `ImageStorage`로 교체 가능), SMTP를 쓴다. 로그인은 서버 세션 쿠키
(Spring Session JDBC), 비밀번호는 BCrypt, DB 변경은 Flyway, 개발 인프라는 Docker Compose다.
지금 있는 HTML 시안의 화면 구조·문구·CSS는 React 컴포넌트로 옮긴다.

## Technical Context

**Language/Version**: Java 21 (서버), TypeScript 5.x + Node.js 22 LTS (화면)

**Primary Dependencies**:
- 서버: Spring Boot 3.5, Spring Web, Spring Security, Spring Session JDBC, Spring Data JPA, Bean
  Validation, Spring Data Redis, Spring Mail, Flyway, AWS SDK for Java v2 (S3), Gradle
- 화면: React 19, Vite, React Router, TanStack Query, react-markdown(+remark-gfm, 원시 HTML 비활성),
  Recharts(통계 그래프)

**Storage**: PostgreSQL 16(모든 영구 데이터 + `spring_session` 표, pg_trgm 확장), Redis 7(인증번호·
재발송 제한·인증됨 표시·조회수/방문자 중복 방지), MinIO(S3 방식, 개발용) 또는 서버 디스크

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers(PostgreSQL, Redis, MinIO), MockMvc;
화면은 Vitest + Testing Library; 종단은 Playwright

**Target Platform**: 서버는 Linux/macOS의 JVM 21, 화면은 최신 데스크톱·모바일 브라우저.
개발은 수연님 Mac(OrbStack)에서 Docker Compose

**Project Type**: 웹 애플리케이션(화면 `frontend/` + 서버 `backend/`)

**Performance Goals**: 글 목록·상세 2초 이내(NF-09, SC-003), 인기 검색어 5초 갱신(SC-008)

**Constraints**: 360px에서 가로 스크롤 없음(NF-03), 비공개 글 노출 0건(SC-004), 이미지 5MB·글당 10장
(CF-22), 로그인 7일 유지(CF-02-10), Redis가 꺼져도 로그인은 동작

**Scale/Scope**: 회원 수천 명, 동시 접속 수십~수백 명, 화면 약 20개, 2주 개인 구현 일정(비교표 6장)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 확인 내용 | 결과 |
|------|-----------|------|
| I. 요구사항 문서가 기준 | spec FR마다 원본 ID를 적었고, tasks와 테스트 이름에도 ID를 단다. 기본값과 문구는 원본 표에서 가져온다 | 통과 |
| II. 권한과 공개 범위는 서버가 | 모든 쓰기 API는 서비스 계층에서 소유자 확인. 방문자용 글 조회는 `PostQueryService.visiblePosts(viewer)` 한 곳의 조건(`visibility = PUBLIC or author = viewer`)만 쓴다. 남의 비공개 글·남의 자원은 404 `POST_NOT_FOUND` | 통과 |
| III. 단순하게 | 단일 서버(기능별 패키지). Redis·MinIO·SMTP는 기술스택 문서에서 이미 근거와 함께 정함. MSA·JWT·MQ·ES 없음. 인기 검색어는 `@Scheduled` + 메모리 | 통과 |
| IV. 핵심 흐름 테스트 | 인증·글·권한·비공개 차단은 Testcontainers 통합 테스트, 입력 규칙은 단위 테스트, P1은 Playwright | 통과 |
| V. 안전한 기본값 | BCrypt, Spring Security 세션 고정 방지, CSRF(쿠키 토큰 → 헤더), HttpOnly·Secure·SameSite=Lax, react-markdown 원시 HTML 비활성, JPA 파라미터 바인딩, 비밀 값은 `.env` | 통과 |
| VI. 기본값은 한곳 | `myblog.*` 설정 묶음(`MyBlogProperties`)에 모으고 `GET /api/config`로 화면에 내려 준다 | 통과 |

설계 후 재확인(Phase 1 이후): data-model과 contracts에 위반 없음. 통과.

## Project Structure

### Documentation (this feature)

```text
specs/001-blog-core/
├── plan.md              # 이 파일
├── research.md          # Phase 0: 미정 항목의 결정과 이유
├── data-model.md        # Phase 1: 표·칸·제약 (ERD 초안)
├── quickstart.md        # Phase 1: 로컬 실행과 검증 시나리오
├── contracts/
│   ├── api.md           # REST API 계약 (요청·응답·오류 코드)
│   └── screens.md       # 화면 주소와 화면별 API
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
docker-compose.yml               # PostgreSQL 16, Redis 7, MinIO (개발용)
.env.example                     # DB_PASSWORD, MINIO_PASSWORD, SMTP_* (실제 .env는 커밋하지 않음)

backend/
├── build.gradle.kts
└── src/
    ├── main/java/com/myblog/
    │   ├── MyBlogApplication.java
    │   ├── common/              # MyBlogProperties, 오류 응답, 페이지 응답, 시간(Asia/Seoul)
    │   ├── config/              # SecurityConfig, SessionConfig, RedisConfig, S3Config, AsyncConfig
    │   ├── member/              # 가입, 이메일 인증, 로그인 잠금, 비밀번호 찾기, 마이페이지, 탈퇴
    │   ├── mail/                # MailSender(@Async), 개발용 로그 출력 구현
    │   ├── blog/                # 블로그, 분류, 주제
    │   ├── post/                # 글, 공개 범위, 이전/다음, 태그, 좋아요, 신고
    │   ├── image/               # ImageStorage(S3/디스크), 업로드 검증
    │   ├── comment/             # 블로그 글 댓글, 새 댓글 계산
    │   ├── community/           # 커뮤니티 글·댓글
    │   ├── search/              # 통합 검색, 검색 기록, 인기 검색어 스케줄러
    │   ├── stats/               # 조회수·방문자 집계, 대시보드, 통계
    │   └── home/                # 홈, 주제별 화면 조합
    ├── main/resources/
    │   ├── application.yml      # myblog.* 기본값 (원본 각 파일의 '기본값' 표)
    │   └── db/migration/        # Flyway V1__..., V2__...
    └── test/java/com/myblog/    # 기능별 통합·단위 테스트

frontend/
├── package.json
├── vite.config.ts               # /api → localhost:8080 프록시 (쿠키·CSRF를 같은 주소로)
└── src/
    ├── api/                     # fetch 래퍼(CSRF 헤더), API별 훅
    ├── components/              # Header, TopicNav, LoginModal, Pagination, PostCard, BlogCard …
    ├── pages/                   # Home, Topic, Blog, BlogAbout, PostDetail, PostEditor, Search,
    │                            # Community*, MyPage, PasswordReset, Manage/*
    ├── styles/                  # 기존 시안 CSS
    └── messages.ts              # 원본 '안내 문구' 표
e2e/                             # Playwright 시나리오 (US1~US3)

docs/                            # 수연님의 요구사항·기술 문서 (원본)
```

**Structure Decision**: 기술스택 문서의 "화면과 서버를 나눈 풀스택 + 단일 서버" 결정을 따라
`frontend/`와 `backend/` 두 폴더로 둔다. 서버 안은 기술스택 문서 3장 A의 기능별 폴더(user→`member`,
blog, post, comment, search, stats)에 image·community·home·mail을 더했다. 패키지끼리는 서비스
인터페이스로만 부른다.

## Complexity Tracking

위반 사항 없음. (Redis, MinIO, SMTP는 원본 기술 문서에서 이미 이유와 함께 정한 구성이라 헌법 III의
"근거"를 그 문서로 갈음한다.)
