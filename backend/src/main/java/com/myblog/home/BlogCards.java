package com.myblog.home;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 블로그 카드: 주인 첫 글자, 주제, 이름, 주인, 소개, 공개 글 수, 최근 글 날짜 (요구사항.md 3.2). */
@Component
public class BlogCards {
    private final JdbcClient jdbc;

    public BlogCards(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SELECT = """
            SELECT b.id, b.name, b.description, m.nickname, t.code, t.name AS topic_name,
                   (SELECT count(*) FROM posts p WHERE p.blog_id = b.id AND p.visibility = 'PUBLIC') AS post_count,
                   (SELECT max(p.created_at) FROM posts p WHERE p.blog_id = b.id AND p.visibility = 'PUBLIC') AS last_post_at
            FROM blogs b JOIN members m ON m.id = b.owner_id JOIN topics t ON t.id = b.topic_id
            """;

    private static Map<String, Object> card(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> m = new LinkedHashMap<>();
        String nickname = rs.getString("nickname");
        m.put("id", rs.getLong("id"));
        m.put("name", rs.getString("name"));
        m.put("description", rs.getString("description"));
        m.put("ownerNickname", nickname);
        m.put("owner", Map.of("nickname", nickname, "initial", nickname.substring(0, nickname.offsetByCodePoints(0, 1))));
        m.put("topic", Map.of("code", rs.getString("code"), "name", rs.getString("topic_name")));
        m.put("postCount", rs.getLong("post_count"));
        m.put("lastPostAt", rs.getObject("last_post_at", OffsetDateTime.class));
        return m;
    }

    public List<Map<String, Object>> cards(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        var byId = new LinkedHashMap<Long, Map<String, Object>>();
        jdbc.sql(SELECT + " WHERE b.id IN (:ids)").param("ids", ids)
                .query((rs, i) -> card(rs)).list().forEach(c -> byId.put((Long) c.get("id"), c));
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    /** 최근에 글을 쓴 블로그부터. */
    public List<Map<String, Object>> recent(Long topicId, int limit) {
        String where = topicId == null ? "" : " WHERE b.topic_id = :topic";
        var stmt = jdbc.sql(SELECT + where + " ORDER BY last_post_at DESC NULLS LAST, b.id DESC LIMIT :limit")
                .param("limit", limit);
        if (topicId != null) {
            stmt = stmt.param("topic", topicId);
        }
        return stmt.query((rs, i) -> card(rs)).list();
    }

    public long count(Long topicId) {
        return topicId == null
                ? jdbc.sql("SELECT count(*) FROM blogs").query(Long.class).single()
                : jdbc.sql("SELECT count(*) FROM blogs WHERE topic_id = ?").param(topicId).query(Long.class).single();
    }
}
