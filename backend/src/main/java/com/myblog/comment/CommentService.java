package com.myblog.comment;

import com.myblog.common.KoreanClock;
import com.myblog.common.MyBlogProperties;
import com.myblog.common.RedisGuard;
import com.myblog.common.Texts;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import com.myblog.post.PostQueryService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

/** 블로그 글 댓글 (CF-18). */
@Service
public class CommentService {
    public record CommentRow(long id, long postId, Long authorId, String authorNickname, String body,
                             OffsetDateTime createdAt, long blogOwnerId) {}

    private final JdbcClient jdbc;
    private final PostQueryService posts;
    private final StringRedisTemplate redis;
    private final RedisGuard guard;
    private final MyBlogProperties.Comment rules;
    private final KoreanClock clock;

    public CommentService(JdbcClient jdbc, PostQueryService posts, StringRedisTemplate redis, RedisGuard guard,
                          MyBlogProperties props, KoreanClock clock) {
        this.jdbc = jdbc;
        this.posts = posts;
        this.redis = redis;
        this.guard = guard;
        this.rules = props.comment();
        this.clock = clock;
    }

    private static final String SELECT = """
            SELECT cm.id, cm.post_id, cm.author_id, m.nickname AS author_nickname, cm.body, cm.created_at,
                   b.owner_id AS blog_owner_id
            FROM comment cm JOIN post p ON p.id = cm.post_id JOIN blog b ON b.id = p.blog_id
            LEFT JOIN member m ON m.id = cm.author_id
            """;

    static Map<String, Object> view(CommentRow c, Long viewer) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", c.id());
        v.put("author", c.authorId() == null ? null : Map.of("id", c.authorId(), "nickname", c.authorNickname()));
        v.put("body", c.body());
        v.put("createdAt", c.createdAt());
        v.put("canDelete", viewer != null && (viewer.equals(c.authorId()) || viewer == c.blogOwnerId()));
        return v;
    }

    /** 오래된 댓글이 위 (CF-18-3). 작성자가 탈퇴했으면 author = null → "탈퇴한 사용자". */
    public List<Map<String, Object>> list(long postId, Long viewer) {
        posts.requireVisible(postId, viewer);
        return jdbc.sql(SELECT + " WHERE cm.post_id = ? ORDER BY cm.created_at, cm.id").param(postId)
                .query(CommentRow.class).list().stream().map(c -> view(c, viewer)).toList();
    }

    public long count(long postId) {
        return jdbc.sql("SELECT count(*) FROM comment WHERE post_id = ?").param(postId).query(Long.class).single();
    }

    public Map<String, Object> create(long postId, long memberId, String rawBody) {
        posts.requireVisible(postId, memberId);
        String body = rawBody == null ? "" : rawBody.strip();
        if (body.isEmpty()) {
            throw ApiException.field("body", Messages.COMMENT_REQUIRED);
        }
        if (Texts.length(body) > rules.bodyMax()) {
            throw ApiException.field("body", Messages.COMMENT_TOO_LONG);
        }
        boolean allowed = guard.optional(() -> redis.opsForValue()
                .setIfAbsent("comment:cooldown:" + memberId, "1", rules.cooldown())).orElse(true);
        if (!allowed) {
            throw new ApiException(ErrorCode.COMMENT_TOO_SOON); // 5초 안에 다시 등록 (CF-18-7)
        }
        var keys = new GeneratedKeyHolder();
        jdbc.sql("INSERT INTO comment (post_id, author_id, body, created_at) VALUES (?, ?, ?, ?)")
                .params(postId, memberId, body, clock.nowOffset()).update(keys, "id");
        long id = keys.getKey().longValue();
        return view(jdbc.sql(SELECT + " WHERE cm.id = ?").param(id).query(CommentRow.class).single(), memberId);
    }

    /** 댓글 작성자와 그 글의 블로그 주인만 지운다 (CF-18-4). */
    public void delete(long commentId, long memberId) {
        CommentRow c = jdbc.sql(SELECT + " WHERE cm.id = ?").param(commentId).query(CommentRow.class).optional()
                .orElseThrow(() -> new ApiException(ErrorCode.COMMENT_NOT_FOUND));
        if (!Long.valueOf(memberId).equals(c.authorId()) && c.blogOwnerId() != memberId) {
            throw new ApiException(ErrorCode.COMMENT_NOT_FOUND);
        }
        jdbc.sql("DELETE FROM comment WHERE id = ?").param(commentId).update();
    }
}
