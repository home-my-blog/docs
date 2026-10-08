# MyBlog

여러 회원이 블로그를 하나씩 운영하는 티스토리형 서비스입니다.

## 폴더

| 폴더 | 내용 |
|---|---|
| [`app/`](app/) | 소스코드. 화면 `app/frontend/`(React + Vite + TypeScript), 서버 `app/backend/`(Spring Boot 3.5, Java 21, PostgreSQL 16, Redis 7), 개발용 `docker-compose.yml`, `.env.example` |
| [`docs/`](docs/) | 문서. 요구사항 원본, 상세, 가이드 |
| [`specs/`](specs/001-blog-core/) | 스펙 문서(Spec Kit). 데이터 모델, 화면 흐름, 계획 |

## 로컬에서 실행

```bash
cd app
cp .env.example .env              # DB_PASSWORD 등을 채운다
docker compose up -d db redis     # MinIO까지 쓰려면: docker compose --profile minio up -d
(cd backend && ./gradlew bootRun) # http://localhost:8080, Flyway가 표와 주제를 만든다
(cd frontend && npm install && npm run dev) # http://localhost:5173 (/api는 8080으로 넘긴다)
```

메일은 기본값(`MYBLOG_MAIL_MODE=log`)이면 실제로 보내지 않고, 인증번호를 서버 로그에 찍습니다.
이미지는 기본으로 `app/backend/data/images`에 저장합니다(`MYBLOG_IMAGE_STORAGE=s3`이면 MinIO).

## 테스트

```bash
# 서버: PostgreSQL의 myblog_test DB와 Redis가 떠 있어야 한다
createdb -h localhost -U myblog myblog_test
cd app/backend && DB_USERNAME=myblog DB_PASSWORD=... ./gradlew test

# 화면
cd app/frontend && npm run lint && npm run typecheck && npm test
```

## 이번 범위에서 뺀 것

- 커뮤니티 게시판, 세션 표(`spring_session`). 로그인 세션은 서버 메모리에 있어 서버를 다시 켜면 다시 로그인합니다.
