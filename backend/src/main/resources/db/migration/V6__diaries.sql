-- 분류(categories)를 화면에서 "다이어리"로 부른다 (데모 common-demo의 다이어리 컨셉). 표와 칸 이름은 그대로 둔다.
-- 다이어리마다 소개와 공개 범위를 둔다. 표지 색은 이미 있는 color_index를 쓴다.
-- 비공개 다이어리의 글은 글 자체가 공개여도 주인만 본다.
ALTER TABLE categories ADD COLUMN description varchar(100) NOT NULL DEFAULT '';
ALTER TABLE categories ADD COLUMN visibility varchar(10) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE categories ADD CONSTRAINT ck_categories_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE'));
-- 비공개 다이어리는 적어서 부분 인덱스로 그 목록을 바로 찾는다 (공개 글 조건에서 매번 쓴다)
CREATE INDEX idx_categories_private ON categories (id) WHERE visibility = 'PRIVATE';
