package com.myblog.manage;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** 새 댓글 = 마지막으로 댓글 관리를 연 뒤 내 블로그 글에 달린 댓글, 내가 쓴 것은 제외 (BM-05-4). */
@Component
public class NewCommentCounter {
    private final JdbcClient jdbc;

    public NewCommentCounter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public long count(long blogId, long ownerId) {
        return jdbc.sql("""
                SELECT count(*) FROM comment cm JOIN post p ON p.id = cm.post_id JOIN blog b ON b.id = p.blog_id
                WHERE b.id = ? AND cm.created_at > b.comments_seen_at AND cm.author_id IS DISTINCT FROM ?""")
                .params(blogId, ownerId).query(Long.class).single();
    }
}
