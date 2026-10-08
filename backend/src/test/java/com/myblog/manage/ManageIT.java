package com.myblog.manage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class ManageIT extends IntegrationTest {

    @Test
    void BM_06_3_4_viewsAndVisitors() throws Exception {
        MockHttpSession owner = member("주인", "owner@example.com");
        long p1 = writePost(owner, "하나", "본문", "PUBLIC");
        long p2 = writePost(owner, "둘", "본문", "PUBLIC");
        long p3 = writePost(owner, "셋", "본문", "PUBLIC");
        long priv = writePost(owner, "비공개", "본문", "PRIVATE");
        Cookie vid = new Cookie("MYBLOG_VID", "11111111-1111-1111-1111-111111111111");
        for (long p : new long[] {p1, p2, p3, p1}) { // p1을 30분 안에 다시 열면 세지 않는다
            mvc.perform(get("/api/posts/" + p).cookie(vid)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/posts/" + p1).session(owner)); // 주인 본인은 세지 않는다
        mvc.perform(get("/api/posts/" + priv).session(owner));
        mvc.perform(get("/api/manage/dashboard").session(owner))
                .andExpect(jsonPath("$.views.today").value(3))
                .andExpect(jsonPath("$.visitors.today").value(1))
                .andExpect(jsonPath("$.daily.length()").value(30))
                .andExpect(jsonPath("$.popularPosts.length()").value(3))
                .andExpect(jsonPath("$.popularPosts[0].viewCount").exists());
        mvc.perform(get("/api/manage/posts").session(owner).param("visibility", "PRIVATE"))
                .andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/manage/stats").session(owner).param("days", "7"))
                .andExpect(jsonPath("$.daily.length()").value(7));
    }

    @Test
    void BM_05_newComments() throws Exception {
        MockHttpSession owner = member("주인", "owner@example.com");
        MockHttpSession guest = member("손님", "guest@example.com");
        long post = writePost(owner, "글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "손님 댓글")).session(guest));
        redis.delete(redis.keys("comment:cooldown:*"));
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "손님 댓글 2")).session(guest));
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "내 댓글")).session(owner));
        mvc.perform(get("/api/auth/me").session(owner)).andExpect(jsonPath("$.newCommentCount").value(2));
        mvc.perform(get("/api/manage/comments").session(owner))
                .andExpect(jsonPath("$.items[0].body").doesNotExist())
                .andExpect(jsonPath("$.items[0].preview").value("내 댓글"))
                .andExpect(jsonPath("$.items[0].isNew").value(false))
                .andExpect(jsonPath("$.items[1].isNew").value(true));
        mvc.perform(get("/api/auth/me").session(owner)).andExpect(jsonPath("$.newCommentCount").value(0));
    }

    @Test
    void BM_01_2_requiresLogin() throws Exception {
        mvc.perform(get("/api/manage/dashboard")).andExpect(status().isUnauthorized());
    }
}
