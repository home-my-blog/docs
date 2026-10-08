# Quickstart: 로컬에서 띄우고 확인하기

구현이 끝난 뒤 기능이 끝까지 동작하는지 확인하는 순서다. 개발 환경 설명은
[03-로컬환경-도커컴포즈-사용법.md](../../docs/가이드/03-로컬환경-도커컴포즈-사용법.md)를 함께 본다.

## 준비물

- JDK 21, Node.js 22, Docker(OrbStack 또는 Docker Desktop)
- 저장소 루트에서 `cp .env.example .env` 후 `DB_PASSWORD`, `MINIO_PASSWORD`를 채운다.
  메일은 기본값 `MYBLOG_MAIL_MODE=log`(서버 로그에 인증번호 출력)로 둔다.

## 실행

```bash
docker compose up -d                      # PostgreSQL 16, Redis 7, MinIO
cd backend && ./gradlew bootRun           # http://localhost:8080 (Flyway가 표와 주제 시드를 만든다)
cd frontend && npm install && npm run dev # http://localhost:5173 (/api는 8080으로 프록시)
```

## 자동 검사

```bash
cd backend && ./gradlew test              # Testcontainers로 DB·Redis·MinIO를 띄워 통합 테스트
cd frontend && npm run lint && npm run typecheck && npm test
npx playwright test                       # 위 실행 상태에서 US1~US3 종단 시나리오
```

## 손으로 확인하는 시나리오

각 시나리오의 기대 결과는 [spec.md](./spec.md)의 Acceptance Scenarios와 원본 ID를 따른다.

1. **가입과 로그인 (US1)**
   - 회원가입 창에서 닉네임 `수연`, 새 이메일 입력 → `인증번호 받기` → 서버 로그의 6자리 번호 입력 → `확인`.
   - 비밀번호 `abc123!@` 로 가입 → "가입이 완료되었습니다. 로그인해 주세요".
   - 로그인 → 왼쪽 위가 사용자 메뉴로 바뀐다. 브라우저를 닫았다 열어도 유지된다.
   - 비밀번호를 5번 틀리면 잠금 문구와 남은 시간이 나온다.
2. **글쓰기와 공개 범위 (US2)**
   - `분류 관리`에서 "국내여행" 추가 → 글쓰기에서 공개 글 1개, 비공개 글 1개 저장.
   - 시크릿 창(비로그인)에서 내 블로그에 공개 글만 보이고, 비공개 글 주소는 "존재하지 않는 글입니다".
   - "국내여행" 삭제 시도 → "이 분류에 글이 2개 있어 삭제할 수 없습니다…".
3. **읽기와 검색 (US3)**
   - 공개 글을 12개 만든 뒤 블로그 화면 1·2페이지, `?page=99`가 마지막 페이지인지, 이전/다음 글 버튼.
   - "단풍 명소" 검색 → 두 단어가 모두 든 공개 글만 나온다. "단" 한 글자는 안내 문구.
4. **소통 (US4)**: 두 번째 계정으로 댓글·좋아요·신고, 첫 계정이 그 댓글 삭제. 6MB 이미지 업로드는 거절.
5. **마이페이지 (US5)**: 두 브라우저에서 같은 계정 로그인 → 한쪽에서 비밀번호 변경 → 다른 쪽 새로고침 시 로그아웃.
6. **블로그 관리 (US6)**: 시크릿 창으로 내 글 3개 열기 → 대시보드 오늘 조회수 3, 방문자 1. 다른 계정 댓글 2개 → 새 댓글 2 → 댓글 관리 열면 사라짐.
7. **홈·주제 (US7)**: `UPDATE post SET featured = true WHERE id IN (…)`로 대표 글 3개 지정 → 홈 오늘의 이슈. 블로그 설정에서 주제를 "여행"으로 바꾸고 `/topics/travel` 확인.
8. **커뮤니티·인기 검색어 (US8)**: 커뮤니티 글·댓글 등록 → 목록의 댓글 수. 여러 검색어로 검색 → 5초 안에 인기 검색어 순위와 ▲/▼/NEW 변화.

## 보안 확인 (헌법 II, V)

- 다른 계정의 세션으로 `PUT /api/posts/{남의 글}` → `404 POST_NOT_FOUND`.
- `X-XSRF-TOKEN` 없이 `POST` → `403`.
- 본문·댓글에 `<script>alert(1)</script>` → 글자로만 보인다.
- 로그인 응답의 `Set-Cookie`에 `HttpOnly`, `SameSite=Lax`가 있고, 로그인 전후 세션 ID가 다르다.
