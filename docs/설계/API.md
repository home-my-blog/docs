# API 목록

> 지금 코드의 컨트롤러에서 뽑은 API 81개예요. 화면(React)이 이 주소들을 불러요. 자세한 동작은 같은 번호의 [기능](../기능/00-목차.md)·[원리](../원리/00-목차.md) 문서에 있어요.

## 공통 규칙

- 모든 주소는 `/api`로 시작하고, 주고받는 몸통은 JSON이에요(사진 올리기만 `multipart/form-data`).
- **로그인**: 로그인하면 `MYBLOG_SESSION` 쿠키가 생기고, 그 쿠키로 회원을 알아봐요. 아래 표에서 "로그인" 칸이 "필요"면 쿠키 없이 401이에요.
- **CSRF**: GET이 아닌 요청은 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더에 담아 보내야 해요. 화면은 시작할 때 `GET /api/csrf`로 쿠키를 받아요.
- **권한**: 로그인만으로 끝나지 않고 "주인만", "쓴 사람만" 같은 확인은 서비스 코드가 해요. 남의 것이면 403이나 404예요.
- **오류 모양**: `{ "error": { "code": "VALIDATION_FAILED", "message": "…", "fields": { "칸": "이유" } } }`. `code` 목록은 `ErrorCode.java`에 있어요.
- 로그인 규칙은 `SecurityConfig`가 정해요: `/api/me/**`, `/api/manage/**`, 글 고치기 원문은 로그인 필요, 나머지 GET은 누구나, 가입·로그인 관련 POST(소셜 가입 포함)는 누구나, 그 밖의 쓰기는 로그인 필요.

## 01 회원과 로그인

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| POST | `/api/auth/verifications` |  | 인증번호 보내기(가입·비밀번호 찾기) |
| POST | `/api/auth/verifications/confirm` |  | 인증번호 확인 |
| POST | `/api/auth/signup` |  | 가입 끝내기(블로그와 기본 다이어리도 생김) |
| POST | `/api/auth/login` |  | 로그인 |
| POST | `/api/auth/restore` |  | 탈퇴 30일 안에 계정 되살리기 |
| POST | `/api/auth/logout` | 필요 | 로그아웃 |
| GET | `/api/auth/me` |  | 지금 로그인한 회원(아니면 204) |
| POST | `/api/auth/password-reset` |  | 인증번호로 비밀번호 새로 정하기 |
| GET | `/api/auth/social/pending` |  | 소셜 가입 대기 정보 `{provider, email, nickname}`. 세션에 없으면 404 `SOCIAL_SIGNUP_NOT_FOUND` |
| POST | `/api/auth/social/signup` |  | 소셜 가입 끝내기 `{nickname}`. 회원·블로그를 만들고 바로 로그인, 응답 201 `{id, member}` |
| GET | `/api/me` | 필요 | 내 정보 |
| PATCH | `/api/me` | 필요 | 닉네임·소개 바꾸기 |
| PUT | `/api/me/password` | 필요 | 비밀번호 바꾸기(다른 기기는 로그아웃) |
| DELETE | `/api/me` | 필요 | 탈퇴(30일 뒤 익명화) |

- `POST /api/auth/verifications`의 응답은 `{expiresAt}`이에요. 메일 모드가 `log`이고 `purpose`가 `signup`일 때만 `devCode`(인증번호)가 더 실려요. 비밀번호 찾기(`reset`) 번호는 어떤 모드에서도 응답에 싣지 않아요(v1.7.1).
- 소셜 로그인은 `/api`가 아니라 Spring Security가 받는 주소예요. 화면의 버튼이 `/oauth2/authorization/{kakao|google}`로 보내고, 제공자는 `SITE_URL/login/oauth2/code/{kakao|google}`로 돌려보내요. 끝나면 `/`(로그인됨), `/social/signup`(닉네임 정하기), `/?social=withdrawn|no-email|failed`(로그인 안 됨) 중 하나로 가요. 이 주소들은 키가 설정된 제공자만 동작하고, 위 81개에 세지 않았어요.

## 02 블로그와 다이어리

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/blogs/{blogId}` |  | 블로그 정보와 내가 주인인지, 구독했는지 |
| GET | `/api/blogs/{blogId}/about` |  | 블로그 소개 |
| PATCH | `/api/blogs/{blogId}` | 필요 | 블로그 설정 바꾸기(이름, 설명, 분야, 소개). 주인만 |
| GET | `/api/blogs/{blogId}/categories` |  | 블로그의 다이어리 목록(볼 수 있는 것만) |
| POST | `/api/blogs/{blogId}/categories` | 필요 | 다이어리 만들기. 주인만 |
| PATCH | `/api/categories/{id}` | 필요 | 다이어리 설정 (이름·소개·표지 색·공개 범위). 보낸 칸만 바꾼다. |
| POST | `/api/categories/{id}/move` | 필요 | 다이어리 순서 옮기기. 주인만 |
| DELETE | `/api/categories/{id}` | 필요 | 다이어리 지우기(기본 다이어리와 글이 있는 다이어리는 안 됨). 주인만 |

## 03 글쓰기와 사진

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| PUT | `/api/posts/{id}/pin` | 필요 | 대표글 고정·해제 (작성자만) |
| POST | `/api/blogs/{blogId}/posts/move` | 필요 | 다이어리 편집: 고른 글을 다른 다이어리로 옮긴다 |
| POST | `/api/blogs/{blogId}/posts/delete` | 필요 | 다이어리 편집: 고른 글을 한꺼번에 지운다 |
| GET | `/api/posts/{id}/edit` | 필요 | 글 고치기 화면용 원문. 쓴 사람만 |
| POST | `/api/blogs/{blogId}/posts` | 필요 | 글 올리기 |
| PUT | `/api/posts/{id}` | 필요 | 글 고치기 |
| DELETE | `/api/posts/{id}` | 필요 | 글 지우기 |
| GET | `/api/me/last-category` | 필요 | 마지막으로 글을 쓴 다이어리(글쓰기 기본값) |
| GET | `/api/me/drafts` | 필요 | 임시저장 목록 |
| GET | `/api/me/drafts/{id}` | 필요 | 임시저장 하나 불러오기 |
| POST | `/api/me/drafts` | 필요 | 임시저장 새로 만들기(20개까지) |
| PUT | `/api/me/drafts/{id}` | 필요 | 임시저장 덮어쓰기 |
| DELETE | `/api/me/drafts/{id}` | 필요 | 임시저장 지우기 |
| POST | `/api/images` | 필요 | 사진 올리기(한 장 5MB, jpg·png·gif·webp) |
| GET | `/api/images/**` |  | 저장소에서 읽어 내려 준다 (MinIO 포트를 밖에 열지 않는다). |

## 04 읽기와 소통

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/blogs/{blogId}/posts` |  | sort: latest(기본) · popular · oldest, tag: 이 태그가 붙은 글만 |
| GET | `/api/blogs/{blogId}/tags` |  | 블로그 왼쪽 태그 모음 |
| GET | `/api/posts/{id}` |  | 글 상세(조회수도 셈) |
| GET | `/api/blogs/{blogId}/pinned` |  | 블로그 첫 화면 위쪽 대표글 |
| PUT | `/api/posts/{id}/like` | 필요 | 좋아요 켜고 끄기(내 글은 안 됨) |
| POST | `/api/posts/{id}/reports` | 필요 | 신고하기(한 글에 한 번) |
| GET | `/api/posts/{postId}/comments` |  | 댓글과 답글 목록 |
| POST | `/api/posts/{postId}/comments` | 필요 | 댓글이나 답글 달기 |
| DELETE | `/api/comments/{id}` | 필요 | 댓글 지우기(쓴 사람이나 블로그 주인, 답글도 같이) |
| PUT | `/api/posts/{id}/bookmark` | 필요 | 꽂거나, 이미 꽂은 글이면 폴더만 옮긴다. folderId가 없으면 미분류. 응답은 꽂힌 폴더. |
| DELETE | `/api/posts/{id}/bookmark` | 필요 | 책갈피 빼기 |
| GET | `/api/me/bookmarks` | 필요 | 내 책갈피: 최근에 꽂은 것부터. 지금 볼 수 없는 글은 빠진다. folder: all(기본) · none(미분류) · 폴더 id |
| GET | `/api/me/bookmark-folders` | 필요 | 폴더 탭: { total, none, folders: [{ id, name, createdAt, count }] } |
| POST | `/api/me/bookmark-folders` | 필요 | 책갈피 폴더 만들기 |
| PATCH | `/api/me/bookmark-folders/{id}` | 필요 | 책갈피 폴더 이름 바꾸기 |
| DELETE | `/api/me/bookmark-folders/{id}` | 필요 | 폴더만 지운다. 안의 책갈피는 미분류로 옮겨진다. |
| PUT | `/api/blogs/{blogId}/subscribe` | 필요 | 구독: 누르면 구독, 다시 누르면 취소. 내 블로그는 안 된다. |
| GET | `/api/me/subscriptions` | 필요 | 내가 구독한 블로그: 최근에 구독한 것부터 |
| GET | `/api/me/subscriptions/diaries` | 필요 | 내가 구독한 다이어리: 최근에 구독한 것부터 |
| GET | `/api/me/subscriptions/posts` | 필요 | 구독 피드: 공개 글과 구독자만 글, 최신순. kind = all(둘 다, 겹치는 글은 한 번) · diary(구독한 다이어리) · blog(구독한 블로그) |
| PUT | `/api/categories/{id}/subscribe` | 필요 | 다이어리 구독: 누르면 구독, 다시 누르면 취소. 내 다이어리·비공개 다이어리는 안 되고, 구독자만 다이어리는 블로그 구독자만. |

## 05 홈과 찾기

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/topics` |  | 분야 목록 |
| GET | `/api/home` |  | 홈 화면 묶음(인기 글, 인기 다이어리 등) |
| GET | `/api/posts` |  | 메인 '분야별 글': topic(주제 코드)이 없으면 전체, sort=popular면 인기순. |
| GET | `/api/notices` |  | 공지사항: 공지 담당 회원(myblog.notice.owner-email) 블로그의 '공지' 다이어리 공개 글, 최신순. |
| GET | `/api/topics/{code}` |  | 분야 화면: 그 분야의 글과 블로그 |
| GET | `/api/search` |  | 통합 검색(블로그·글) |
| GET | `/api/search/trending` |  | 실시간 인기 검색어 |
| GET | `/api/tags/{name}/posts` |  | 태그가 붙은 공개 글 |

## 06 잉크와 스티커

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/me/ink` | 필요 | 내 잉크: 잔액 · 오늘 출석 · 연속 출석 · 오늘 적립 현황 · 최근 내역 |
| POST | `/api/shop/checkin` | 필요 | 하루 첫 출석 (로그인한 화면이 하루 한 번 부른다). 이미 했으면 earned = 0 |
| GET | `/api/shop/packs` |  | 팩 목록: 누구나 볼 수 있다 |
| POST | `/api/shop/packs/{code}/buy` | 필요 | 스티커 팩 사기(잉크 차감) |
| GET | `/api/me/stickers` | 필요 | 내가 가진 스티커 |
| GET | `/api/shop/challenges` |  | 도전과제: 로그인 안 했으면 진행도 0 |
| POST | `/api/shop/challenges/{code}/claim` | 필요 | 달성한 도전과제 보상 받기 |
| GET | `/api/diary-stickers` |  | 다이어리 표지 스티커 여러 개를 한 번에 (책장·인기 다이어리) |
| PUT | `/api/categories/{id}/stickers` | 필요 | 다이어리 표지 스티커 바꾸기 (주인만, 가진 스티커만) |

## 07 블로그 관리와 통계

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/manage/dashboard` | 필요 | 블로그 관리 대시보드 숫자 |
| GET | `/api/manage/posts` | 필요 | 블로그 관리 글 목록(비공개 포함) |
| GET | `/api/manage/comments` | 필요 | 내 글에 달린 댓글 목록 |
| GET | `/api/manage/stats` | 필요 | 날짜별 조회수·방문자·댓글 |

## 공통

| 메서드 | 주소 | 로그인 | 하는 일 |
|---|---|---|---|
| GET | `/api/config` |  | 화면이 쓰는 설정값(글자 수 한도, 자동 저장 간격, 인기 검색어 새로고침 간격 등). `socialLogin`: 켜진 소셜 로그인 제공자 목록(`["kakao", "google"]` 중 client id가 설정된 것, 없으면 `[]`) |
| GET | `/api/csrf` |  | 앱 시작 시 한 번 불러 XSRF-TOKEN 쿠키를 받는다. |
