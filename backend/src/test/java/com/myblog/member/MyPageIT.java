package com.myblog.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.myblog.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class MyPageIT extends IntegrationTest {

    @Test
    void CF_15_2_requiresLogin() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHENTICATED"));
    }

    @Test
    void CF_15_5_profileUpdate() throws Exception {
        MockHttpSession s = member("수연", "su@example.com");
        member("다른이", "other@example.com");
        mvc.perform(jsonRequest(patch("/api/me"), Map.of("nickname", "수연", "bio", "안녕하세요")).session(s))
                .andExpect(status().isOk());
        mvc.perform(jsonRequest(patch("/api/me"), Map.of("nickname", "다른이")).session(s))
                .andExpect(jsonPath("$.error.code").value("NICKNAME_TAKEN"));
        mvc.perform(get("/api/me").session(s)).andExpect(jsonPath("$.bio").value("안녕하세요"))
                .andExpect(jsonPath("$.email").value("su@example.com"));
    }

    @Test
    void CF_15_14_15_passwordChangeKeepsCurrentDeviceOnly() throws Exception {
        signup("수연", "su@example.com");
        MockHttpSession phone = login("su@example.com", PASSWORD);
        MockHttpSession laptop = login("su@example.com", PASSWORD);
        String newPw = "new123!@";
        mvc.perform(jsonRequest(put("/api/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD,
                "newPasswordConfirm", PASSWORD)).session(laptop)).andExpect(jsonPath("$.error.code").value("SAME_AS_CURRENT"));
        mvc.perform(jsonRequest(put("/api/me/password"), Map.of("currentPassword", "wrong1!a", "newPassword", newPw,
                "newPasswordConfirm", newPw)).session(laptop))
                .andExpect(jsonPath("$.error.code").value("CURRENT_PASSWORD_MISMATCH"));
        assertThat(jdbc.sql("SELECT failed_login_count FROM member").query(Integer.class).single()).isEqualTo(1);
        mvc.perform(jsonRequest(put("/api/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", newPw,
                "newPasswordConfirm", newPw)).session(laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(phone)).andExpect(status().isUnauthorized());
    }

    @Test
    void CF_15_17_21_withdraw() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long postB = writePost(b, "비이의 글", "본문", "PUBLIC");
        writePost(a, "에이의 글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/posts/" + postB + "/comments", Map.of("body", "남의 글 댓글")).session(a))
                .andExpect(status().isCreated());
        mvc.perform(jsonRequest(put("/api/posts/" + postB + "/like"), Map.of()).session(a)).andExpect(status().isOk());

        mvc.perform(jsonRequest(delete("/api/me"), Map.of("password", PASSWORD, "acknowledged", false)).session(a))
                .andExpect(jsonPath("$.error.code").value("ACKNOWLEDGE_REQUIRED"));
        mvc.perform(jsonRequest(delete("/api/me"), Map.of("password", PASSWORD, "acknowledged", true)).session(a))
                .andExpect(status().isNoContent());

        assertThat(jdbc.sql("SELECT count(*) FROM blog").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM post").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM post_like").query(Long.class).single()).isZero();
        mvc.perform(get("/api/posts/" + postB + "/comments"))
                .andExpect(jsonPath("$[0].author").doesNotExist())
                .andExpect(jsonPath("$[0].body").value("남의 글 댓글"));
        mvc.perform(get("/api/me").session(a)).andExpect(status().isUnauthorized());
        // 같은 이메일·닉네임으로 다시 가입할 수 있다
        redis.delete(redis.keys("*"));
        signup("에이", "a@example.com");
    }
}
