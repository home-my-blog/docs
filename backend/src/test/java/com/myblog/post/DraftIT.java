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

class DraftIT extends IntegrationTest {

    private Map<String, Object> draft(String title, String body) {
        var m = new HashMap<String, Object>();
        m.put("title", title);
        m.put("body", body);
        return m;
    }

    @Test
    void CF_05_draftSaveResumeAndList() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long cat = defaultCategory(blogIdOf(a));

        mvc.perform(postJson("/api/me/drafts", draft("", "  "))).andExpect(status().isUnauthorized());
        mvc.perform(postJson("/api/me/drafts", draft(" ", " \n ")).session(a))
                .andExpect(jsonPath("$.error.fields.body").value("제목이나 본문을 입력하면 임시저장할 수 있습니다"));

        // 제목만 있어도 저장, 덮어쓰기, 분류·공개 여부·태그도 함께
        long id = read(mvc.perform(postJson("/api/me/drafts", draft("쓰다 만 글", "")).session(a))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        var more = draft("쓰다 만 글", "본문 조금");
        more.put("categoryId", cat);
        more.put("visibility", "PRIVATE");
        more.put("tags", List.of("#여행", "제주"));
        mvc.perform(jsonRequest(put("/api/me/drafts/" + id), more).session(a)).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/me/drafts/" + id).session(a))
                .andExpect(jsonPath("$.body").value("본문 조금"))
                .andExpect(jsonPath("$.categoryId").value(cat))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"))
                .andExpect(jsonPath("$.tags[0]").value("여행"));
        mvc.perform(get("/api/me/drafts").session(a))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("쓰다 만 글"))
                .andExpect(jsonPath("$.limit").value(20));

        // 남의 임시저장 글은 없는 것과 같다. 남의 분류는 비운다
        mvc.perform(get("/api/me/drafts/" + id).session(b)).andExpect(status().isNotFound());
        mvc.perform(jsonRequest(put("/api/me/drafts/" + id), draft("가로채기", "x")).session(b))
                .andExpect(jsonPath("$.error.code").value("DRAFT_NOT_FOUND"));
        mvc.perform(delete("/api/me/drafts/" + id).with(csrf()).session(b)).andExpect(status().isNotFound());
        var other = draft("비이 글", "본문");
        other.put("categoryId", cat);
        long bDraft = read(mvc.perform(postJson("/api/me/drafts", other).session(b)).andReturn()).get("id").asLong();
        mvc.perform(get("/api/me/drafts/" + bDraft).session(b)).andExpect(jsonPath("$.categoryId").doesNotExist());

        // 임시저장 글로 글을 올리면 그 임시저장 글은 지워진다
        var post = new HashMap<String, Object>();
        post.put("title", "쓰다 만 글");
        post.put("body", "본문 완성");
        post.put("categoryId", cat);
        post.put("draftId", id);
        mvc.perform(postJson("/api/blogs/" + blogIdOf(a) + "/posts", post).session(a)).andExpect(status().isCreated());
        mvc.perform(get("/api/me/drafts").session(a)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void CF_05_draftLimitAndDelete() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        long first = 0;
        for (int i = 0; i < 20; i++) {
            long id = read(mvc.perform(postJson("/api/me/drafts", draft("임시 " + i, "")).session(a))
                    .andExpect(status().isCreated()).andReturn()).get("id").asLong();
            if (i == 0) {
                first = id;
            }
        }
        mvc.perform(postJson("/api/me/drafts", draft("21번째", "")).session(a))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DRAFT_LIMIT"));
        mvc.perform(delete("/api/me/drafts/" + first).with(csrf()).session(a)).andExpect(status().isNoContent());
        mvc.perform(postJson("/api/me/drafts", draft("21번째", "")).session(a)).andExpect(status().isCreated());
        assertThat(jdbc.sql("SELECT count(*) FROM drafts").query(Long.class).single()).isEqualTo(20);
    }
}
