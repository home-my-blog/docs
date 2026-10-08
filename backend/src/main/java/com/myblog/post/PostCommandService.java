package com.myblog.post;

import com.myblog.blog.BlogService;
import com.myblog.blog.CategoryService;
import com.myblog.common.KoreanClock;
import com.myblog.common.MyBlogProperties;
import com.myblog.common.Texts;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.image.ImageService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 글 작성·수정·삭제 (CF-05, CF-13, CF-20, CF-22). */
@Service
public class PostCommandService {
    public record Request(String title, String body, Long categoryId, String visibility, List<String> tags,
                          List<Long> imageIds, Long coverImageId) {}

    private record Valid(String title, String body, long categoryId, String visibility, List<String> tags) {}

    private final JdbcClient jdbc;
    private final MyBlogProperties props;
    private final BlogService blogs;
    private final CategoryService categories;
    private final PostQueryService queries;
    private final TagService tags;
    private final ImageService images;
    private final KoreanClock clock;

    public PostCommandService(JdbcClient jdbc, MyBlogProperties props, BlogService blogs, CategoryService categories,
                              PostQueryService queries, TagService tags, ImageService images, KoreanClock clock) {
        this.jdbc = jdbc;
        this.props = props;
        this.blogs = blogs;
        this.categories = categories;
        this.queries = queries;
        this.tags = tags;
        this.images = images;
        this.clock = clock;
    }

    private Valid validate(long blogId, Request req) {
        String title = Texts.trim(req.title());
        if (title.isEmpty()) {
            throw ApiException.field("title", Messages.TITLE_REQUIRED);
        }
        if (Texts.length(title) > props.post().titleMax()) {
            throw ApiException.field("title", Messages.TITLE_TOO_LONG);
        }
        String body = req.body() == null ? "" : req.body();
        if (body.isBlank()) {
            throw ApiException.field("body", Messages.BODY_REQUIRED);
        }
        if (Texts.length(body) > props.post().bodyMax()) {
            throw ApiException.field("body", Messages.BODY_TOO_LONG);
        }
        if (req.categoryId() == null) {
            throw ApiException.field("categoryId", Messages.CATEGORY_REQUIRED);
        }
        var category = categories.get(req.categoryId());
        if (category.blogId() != blogId) {
            throw ApiException.field("categoryId", Messages.CATEGORY_REQUIRED);
        }
        String visibility = req.visibility() == null ? "PUBLIC" : req.visibility();
        if (!visibility.equals("PUBLIC") && !visibility.equals("PRIVATE")) {
            throw ApiException.field("visibility", "공개 여부가 올바르지 않습니다");
        }
        return new Valid(title, body, category.id(), visibility, tags.normalize(req.tags()));
    }

    private Long cover(Request req, long memberId) {
        if (req.coverImageId() == null) {
            return null;
        }
        if (!images.ownedBy(req.coverImageId(), memberId)) {
            throw ApiException.field("coverImageId", "대표 사진을 다시 골라 주세요");
        }
        return req.coverImageId();
    }

    private List<Long> imageIds(Request req) {
        var ids = new java.util.LinkedHashSet<Long>(req.imageIds() == null ? List.of() : req.imageIds());
        if (req.coverImageId() != null) {
            ids.add(req.coverImageId());
        }
        return List.copyOf(ids);
    }

    /** 자기 블로그에만 쓸 수 있다 (CF-05-1). */
    @Transactional
    public long create(long blogId, long memberId, Request req) {
        blogs.requireOwner(blogId, memberId);
        Valid v = validate(blogId, req);
        Long cover = cover(req, memberId);
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO posts (blog_id, author_id, category_id, title, body, visibility, cover_image_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                .params(blogId, memberId, v.categoryId(), v.title(), v.body(), v.visibility(), cover,
                        clock.nowOffset())
                .update(keys, "id");
        long id = keys.getKey().longValue();
        tags.replace(id, v.tags());
        images.attach(id, memberId, imageIds(req));
        return id;
    }

    /** 작성자만. 제목·본문·분류·공개 여부가 실제로 바뀐 때만 수정 시각을 갱신한다 (CF-05-13). */
    @Transactional
    public void update(long postId, long memberId, Request req) {
        var p = queries.requireAuthor(postId, memberId);
        Valid v = validate(p.blogId(), req);
        Long cover = cover(req, memberId);
        boolean changed = !p.title().equals(v.title()) || !p.body().equals(v.body())
                || p.categoryId() != v.categoryId() || !p.visibility().equals(v.visibility());
        OffsetDateTime updatedAt = changed ? clock.nowOffset() : p.contentUpdatedAt();
        jdbc.sql("""
                UPDATE posts SET title = ?, body = ?, category_id = ?, visibility = ?, cover_image_id = ?,
                                content_updated_at = ?
                WHERE id = ?""")
                .params(v.title(), v.body(), v.categoryId(), v.visibility(), cover, updatedAt, postId).update();
        tags.replace(postId, v.tags());
        images.attach(postId, memberId, imageIds(req));
        // 본문에서 빠진 이미지는 연결만 풀린다. 24시간 정리 작업이 지운다.
    }

    /** 댓글·좋아요·태그 연결·이미지 기록은 FK로 함께 지우고, 파일은 커밋 후 지운다 (CF-05-15). */
    @Transactional
    public long delete(long postId, long memberId) {
        var p = queries.requireAuthor(postId, memberId);
        List<String> keys = images.keysOfPost(postId);
        jdbc.sql("DELETE FROM posts WHERE id = ?").param(postId).update();
        images.deleteFilesAfterCommit(keys);
        return p.blogId();
    }

    /** 글쓰기 기본 분류: 마지막으로 쓴 글의 분류, 처음이면 "미분류" (CF-05-5). */
    public Map<String, Long> lastCategory(long memberId) {
        long blogId = blogs.requireOwnBlogId(memberId);
        Long id = jdbc.sql("""
                SELECT category_id FROM posts WHERE blog_id = ? ORDER BY created_at DESC, id DESC LIMIT 1""")
                .param(blogId).query(Long.class).optional()
                .orElseGet(() -> jdbc.sql("SELECT id FROM categories WHERE blog_id = ? AND is_default")
                        .param(blogId).query(Long.class).single());
        return Map.of("categoryId", id);
    }
}
