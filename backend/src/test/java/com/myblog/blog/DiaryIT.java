package com.myblog.blog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

/** 다이어리(분류) 설정, 비공개 다이어리, 블로그 글 정렬·태그 거르기 (데모 블로그 화면) */
class DiaryIT extends IntegrationTest {

    private long write(MockHttpSession s, long blog, long category, String title, List<String> tags) throws Exception {
        var m = new HashMap<String, Object>();
        m.put("title", title);
        m.put("body", "본문");
        m.put("categoryId", category);
        m.put("visibility", "PUBLIC");
        m.put("tags", tags);
        return read(mvc.perform(postJson("/api/blogs/" + blog + "/posts", m).session(s))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    @Test
    void diarySettingsAndPrivateDiary() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blog = blogIdOf(a);
        long open = defaultCategory(blog);
        long secret = read(mvc.perform(postJson("/api/blogs/" + blog + "/categories", Map.of("name", "비밀 일기")).session(a))
                .andReturn()).get("id").asLong();

        // 설정: 보낸 칸만 바뀐다
        mvc.perform(jsonRequest(patch("/api/categories/" + secret),
                Map.of("description", "나만 보는 다이어리", "colorIndex", 4, "visibility", "PRIVATE")).session(a))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(patch("/api/categories/" + secret), Map.of("colorIndex", 99)).session(a))
                .andExpect(status().isBadRequest());
        mvc.perform(jsonRequest(patch("/api/categories/" + secret), Map.of("description", "가".repeat(101))).session(a))
                .andExpect(jsonPath("$.error.fields.description").exists());
        mvc.perform(jsonRequest(patch("/api/categories/" + secret), Map.of("visibility", "PUBLIC")).session(b))
                .andExpect(status().isNotFound());

        long shown = write(a, blog, open, "공개 다이어리 글", List.of());
        long hidden = write(a, blog, secret, "비밀 다이어리 글", List.of());

        // 주인은 비공개 다이어리와 그 글까지, 다른 사람은 못 본다 (글이 공개여도)
        mvc.perform(get("/api/blogs/" + blog + "/categories").session(a))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("비밀 일기"))
                .andExpect(jsonPath("$[1].description").value("나만 보는 다이어리"))
                .andExpect(jsonPath("$[1].colorIndex").value(4))
                .andExpect(jsonPath("$[1].visibility").value("PRIVATE"))
                .andExpect(jsonPath("$[1].postCount").value(1));
        mvc.perform(get("/api/blogs/" + blog + "/categories").session(b)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/blogs/" + blog + "/posts").session(b)).andExpect(jsonPath("$.totalItems").value(1));
        mvc.perform(get("/api/blogs/" + blog + "/posts").session(a)).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get("/api/posts/" + hidden).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + hidden).session(a)).andExpect(status().isOk());
        mvc.perform(get("/api/blogs/" + blog + "/posts")).andExpect(jsonPath("$.items[0].category.colorIndex").value(0));
        mvc.perform(get("/api/home")).andExpect(jsonPath("$.latestPosts.length()").value(1));
        mvc.perform(get("/api/search").param("q", "다이어리")).andExpect(jsonPath("$.posts.totalItems").value(1));

        // 다시 공개로 바꾸면 보인다
        mvc.perform(jsonRequest(patch("/api/categories/" + secret), Map.of("visibility", "PUBLIC")).session(a));
        mvc.perform(get("/api/posts/" + hidden).session(b)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + shown).session(b)).andExpect(status().isOk());
    }

    @Test
    void blogPostsSortAndTagFilter() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blog = blogIdOf(a);
        long cat = defaultCategory(blog);
        long first = write(a, blog, cat, "첫 글", List.of("여행", "제주"));
        long second = write(a, blog, cat, "둘째 글", List.of("여행"));
        long third = write(a, blog, cat, "셋째 글", List.of());
        jdbc.sql("UPDATE posts SET created_at = created_at - (interval '1 hour' * (4 - id))").update();
        mvc.perform(put("/api/posts/" + second + "/like").with(csrf()).session(b)).andExpect(status().isOk());

        String url = "/api/blogs/" + blog + "/posts";
        mvc.perform(get(url)).andExpect(jsonPath("$.items[0].id").value(third));
        mvc.perform(get(url).param("sort", "oldest")).andExpect(jsonPath("$.items[0].id").value(first));
        mvc.perform(get(url).param("sort", "popular")).andExpect(jsonPath("$.items[0].id").value(second));
        mvc.perform(get(url).param("tag", "#여행")).andExpect(jsonPath("$.totalItems").value(2));
        mvc.perform(get(url).param("tag", "제주")).andExpect(jsonPath("$.items[0].id").value(first));

        mvc.perform(get("/api/blogs/" + blog + "/tags"))
                .andExpect(jsonPath("$[0].name").value("여행"))
                .andExpect(jsonPath("$[0].count").value(2))
                .andExpect(jsonPath("$[1].name").value("제주"));
    }

    @Test
    void pinnedPostsUpToThree() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blog = blogIdOf(a);
        long cat = defaultCategory(blog);
        long p1 = write(a, blog, cat, "하나", List.of());
        long p2 = write(a, blog, cat, "둘", List.of());
        long p3 = write(a, blog, cat, "셋", List.of());
        long p4 = write(a, blog, cat, "넷", List.of());

        mvc.perform(put("/api/posts/" + p1 + "/pin").with(csrf()).session(b)).andExpect(status().isNotFound());
        for (long id : new long[] {p2, p1, p3}) {
            mvc.perform(put("/api/posts/" + id + "/pin").with(csrf()).session(a)).andExpect(jsonPath("$.pinned").value(true));
        }
        mvc.perform(put("/api/posts/" + p4 + "/pin").with(csrf()).session(a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("PIN_LIMIT"));
        mvc.perform(get("/api/blogs/" + blog + "/pinned"))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(p2))
                .andExpect(jsonPath("$[1].id").value(p1));
        mvc.perform(get("/api/posts/" + p2).session(a)).andExpect(jsonPath("$.pinned").value(true));

        // 해제하면 다시 고정할 수 있다. 비공개로 바꾼 대표글은 다른 사람에게 안 보인다
        mvc.perform(put("/api/posts/" + p1 + "/pin").with(csrf()).session(a)).andExpect(jsonPath("$.pinned").value(false));
        mvc.perform(put("/api/posts/" + p4 + "/pin").with(csrf()).session(a)).andExpect(jsonPath("$.pinned").value(true));
        jdbc.sql("UPDATE posts SET visibility = 'PRIVATE' WHERE id = ?").param(p3).update();
        mvc.perform(get("/api/blogs/" + blog + "/pinned").session(b)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/blogs/" + blog + "/pinned").session(a)).andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void diaryEditMovesAndDeletesManyPosts() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blog = blogIdOf(a);
        long cat = defaultCategory(blog);
        long other = read(mvc.perform(postJson("/api/blogs/" + blog + "/categories", Map.of("name", "여행")).session(a))
                .andReturn()).get("id").asLong();
        long p1 = write(a, blog, cat, "하나", List.of());
        long p2 = write(a, blog, cat, "둘", List.of());
        long p3 = write(a, blog, cat, "셋", List.of());
        long bBlog = blogIdOf(b);
        long bPost = write(b, bBlog, defaultCategory(bBlog), "비이 글", List.of());
        String move = "/api/blogs/" + blog + "/posts/move";
        String del = "/api/blogs/" + blog + "/posts/delete";

        mvc.perform(postJson(move, Map.of("postIds", List.of(), "categoryId", other)).session(a))
                .andExpect(jsonPath("$.error.fields.postIds").exists());
        mvc.perform(postJson(move, Map.of("postIds", List.of(p1, bPost), "categoryId", other)).session(a))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(move, Map.of("postIds", List.of(p1), "categoryId", other)).session(b))
                .andExpect(status().isNotFound());
        mvc.perform(postJson(move, Map.of("postIds", List.of(p1), "categoryId", defaultCategory(bBlog))).session(a))
                .andExpect(status().isBadRequest());

        mvc.perform(postJson(move, Map.of("postIds", List.of(p1, p2), "categoryId", other)).session(a))
                .andExpect(jsonPath("$.moved").value(2));
        mvc.perform(get("/api/blogs/" + blog + "/posts").param("categoryId", String.valueOf(other)))
                .andExpect(jsonPath("$.totalItems").value(2));

        mvc.perform(postJson(del, Map.of("postIds", List.of(p2, p3))).session(a)).andExpect(jsonPath("$.deleted").value(2));
        mvc.perform(get("/api/blogs/" + blog + "/posts")).andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(p1));
        mvc.perform(get("/api/posts/" + bPost)).andExpect(status().isOk());
    }
}
