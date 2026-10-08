package com.myblog.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class PostFlowIT extends IntegrationTest {

    private Map<String, Object> postBody(long categoryId, String title, String body, String visibility) {
        var m = new HashMap<String, Object>();
        m.put("title", title);
        m.put("body", body);
        m.put("categoryId", categoryId);
        m.put("visibility", visibility);
        return m;
    }

    @Test
    void CF_05_3_4_titleAndBodyRequired() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        long blog = blogIdOf(s);
        long cat = defaultCategory(blog);
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", postBody(cat, "   ", "본문", "PUBLIC")).session(s))
                .andExpect(jsonPath("$.error.fields.title").value("제목을 입력해 주세요"));
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", postBody(cat, "제목", " \n ", "PUBLIC")).session(s))
                .andExpect(jsonPath("$.error.fields.body").value("본문을 입력해 주세요"));
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", postBody(cat, "가".repeat(101), "본문", "PUBLIC")).session(s))
                .andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", postBody(cat, "가".repeat(100), "나".repeat(10000), "PUBLIC"))
                .session(s)).andExpect(status().isCreated());
    }

    @Test
    void CF_05_1_12_NF_02_cannotWriteOrEditOthers() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blogA = blogIdOf(a);
        long postA = writePost(a, "에이의 글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/blogs/" + blogA + "/posts", postBody(defaultCategory(blogA), "침입", "본문", "PUBLIC"))
                .session(b)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(put("/api/posts/" + postA), postBody(defaultCategory(blogA), "수정", "본문", "PUBLIC"))
                .session(b)).andExpect(jsonPath("$.error.code").value("POST_NOT_FOUND"));
        mvc.perform(delete("/api/posts/" + postA).with(csrf()).session(b)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + postA + "/edit").session(b)).andExpect(status().isNotFound());
        mvc.perform(postJson("/api/blogs/" + blogA + "/posts", Map.of()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void CF_13_4_SC_004_privatePostHiddenEverywhere() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blogA = blogIdOf(a);
        long pub1 = writePost(a, "공개 단풍 하나", "단풍 명소", "PUBLIC");
        long priv = writePost(a, "비밀 단풍", "단풍 명소 비밀", "PRIVATE");
        long pub2 = writePost(a, "공개 단풍 둘", "단풍 명소", "PUBLIC");
        jdbc.sql("UPDATE posts SET featured = true").update();

        for (MockHttpSession viewer : new MockHttpSession[] {b, new MockHttpSession()}) {
            mvc.perform(get("/api/posts/" + priv).session(viewer))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.message").value("존재하지 않는 글입니다"));
            mvc.perform(get("/api/blogs/" + blogA + "/posts").session(viewer))
                    .andExpect(jsonPath("$.totalItems").value(2));
            mvc.perform(get("/api/blogs/" + blogA + "/categories").session(viewer))
                    .andExpect(jsonPath("$[0].postCount").value(2));
            mvc.perform(get("/api/search").param("q", "단풍").session(viewer))
                    .andExpect(jsonPath("$.posts.totalItems").value(2));
            mvc.perform(get("/api/posts/" + pub2).session(viewer))
                    .andExpect(jsonPath("$.prev.id").value(pub1));
            mvc.perform(get("/api/posts/" + priv + "/comments").session(viewer)).andExpect(status().isNotFound());
        }
        var home = read(mvc.perform(get("/api/home")).andReturn());
        assertThat(home.get("featured").findValuesAsText("title")).doesNotContain("비밀 단풍");
        assertThat(home.get("latestPosts").findValuesAsText("title")).doesNotContain("비밀 단풍");
        // 주인에게는 보인다 (CF-10-6, CF-13-5)
        mvc.perform(get("/api/blogs/" + blogA + "/posts").session(a)).andExpect(jsonPath("$.totalItems").value(3));
        mvc.perform(get("/api/posts/" + priv).session(a)).andExpect(jsonPath("$.visibility").value("PRIVATE"));
    }

    @Test
    void CF_05_13_updatedAtOnlyWhenContentChanges() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        long blog = blogIdOf(s);
        long cat = defaultCategory(blog);
        long id = writePost(s, "제목", "본문", "PUBLIC");
        mvc.perform(jsonRequest(put("/api/posts/" + id), postBody(cat, "제목", "본문", "PUBLIC")).session(s))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.contentUpdatedAt").doesNotExist());
        mvc.perform(jsonRequest(put("/api/posts/" + id), postBody(cat, "새 제목", "본문", "PUBLIC")).session(s))
                .andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + id)).andExpect(jsonPath("$.contentUpdatedAt").exists());
    }

    @Test
    void CF_08_categoryRules() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        MockHttpSession other = member("남이", "other@example.com");
        long blog = blogIdOf(s);
        long def = defaultCategory(blog);
        long travel = read(mvc.perform(postJson("/api/blogs/" + blog + "/categories", Map.of("name", "Travel"))
                .session(s)).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(postJson("/api/blogs/" + blog + "/categories", Map.of("name", " travel ")).session(s))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NAME_TAKEN"));
        mvc.perform(postJson("/api/blogs/" + blog + "/categories", Map.of("name", "남의 분류")).session(other))
                .andExpect(status().isNotFound());
        var p = postBody(travel, "여행 글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", p).session(s)).andExpect(status().isCreated());
        mvc.perform(postJson("/api/blogs/" + blog + "/posts", p).session(s)).andExpect(status().isCreated());
        mvc.perform(delete("/api/categories/" + travel).with(csrf()).session(s))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_HAS_POSTS"))
                .andExpect(jsonPath("$.error.postCount").value(2))
                .andExpect(jsonPath("$.error.message")
                        .value("이 분류에 글이 2개 있어 삭제할 수 없습니다. 글을 다른 분류로 옮긴 뒤 삭제해 주세요"));
        mvc.perform(delete("/api/categories/" + def).with(csrf()).session(s))
                .andExpect(jsonPath("$.error.code").value("DEFAULT_CATEGORY"));
        mvc.perform(jsonRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/categories/" + def), Map.of("name", "기타")).session(s)).andExpect(status().isOk());
        mvc.perform(postJson("/api/categories/" + travel + "/move", Map.of("direction", "up")).session(s))
                .andExpect(status().isOk());
        var cats = read(mvc.perform(get("/api/blogs/" + blog + "/categories")).andReturn());
        assertThat(cats.findValuesAsText("name")).containsExactly("Travel", "기타");
    }

    @Test
    void CF_10_listPagingAndOrder() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        long blog = blogIdOf(s);
        for (int i = 1; i <= 12; i++) {
            writePost(s, "글 " + i, "본문 " + i, "PUBLIC");
        }
        var first = read(mvc.perform(get("/api/blogs/" + blog + "/posts")).andReturn());
        assertThat(first.get("items").get(0).get("title").asText()).isEqualTo("글 12");
        assertThat(first.get("items")).hasSize(10);
        assertThat(first.get("totalPages").asInt()).isEqualTo(2);
        var last = read(mvc.perform(get("/api/blogs/" + blog + "/posts").param("page", "99")).andReturn());
        assertThat(last.get("page").asInt()).isEqualTo(2);
        assertThat(last.get("items")).hasSize(2);
    }

    @Test
    void CF_11_searchAllWordsCaseInsensitive() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        writePost(s, "단풍 명소 정리", "가을 여행", "PUBLIC");
        writePost(s, "단풍 사진", "Seoul trip", "PUBLIC");
        writePost(s, "100% 할인", "특수문자 테스트", "PUBLIC");
        mvc.perform(get("/api/search").param("q", "단풍 명소")).andExpect(jsonPath("$.posts.totalItems").value(1));
        mvc.perform(get("/api/search").param("q", "seoul")).andExpect(jsonPath("$.posts.totalItems").value(1));
        mvc.perform(get("/api/search").param("q", "0%")).andExpect(jsonPath("$.posts.totalItems").value(1));
        mvc.perform(get("/api/search").param("q", "%%")).andExpect(jsonPath("$.posts.totalItems").value(0));
        mvc.perform(get("/api/search").param("q", "단")).andExpect(jsonPath("$.error.code").value("QUERY_TOO_SHORT"));
        mvc.perform(get("/api/search").param("q", "수연")).andExpect(jsonPath("$.blogs.totalItems").value(1));
    }

    @Test
    void CF_18_19_20_21_interactions() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blogA = blogIdOf(a);
        var p = postBody(defaultCategory(blogA), "태그 글", "본문", "PUBLIC");
        p.put("tags", List.of("#여행", "여행", "Jeju", "jeju"));
        long post = read(mvc.perform(postJson("/api/blogs/" + blogA + "/posts", p).session(a)).andReturn())
                .get("id").asLong();
        mvc.perform(get("/api/posts/" + post)).andExpect(jsonPath("$.tags.length()").value(2));
        p.put("tags", List.of("a", "b", "c", "d", "e", "f"));
        mvc.perform(postJson("/api/blogs/" + blogA + "/posts", p).session(a))
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_TAGS"));
        mvc.perform(get("/api/tags/여행/posts")).andExpect(jsonPath("$.totalItems").value(1));

        // 댓글: 비회원 거절, 5초 연속 거절, 오래된 순, 블로그 주인 삭제
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "안녕"))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "  ")).session(b))
                .andExpect(status().isBadRequest());
        long c1 = read(mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "첫 댓글")).session(b))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "둘째")).session(b))
                .andExpect(jsonPath("$.error.code").value("COMMENT_TOO_SOON"));
        redis.delete(redis.keys("comment:cooldown:*"));
        mvc.perform(postJson("/api/posts/" + post + "/comments", Map.of("body", "둘째")).session(b))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/posts/" + post + "/comments")).andExpect(jsonPath("$[0].body").value("첫 댓글"));
        mvc.perform(delete("/api/comments/" + c1).with(csrf()).session(a)).andExpect(status().isNoContent());

        // 좋아요: 토글, 자기 글 불가
        mvc.perform(put("/api/posts/" + post + "/like").with(csrf()).session(b))
                .andExpect(jsonPath("$.liked").value(true)).andExpect(jsonPath("$.likeCount").value(1));
        mvc.perform(put("/api/posts/" + post + "/like").with(csrf()).session(b))
                .andExpect(jsonPath("$.liked").value(false)).andExpect(jsonPath("$.likeCount").value(0));
        mvc.perform(put("/api/posts/" + post + "/like").with(csrf()).session(a))
                .andExpect(jsonPath("$.error.code").value("OWN_POST"));

        // 신고: 한 번만, 자기 글 불가
        mvc.perform(postJson("/api/posts/" + post + "/reports", Map.of("reason", "SPAM")).session(b))
                .andExpect(status().isCreated());
        mvc.perform(postJson("/api/posts/" + post + "/reports", Map.of("reason", "SPAM")).session(b))
                .andExpect(jsonPath("$.error.code").value("ALREADY_REPORTED"));
        mvc.perform(postJson("/api/posts/" + post + "/reports", Map.of("reason", "SPAM")).session(a))
                .andExpect(jsonPath("$.error.code").value("OWN_POST"));

        // 글을 지우면 댓글·좋아요·태그 연결·신고도 함께 (CF-05-15)
        mvc.perform(delete("/api/posts/" + post).with(csrf()).session(a)).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT count(*) FROM comments").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM post_tags").query(Long.class).single()).isZero();
    }

    @Test
    void CF_22_imageUpload() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        var file = new org.springframework.mock.web.MockMultipartFile("file", "a.jpg", "image/jpeg", png);
        var up = read(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/api/images").file(file).with(csrf()).session(s)).andExpect(status().isCreated()).andReturn());
        String url = up.get("url").asText();
        assertThat(url).startsWith("/api/images/posts/").endsWith(".png");
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType("image/png"));
        var text = new org.springframework.mock.web.MockMultipartFile("file", "a.png", "image/png", "<svg>".getBytes());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/images")
                .file(text).with(csrf()).session(s)).andExpect(jsonPath("$.error.code").value("INVALID_IMAGE"));
        var big = new org.springframework.mock.web.MockMultipartFile("file", "big.png", "image/png",
                java.util.Arrays.copyOf(png, 5_242_881));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/images")
                .file(big).with(csrf()).session(s)).andExpect(jsonPath("$.error.code").value("INVALID_IMAGE"));

        long blog = blogIdOf(s);
        var p = postBody(defaultCategory(blog), "사진 글", "![](" + url + ")", "PUBLIC");
        p.put("imageIds", List.of(up.get("id").asLong()));
        p.put("coverImageId", up.get("id").asLong());
        long post = read(mvc.perform(postJson("/api/blogs/" + blog + "/posts", p).session(s)).andReturn())
                .get("id").asLong();
        mvc.perform(get("/api/posts/" + post)).andExpect(jsonPath("$.coverImageUrl").value(url));
        mvc.perform(delete("/api/posts/" + post).with(csrf()).session(s)).andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
