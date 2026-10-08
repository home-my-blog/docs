package com.myblog.post;

import com.myblog.common.MyBlogProperties;
import com.myblog.common.PageRequests;
import com.myblog.common.PageResponse;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.image.ImageService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/**
 * 글 조회는 모두 여기를 거친다 (헌법 II, CF-13-4).
 * 공개 범위 조건은 아래 두 개뿐이다.
 * - PUBLIC_ONLY: 홈·주제·검색·태그·이전/다음·인기 글
 * - VISIBLE_TO: 글 상세, 내 블로그 목록 (공개 글 또는 내가 쓴 글)
 */
@Service
public class PostQueryService {
    static final String PUBLIC_ONLY = "p.visibility = 'PUBLIC'";
    static final String VISIBLE_TO = "(p.visibility = 'PUBLIC' OR p.author_id = :viewer)";

    public record PostRow(long id, long blogId, String blogName, long authorId, long categoryId, String categoryName,
                          String topicCode, String topicName, String title, String body, String visibility,
                          String coverKey, OffsetDateTime createdAt, OffsetDateTime contentUpdatedAt,
                          long viewCount) {}

    private static final String SELECT = """
            SELECT p.id, p.blog_id, b.name AS blog_name, p.author_id, p.category_id, c.name AS category_name,
                   t.code AS topic_code, t.name AS topic_name, p.title, p.body, p.visibility,
                   pi.storage_key AS cover_key, p.created_at, p.content_updated_at, p.view_count
            FROM post p
            JOIN blog b ON b.id = p.blog_id
            JOIN topic t ON t.id = b.topic_id
            JOIN category c ON c.id = p.category_id
            LEFT JOIN post_image pi ON pi.id = p.cover_image_id
            """;
    private static final String ORDER = " ORDER BY p.created_at DESC, p.id DESC";

    private final JdbcClient jdbc;
    private final MyBlogProperties props;

    public PostQueryService(JdbcClient jdbc, MyBlogProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    private static long viewerParam(Long viewer) {
        return viewer == null ? -1L : viewer;
    }

    /** 볼 수 없는 글은 없는 글과 같다: POST_NOT_FOUND (CF-09-4). */
    public PostRow requireVisible(long postId, Long viewer) {
        return jdbc.sql(SELECT + " WHERE p.id = :id AND " + VISIBLE_TO)
                .param("id", postId).param("viewer", viewerParam(viewer))
                .query(PostRow.class).optional()
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    /** 작성자만 고칠 수 있다. 남의 글은 없는 글과 같다 (CF-05-12). */
    public PostRow requireAuthor(long postId, Long me) {
        PostRow p = requireVisible(postId, me);
        if (me == null || p.authorId() != me) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        return p;
    }

    public Map<String, Object> summary(PostRow p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.id());
        m.put("blog", Map.of("id", p.blogId(), "name", p.blogName()));
        m.put("topic", Map.of("code", p.topicCode(), "name", p.topicName()));
        m.put("category", Map.of("id", p.categoryId(), "name", p.categoryName()));
        m.put("title", p.title());
        m.put("excerpt", ExcerptMaker.excerpt(p.body(), props.post().excerptLength()));
        m.put("createdAt", p.createdAt());
        m.put("visibility", p.visibility());
        m.put("coverImageUrl", p.coverKey() == null ? null : ImageService.url(p.coverKey()));
        m.put("viewCount", p.viewCount());
        return m;
    }

    private PageResponse<Map<String, Object>> page(String where, Map<String, Object> params, Integer page) {
        int size = props.post().pageSize();
        long total = jdbc.sql("SELECT count(*) FROM post p JOIN blog b ON b.id = p.blog_id "
                        + "JOIN topic t ON t.id = b.topic_id JOIN category c ON c.id = p.category_id WHERE " + where)
                .params(params).query(Long.class).single();
        return PageRequests.page(page, size, total, (limit, offset) -> {
            Map<String, Object> all = new LinkedHashMap<>(params);
            all.put("limit", limit);
            all.put("offset", offset);
            return jdbc.sql(SELECT + " WHERE " + where + ORDER + " LIMIT :limit OFFSET :offset")
                    .params(all).query(PostRow.class).list();
        }).map(this::summary);
    }

    private List<Map<String, Object>> top(String where, Map<String, Object> params, int limit) {
        Map<String, Object> all = new LinkedHashMap<>(params);
        all.put("limit", limit);
        return jdbc.sql(SELECT + " WHERE " + where + ORDER + " LIMIT :limit").params(all)
                .query(PostRow.class).list().stream().map(this::summary).toList();
    }

    /** 블로그 글 목록. 주인이 자기 블로그를 볼 때만 비공개 포함 (CF-10-6). */
    public PageResponse<Map<String, Object>> blogPosts(long blogId, Long categoryId, Long viewer, Integer page) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("blog", blogId);
        params.put("viewer", viewerParam(viewer));
        String where = "p.blog_id = :blog AND " + VISIBLE_TO;
        if (categoryId != null) {
            where += " AND p.category_id = :category";
            params.put("category", categoryId);
        }
        return page(where, params, page);
    }

    public record Neighbor(long id, String title) {}

    /** 같은 블로그의 공개 글 중 바로 앞(이전, 더 오래된)·뒤(다음, 더 새로운) 글 (CF-09-2). */
    public Map<String, Optional<Neighbor>> neighbors(PostRow p) {
        var params = Map.<String, Object>of("blog", p.blogId(), "at", p.createdAt(), "id", p.id());
        var prev = jdbc.sql("SELECT p.id, p.title FROM post p WHERE p.blog_id = :blog AND " + PUBLIC_ONLY
                        + " AND (p.created_at, p.id) < (:at, :id) ORDER BY p.created_at DESC, p.id DESC LIMIT 1")
                .params(params).query(Neighbor.class).optional();
        var next = jdbc.sql("SELECT p.id, p.title FROM post p WHERE p.blog_id = :blog AND " + PUBLIC_ONLY
                        + " AND (p.created_at, p.id) > (:at, :id) ORDER BY p.created_at, p.id LIMIT 1")
                .params(params).query(Neighbor.class).optional();
        return Map.of("prev", prev, "next", next);
    }

    /** 같은 블로그의 다른 공개 글 (요구사항.md 3.5). */
    public List<Map<String, Object>> otherPosts(PostRow p) {
        return top("p.blog_id = :blog AND p.id <> :id AND " + PUBLIC_ONLY,
                Map.of("blog", p.blogId(), "id", p.id()), props.post().otherPosts());
    }

    /** 태그별 공개 글 (CF-20-3). */
    public PageResponse<Map<String, Object>> tagPosts(String tagKey, Integer page) {
        return page(PUBLIC_ONLY + " AND p.id IN (SELECT pt.post_id FROM post_tag pt JOIN tag g ON g.id = pt.tag_id"
                + " WHERE g.name_key = :tag)", Map.of("tag", tagKey), page);
    }

    /**
     * 공개 글 검색: 단어마다 제목·본문·분류·블로그 이름·주제 이름 중 하나에 들어 있어야 한다(AND).
     * patterns는 이미 LIKE 이스케이프된 '%단어%' 값이다 (CF-11, 요구사항.md 3.8).
     */
    public PageResponse<Map<String, Object>> search(List<String> patterns, Integer page) {
        Map<String, Object> params = new LinkedHashMap<>();
        List<String> conds = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            String k = "w" + i;
            params.put(k, patterns.get(i));
            conds.add("(p.title ILIKE :" + k + " ESCAPE '\\' OR p.body ILIKE :" + k + " ESCAPE '\\' OR c.name ILIKE :"
                    + k + " ESCAPE '\\' OR b.name ILIKE :" + k + " ESCAPE '\\' OR t.name ILIKE :" + k + " ESCAPE '\\')");
        }
        return page(PUBLIC_ONLY + " AND " + String.join(" AND ", conds), params, page);
    }

    public List<Map<String, Object>> latestPublic(int limit) {
        return top(PUBLIC_ONLY, Map.of(), limit);
    }

    public List<Map<String, Object>> latestPublicInTopic(long topicId, int limit) {
        return top(PUBLIC_ONLY + " AND b.topic_id = :topic", Map.of("topic", topicId), limit);
    }

    public long countPublicInTopic(long topicId) {
        return jdbc.sql("SELECT count(*) FROM post p JOIN blog b ON b.id = p.blog_id WHERE " + PUBLIC_ONLY
                + " AND b.topic_id = :topic").param("topic", topicId).query(Long.class).single();
    }

    /** 오늘의 이슈: 대표 글을 최신순으로, 모자라면 최근 N일 조회수 많은 공개 글로 채운다 (research §12). */
    public List<Map<String, Object>> featured(int limit, OffsetDateTime popularSince) {
        List<Map<String, Object>> list = new ArrayList<>(top(PUBLIC_ONLY + " AND p.featured", Map.of(), limit));
        if (list.size() < limit) {
            List<Object> taken = list.stream().map(m -> m.get("id")).toList();
            var params = new LinkedHashMap<String, Object>();
            params.put("since", popularSince.toLocalDate());
            params.put("limit", limit - list.size());
            params.put("taken", taken.isEmpty() ? List.of(-1L) : taken);
            jdbc.sql(SELECT + " LEFT JOIN (SELECT post_id, sum(views) AS v FROM daily_stat WHERE post_id IS NOT NULL"
                            + " AND stat_date >= :since GROUP BY post_id) s ON s.post_id = p.id WHERE " + PUBLIC_ONLY
                            + " AND p.id NOT IN (:taken) ORDER BY coalesce(s.v, 0) DESC, p.created_at DESC LIMIT :limit")
                    .params(params).query(PostRow.class).list().forEach(r -> list.add(summary(r)));
        }
        return list;
    }

    /** 대시보드 인기 글: 최근 N일 조회수 많은 공개 글 (BM-02-3). */
    public List<Map<String, Object>> popularInBlog(long blogId, java.time.LocalDate since, int limit) {
        return jdbc.sql("""
                SELECT p.id, p.title, sum(s.views) AS views FROM daily_stat s JOIN post p ON p.id = s.post_id
                WHERE s.blog_id = :blog AND s.stat_date >= :since AND\s""" + PUBLIC_ONLY + """

                GROUP BY p.id, p.title ORDER BY views DESC, p.id DESC LIMIT :limit""")
                .param("blog", blogId).param("since", since).param("limit", limit)
                .query((rs, i) -> Map.<String, Object>of("id", rs.getLong("id"), "title", rs.getString("title"),
                        "views", rs.getLong("views"))).list();
    }
}
