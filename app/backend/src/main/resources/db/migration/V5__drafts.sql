-- 임시저장 글 (CF-05). 글쓰기 중간에 저장해 두고 나중에 이어 쓴다. 회원별 최대 개수는 설정(myblog.draft.max-per-member).
-- 아직 올린 글이 아니라 규칙이 느슨하다: 제목이나 본문 중 하나만 있어도 되고, 분류는 비어 있어도 된다.
-- 태그와 사진은 올릴 때 정식 표(tags, post_images)에 연결하므로 여기서는 배열로만 들고 있는다.
-- 글을 올리면 그 임시저장 글은 지운다. 회원 줄이 지워지면 함께 지운다.
CREATE TABLE drafts (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id      bigint       NOT NULL REFERENCES members (id) ON DELETE CASCADE,
    category_id    bigint REFERENCES categories (id) ON DELETE SET NULL,
    title          varchar(100) NOT NULL DEFAULT '',
    body           text         NOT NULL DEFAULT '',
    visibility     varchar(10)  NOT NULL DEFAULT 'PUBLIC',
    tags           varchar(15)[] NOT NULL DEFAULT '{}',
    image_ids      bigint[]     NOT NULL DEFAULT '{}',
    cover_image_id bigint,
    created_at     timestamptz  NOT NULL,
    updated_at     timestamptz  NOT NULL,
    CONSTRAINT ck_drafts_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'))
);
CREATE INDEX idx_drafts_member_updated ON drafts (member_id, updated_at DESC);
