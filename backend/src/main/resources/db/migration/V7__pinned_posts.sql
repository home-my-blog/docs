-- 대표글 고정 (데모 개인 기능): 블로그 주인이 내 글을 최대 3개(myblog.post.pin-limit)까지 블로그 첫 화면 위쪽에 고정한다.
-- 고정한 시각을 남겨 고정한 순서대로 보여 준다. NULL이면 고정하지 않은 글.
ALTER TABLE posts ADD COLUMN pinned_at timestamptz;
CREATE INDEX idx_posts_blog_pinned ON posts (blog_id, pinned_at) WHERE pinned_at IS NOT NULL;
