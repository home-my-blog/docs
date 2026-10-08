-- MyBlog 기본 표 (specs/001-blog-core/data-model.md, Crowfoot 문서 669와 같은 구조)
-- 세션 표와 커뮤니티 표는 이번 범위에서 뺐다.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE member (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email              varchar(254) NOT NULL,
    nickname           varchar(10)  NOT NULL,
    nickname_key       varchar(10)  NOT NULL,
    password_hash      varchar(72)  NOT NULL,
    bio                varchar(100) NOT NULL DEFAULT '',
    failed_login_count int          NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_nickname_key UNIQUE (nickname_key),
    CONSTRAINT ck_member_failed_login_count CHECK (failed_login_count >= 0)
);

CREATE TABLE topic (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        varchar(20)  NOT NULL,
    name        varchar(20)  NOT NULL,
    description varchar(100) NOT NULL,
    sort_order  int          NOT NULL,
    CONSTRAINT uk_topic_code UNIQUE (code)
);

INSERT INTO topic (code, name, description, sort_order) VALUES
    ('travel',   '여행', '가 본 곳, 가고 싶은 곳의 이야기', 1),
    ('food',     '음식', '맛집과 요리, 먹는 즐거움', 2),
    ('hobby',    '취미', '좋아하는 것을 꾸준히 하는 기록', 3),
    ('exercise', '운동', '몸을 움직이는 습관과 루틴', 4),
    ('dev',      '개발', '코드와 기술, 배운 것 정리', 5);

CREATE TABLE blog (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    owner_id         bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    topic_id         bigint       NOT NULL REFERENCES topic (id),
    name             varchar(30)  NOT NULL,
    description      varchar(200) NOT NULL DEFAULT '',
    about            text         NOT NULL DEFAULT '',
    comments_seen_at timestamptz  NOT NULL,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    CONSTRAINT uk_blog_owner_id UNIQUE (owner_id)
);
CREATE INDEX idx_blog_topic_id ON blog (topic_id);

CREATE TABLE category (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blog_id     bigint      NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    name        varchar(20) NOT NULL,
    name_key    varchar(20) NOT NULL,
    sort_order  int         NOT NULL,
    is_default  boolean     NOT NULL DEFAULT false,
    color_index int         NOT NULL,
    CONSTRAINT uk_category_blog_id_name_key UNIQUE (blog_id, name_key)
);
CREATE UNIQUE INDEX uk_category_blog_default ON category (blog_id) WHERE is_default;

CREATE TABLE post_image (
    id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id      bigint,
    uploader_id  bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    storage_key  varchar(200) NOT NULL,
    content_type varchar(20)  NOT NULL,
    size_bytes   int          NOT NULL,
    created_at   timestamptz  NOT NULL,
    CONSTRAINT uk_post_image_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_post_image_size CHECK (size_bytes BETWEEN 1 AND 5242880),
    CONSTRAINT ck_post_image_content_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/gif', 'image/webp'))
);

CREATE TABLE post (
    id                 bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blog_id            bigint       NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    author_id          bigint       NOT NULL REFERENCES member (id),
    category_id        bigint       NOT NULL REFERENCES category (id) ON DELETE RESTRICT,
    title              varchar(100) NOT NULL,
    body               text         NOT NULL,
    visibility         varchar(10)  NOT NULL DEFAULT 'PUBLIC',
    cover_image_id     bigint REFERENCES post_image (id) ON DELETE SET NULL,
    featured           boolean      NOT NULL DEFAULT false,
    view_count         bigint       NOT NULL DEFAULT 0,
    created_at         timestamptz  NOT NULL,
    content_updated_at timestamptz,
    CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);
CREATE INDEX idx_post_blog_created ON post (blog_id, created_at DESC, id DESC);
CREATE INDEX idx_post_visibility_created ON post (visibility, created_at DESC);
CREATE INDEX idx_post_category_id ON post (category_id);
CREATE INDEX idx_post_author_id ON post (author_id);
CREATE INDEX idx_post_title_trgm ON post USING gin (title gin_trgm_ops);
CREATE INDEX idx_post_body_trgm ON post USING gin (body gin_trgm_ops);

ALTER TABLE post_image
    ADD CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE;
CREATE INDEX idx_post_image_post_id ON post_image (post_id);
CREATE INDEX idx_post_image_uploader_id ON post_image (uploader_id);

CREATE TABLE tag (
    id       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name     varchar(15) NOT NULL,
    name_key varchar(15) NOT NULL,
    CONSTRAINT uk_tag_name_key UNIQUE (name_key)
);

CREATE TABLE post_tag (
    post_id bigint NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    tag_id  bigint NOT NULL REFERENCES tag (id),
    PRIMARY KEY (post_id, tag_id)
);
CREATE INDEX idx_post_tag_tag_id ON post_tag (tag_id);

CREATE TABLE post_like (
    post_id    bigint      NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    member_id  bigint      NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (post_id, member_id)
);
CREATE INDEX idx_post_like_member_id ON post_like (member_id);

CREATE TABLE post_report (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id     bigint       NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    reporter_id bigint       NOT NULL REFERENCES member (id) ON DELETE CASCADE,
    reason      varchar(20)  NOT NULL,
    detail      varchar(200) NOT NULL DEFAULT '',
    created_at  timestamptz  NOT NULL,
    CONSTRAINT uk_post_report_post_id_reporter_id UNIQUE (post_id, reporter_id),
    CONSTRAINT ck_post_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'ADULT', 'OTHER'))
);
CREATE INDEX idx_post_report_reporter_id ON post_report (reporter_id);

CREATE TABLE comment (
    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    post_id    bigint       NOT NULL REFERENCES post (id) ON DELETE CASCADE,
    author_id  bigint REFERENCES member (id) ON DELETE SET NULL,
    body       varchar(500) NOT NULL,
    created_at timestamptz  NOT NULL
);
CREATE INDEX idx_comment_post_created ON comment (post_id, created_at);
CREATE INDEX idx_comment_created ON comment (created_at);
CREATE INDEX idx_comment_author_id ON comment (author_id);

CREATE TABLE daily_stat (
    id        bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    blog_id   bigint NOT NULL REFERENCES blog (id) ON DELETE CASCADE,
    post_id   bigint REFERENCES post (id) ON DELETE CASCADE,
    stat_date date   NOT NULL,
    views     int    NOT NULL DEFAULT 0,
    visitors  int    NOT NULL DEFAULT 0
);
-- post_id가 NULL인 행(블로그 전체)도 날짜마다 하나만 두려고 NULLS NOT DISTINCT를 쓴다 (PostgreSQL 15+)
CREATE UNIQUE INDEX uk_daily_stat_blog_post_date ON daily_stat (blog_id, post_id, stat_date) NULLS NOT DISTINCT;
CREATE INDEX idx_daily_stat_post_id ON daily_stat (post_id);

CREATE TABLE search_log (
    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    keyword     varchar(50) NOT NULL,
    searched_at timestamptz NOT NULL
);
CREATE INDEX idx_search_log_searched_at ON search_log (searched_at);
