-- 댓글에 답글을 한 단계만 단다 (CF-18, 팀 리뷰 결과).
-- parent_id가 NULL이면 원 댓글, 값이 있으면 그 원 댓글의 답글이다. 답글에 다시 답글은 달 수 없다(서버에서 막는다).
-- 깊이가 한 단계라 재귀 쿼리 없이 한 번에 읽는다: ORDER BY COALESCE(parent_id, id), parent_id NULLS FIRST, created_at
-- 원 댓글을 지우면 답글도 함께 지운다.
ALTER TABLE comments ADD COLUMN parent_id bigint REFERENCES comments (id) ON DELETE CASCADE;
ALTER TABLE comments ADD CONSTRAINT ck_comments_parent_not_self CHECK (parent_id <> id);
CREATE INDEX idx_comments_parent_id ON comments (parent_id);
