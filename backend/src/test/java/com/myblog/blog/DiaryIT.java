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
}
