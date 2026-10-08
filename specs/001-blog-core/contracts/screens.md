# Contract: 화면 주소

React Router 주소와 그 화면이 부르는 API. 화면 구조·문구·CSS는 기존 HTML 시안을 그대로 옮긴다.

| 주소 | 화면 | 주요 API | 원본 |
|------|------|----------|------|
| `/` | 홈 | `GET /api/home`, `/api/search/trending` | 요구사항.md 3.2 |
| `/topics/:code` | 주제별 화면 | `GET /api/topics/{code}` | 3.3 |
| `/blogs/:blogId?category=&page=` | 블로그(글 목록) | `GET /api/blogs/{id}`, `/categories`, `/posts` | 3.4, CF-10 |
| `/blogs/:blogId/about` | 블로그 소개 | `GET /api/blogs/{id}/about` | 3.6 |
| `/posts/:postId` | 글 상세 + 댓글·좋아요·태그·신고 | `GET /api/posts/{id}`, `/comments` | 3.5, CF-09, CF-18~21 |
| `/write` · `/posts/:postId/edit` | 글쓰기·수정 (마크다운 + 미리보기 + 이미지) | `POST/PUT /api/posts`, `POST /api/images` | CF-05, CF-22 |
| `/tags/:name` | 태그별 글 목록 | `GET /api/tags/{name}/posts` | CF-20-3 |
| `/search?q=&type=` | 검색 결과 | `GET /api/search` | 3.8, CF-11 |
| `/community?topic=&page=` | 커뮤니티 목록 | `GET /api/community/posts` | 3.7 |
| `/community/:id` · `/community/write` | 커뮤니티 상세·글쓰기 | `/api/community/...` | 3.7 |
| (창) 로그인·회원가입 | 탭 전환 모달 | `/api/auth/*` | 3.9, CF-01, CF-02 |
| `/password-reset` | 비밀번호 찾기 | `/api/auth/verifications`, `/password-reset` | CF-25 |
| `/me` | 마이페이지 (내 정보·비밀번호 변경·탈퇴·내 블로그) | `/api/me` | CF-15 |
| `/manage` | 블로그 관리 대시보드 | `GET /api/manage/dashboard` | BM-02 |
| `/manage/posts` · `/manage/categories` · `/manage/comments` · `/manage/stats` · `/manage/settings` | 관리 메뉴 | `/api/manage/*`, `/api/blogs/{id}`, `/categories` | BM-03~07 |

## 공통 규칙

- 맨 위 메뉴(로고·로그인/회원가입 또는 사용자 메뉴·검색창·주제 메뉴)는 모든 화면에 있다. 현재 메뉴를 진하게,
  블로그·글 화면은 그 블로그의 주제를 강조한다 (3.1).
- 사용자 메뉴: `내 블로그` / `글쓰기` / `마이페이지` / `블로그 관리`(새 댓글 숫자) / `로그아웃` (3.9, BM-01-3, BM-05-5).
- 검색창을 누르면 입력 전에도 인기 검색어 10개 상자가 펼쳐진다. 순위가 바뀐 항목은 위아래로 부드럽게 움직인다 (3.8).
- 회원 전용 동작을 비로그인으로 누르면 로그인 창을 띄우고, 로그인하면 하던 화면으로 돌아간다 (CF-16-1).
- 입력 화면(글쓰기, 마이페이지)은 저장하지 않은 내용이 있으면 이탈 확인을 묻는다 (CF-05-10, CF-15-7).
- 없는 블로그·글·주제는 각 화면 안에서 "존재하지 않는 …입니다"를 보여 준다(별도 오류 화면 아님).
- 모든 화면은 폭 360px에서 가로 스크롤이 없다 (NF-03).
