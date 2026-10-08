-- 표 이름을 복수형으로 바꾼다 (팀 리뷰 결과).
-- post_report는 post_flags로 바꾼다: "report"는 '보고서'로 읽혀서, 신고는 "flag"로 부르기로 했다.
-- V1은 이미 적용된 DB가 있어 고치지 않고(Flyway 체크섬 유지), 여기서 이름만 바꾼다.
-- 표·제약·인덱스·식별 시퀀스의 이름만 바꾸므로 데이터는 그대로 남는다. 컬럼 이름은 바꾸지 않는다.

-- 표
ALTER TABLE member RENAME TO members;
ALTER TABLE topic RENAME TO topics;
ALTER TABLE blog RENAME TO blogs;
ALTER TABLE category RENAME TO categories;
ALTER TABLE post_image RENAME TO post_images;
ALTER TABLE post RENAME TO posts;
ALTER TABLE tag RENAME TO tags;
ALTER TABLE post_tag RENAME TO post_tags;
ALTER TABLE post_like RENAME TO post_likes;
ALTER TABLE post_report RENAME TO post_flags;
ALTER TABLE comment RENAME TO comments;
ALTER TABLE daily_stat RENAME TO daily_stats;
ALTER TABLE search_log RENAME TO search_logs;

-- 제약 (기본 키·유니크 제약은 딸린 인덱스 이름도 함께 바뀐다)
ALTER TABLE blogs RENAME CONSTRAINT uk_blog_owner_id TO uk_blogs_owner_id;
ALTER TABLE blogs RENAME CONSTRAINT blog_pkey TO blogs_pkey;
ALTER TABLE blogs RENAME CONSTRAINT blog_owner_id_fkey TO blogs_owner_id_fkey;
ALTER TABLE blogs RENAME CONSTRAINT blog_topic_id_fkey TO blogs_topic_id_fkey;
ALTER TABLE categories RENAME CONSTRAINT uk_category_blog_id_name_key TO uk_categories_blog_id_name_key;
ALTER TABLE categories RENAME CONSTRAINT category_pkey TO categories_pkey;
ALTER TABLE categories RENAME CONSTRAINT category_blog_id_fkey TO categories_blog_id_fkey;
ALTER TABLE comments RENAME CONSTRAINT comment_pkey TO comments_pkey;
ALTER TABLE comments RENAME CONSTRAINT comment_author_id_fkey TO comments_author_id_fkey;
ALTER TABLE comments RENAME CONSTRAINT comment_post_id_fkey TO comments_post_id_fkey;
ALTER TABLE daily_stats RENAME CONSTRAINT daily_stat_pkey TO daily_stats_pkey;
ALTER TABLE daily_stats RENAME CONSTRAINT daily_stat_blog_id_fkey TO daily_stats_blog_id_fkey;
ALTER TABLE daily_stats RENAME CONSTRAINT daily_stat_post_id_fkey TO daily_stats_post_id_fkey;
ALTER TABLE members RENAME CONSTRAINT uk_member_email TO uk_members_email;
ALTER TABLE members RENAME CONSTRAINT uk_member_nickname_key TO uk_members_nickname_key;
ALTER TABLE members RENAME CONSTRAINT member_pkey TO members_pkey;
ALTER TABLE members RENAME CONSTRAINT ck_member_failed_login_count TO ck_members_failed_login_count;
ALTER TABLE posts RENAME CONSTRAINT post_pkey TO posts_pkey;
ALTER TABLE posts RENAME CONSTRAINT post_author_id_fkey TO posts_author_id_fkey;
ALTER TABLE posts RENAME CONSTRAINT post_blog_id_fkey TO posts_blog_id_fkey;
ALTER TABLE posts RENAME CONSTRAINT post_category_id_fkey TO posts_category_id_fkey;
ALTER TABLE posts RENAME CONSTRAINT post_cover_image_id_fkey TO posts_cover_image_id_fkey;
ALTER TABLE posts RENAME CONSTRAINT ck_post_visibility TO ck_posts_visibility;
ALTER TABLE post_images RENAME CONSTRAINT uk_post_image_storage_key TO uk_post_images_storage_key;
ALTER TABLE post_images RENAME CONSTRAINT post_image_pkey TO post_images_pkey;
ALTER TABLE post_images RENAME CONSTRAINT fk_post_image_post TO fk_post_images_post;
ALTER TABLE post_images RENAME CONSTRAINT post_image_uploader_id_fkey TO post_images_uploader_id_fkey;
ALTER TABLE post_images RENAME CONSTRAINT ck_post_image_content_type TO ck_post_images_content_type;
ALTER TABLE post_images RENAME CONSTRAINT ck_post_image_size TO ck_post_images_size;
ALTER TABLE post_likes RENAME CONSTRAINT post_like_pkey TO post_likes_pkey;
ALTER TABLE post_likes RENAME CONSTRAINT post_like_member_id_fkey TO post_likes_member_id_fkey;
ALTER TABLE post_likes RENAME CONSTRAINT post_like_post_id_fkey TO post_likes_post_id_fkey;
ALTER TABLE post_flags RENAME CONSTRAINT uk_post_report_post_id_reporter_id TO uk_post_flags_post_id_reporter_id;
ALTER TABLE post_flags RENAME CONSTRAINT post_report_pkey TO post_flags_pkey;
ALTER TABLE post_flags RENAME CONSTRAINT post_report_post_id_fkey TO post_flags_post_id_fkey;
ALTER TABLE post_flags RENAME CONSTRAINT post_report_reporter_id_fkey TO post_flags_reporter_id_fkey;
ALTER TABLE post_flags RENAME CONSTRAINT ck_post_report_reason TO ck_post_flags_reason;
ALTER TABLE post_tags RENAME CONSTRAINT post_tag_pkey TO post_tags_pkey;
ALTER TABLE post_tags RENAME CONSTRAINT post_tag_post_id_fkey TO post_tags_post_id_fkey;
ALTER TABLE post_tags RENAME CONSTRAINT post_tag_tag_id_fkey TO post_tags_tag_id_fkey;
ALTER TABLE search_logs RENAME CONSTRAINT search_log_pkey TO search_logs_pkey;
ALTER TABLE tags RENAME CONSTRAINT uk_tag_name_key TO uk_tags_name_key;
ALTER TABLE tags RENAME CONSTRAINT tag_pkey TO tags_pkey;
ALTER TABLE topics RENAME CONSTRAINT uk_topic_code TO uk_topics_code;
ALTER TABLE topics RENAME CONSTRAINT topic_pkey TO topics_pkey;

-- 제약에 딸리지 않은 인덱스
ALTER INDEX idx_blog_topic_id RENAME TO idx_blogs_topic_id;
ALTER INDEX uk_category_blog_default RENAME TO uk_categories_blog_default;
ALTER INDEX idx_comment_author_id RENAME TO idx_comments_author_id;
ALTER INDEX idx_comment_created RENAME TO idx_comments_created;
ALTER INDEX idx_comment_post_created RENAME TO idx_comments_post_created;
ALTER INDEX idx_daily_stat_post_id RENAME TO idx_daily_stats_post_id;
ALTER INDEX uk_daily_stat_blog_post_date RENAME TO uk_daily_stats_blog_post_date;
ALTER INDEX idx_post_author_id RENAME TO idx_posts_author_id;
ALTER INDEX idx_post_blog_created RENAME TO idx_posts_blog_created;
ALTER INDEX idx_post_body_trgm RENAME TO idx_posts_body_trgm;
ALTER INDEX idx_post_category_id RENAME TO idx_posts_category_id;
ALTER INDEX idx_post_title_trgm RENAME TO idx_posts_title_trgm;
ALTER INDEX idx_post_visibility_created RENAME TO idx_posts_visibility_created;
ALTER INDEX idx_post_image_post_id RENAME TO idx_post_images_post_id;
ALTER INDEX idx_post_image_uploader_id RENAME TO idx_post_images_uploader_id;
ALTER INDEX idx_post_like_member_id RENAME TO idx_post_likes_member_id;
ALTER INDEX idx_post_report_reporter_id RENAME TO idx_post_flags_reporter_id;
ALTER INDEX idx_post_tag_tag_id RENAME TO idx_post_tags_tag_id;
ALTER INDEX idx_search_log_searched_at RENAME TO idx_search_logs_searched_at;

-- 식별 컬럼 시퀀스
ALTER SEQUENCE blog_id_seq RENAME TO blogs_id_seq;
ALTER SEQUENCE category_id_seq RENAME TO categories_id_seq;
ALTER SEQUENCE comment_id_seq RENAME TO comments_id_seq;
ALTER SEQUENCE daily_stat_id_seq RENAME TO daily_stats_id_seq;
ALTER SEQUENCE member_id_seq RENAME TO members_id_seq;
ALTER SEQUENCE post_id_seq RENAME TO posts_id_seq;
ALTER SEQUENCE post_image_id_seq RENAME TO post_images_id_seq;
ALTER SEQUENCE post_report_id_seq RENAME TO post_flags_id_seq;
ALTER SEQUENCE search_log_id_seq RENAME TO search_logs_id_seq;
ALTER SEQUENCE tag_id_seq RENAME TO tags_id_seq;
ALTER SEQUENCE topic_id_seq RENAME TO topics_id_seq;
