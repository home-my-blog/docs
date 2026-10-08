-- 회원 탈퇴를 소프트 삭제로 바꾼다 (CF-15, 팀 리뷰 결과).
-- 탈퇴하면 deleted_at만 찍는다: 바로 로그인할 수 없고, 블로그와 글은 다른 사람에게 보이지 않는다.
-- 보관 기간(기본 30일) 안에 로그인하면 복구할 수 있다.
-- 보관 기간이 지나면 예약 작업이 블로그·글·좋아요를 지우고, 회원 줄은 개인정보만 지운 채 남긴다(익명화):
--   이메일 → del_랜덤값@deleted.invalid, 닉네임 → del랜덤값, 비밀번호 해시 → 쓸 수 없는 값, 소개 → 빈 값.
--   다른 사람 글에 단 댓글과 신고 기록은 회원 번호로 그대로 남고, 화면에는 '탈퇴한 사용자'로 보인다.
ALTER TABLE members ADD COLUMN deleted_at timestamptz;
ALTER TABLE members ADD COLUMN anonymized_at timestamptz;
ALTER TABLE members ADD CONSTRAINT ck_members_anonymized_after_deleted
    CHECK (anonymized_at IS NULL OR deleted_at IS NOT NULL);
-- 탈퇴 회원은 적으니 부분 인덱스로 "탈퇴 회원 목록"과 익명화 대상을 빨리 찾는다
CREATE INDEX idx_members_deleted_at ON members (deleted_at) WHERE deleted_at IS NOT NULL;
