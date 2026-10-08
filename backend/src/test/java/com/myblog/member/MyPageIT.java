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
import org.springframework.beans.factory.annotation.Autowired;
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
        assertThat(jdbc.sql("SELECT failed_login_count FROM members").query(Integer.class).single()).isEqualTo(1);
        mvc.perform(jsonRequest(put("/api/me/password"), Map.of("currentPassword", PASSWORD, "newPassword", newPw,
                "newPasswordConfirm", newPw)).session(laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(laptop)).andExpect(status().isOk());
        mvc.perform(get("/api/me").session(phone)).andExpect(status().isUnauthorized());
    }

    @Autowired
    WithdrawalCleaner cleaner;

    @Test
    void CF_15_17_21_withdrawIsSoftAndHidesContent() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long blogA = blogIdOf(a);
        long postB = writePost(b, "비이의 글", "본문", "PUBLIC");
        long postA = writePost(a, "에이의 글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/posts/" + postB + "/comments", Map.of("body", "남의 글 댓글")).session(a))
                .andExpect(status().isCreated());

        mvc.perform(jsonRequest(delete("/api/me"), Map.of("password", PASSWORD, "acknowledged", false)).session(a))
                .andExpect(jsonPath("$.error.code").value("ACKNOWLEDGE_REQUIRED"));
        mvc.perform(jsonRequest(delete("/api/me"), Map.of("password", PASSWORD, "acknowledged", true)).session(a))
                .andExpect(status().isNoContent());

        // 바로 로그아웃, 데이터는 남아 있지만 블로그·글은 아무에게도 안 보이고 댓글은 "탈퇴한 사용자"
        mvc.perform(get("/api/me").session(a)).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT count(*) FROM posts").query(Long.class).single()).isEqualTo(2);
        mvc.perform(get("/api/blogs/" + blogA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/" + postA)).andExpect(status().isNotFound());
        mvc.perform(get("/api/home")).andExpect(jsonPath("$.latestPosts.length()").value(1));
        mvc.perform(get("/api/posts/" + postB + "/comments"))
                .andExpect(jsonPath("$[0].author").doesNotExist())
                .andExpect(jsonPath("$[0].body").value("남의 글 댓글"));

        // 보관 기간에는 같은 이메일로 가입할 수 없고, 비밀번호 찾기 메일도 가지 않는다
        mvc.perform(postJson("/api/auth/verifications", Map.of("purpose", "signup", "email", "a@example.com",
                "nickname", "새사람"))).andExpect(jsonPath("$.error.code").value("EMAIL_WITHDRAWN"));
        mail.sent.clear();
        mvc.perform(postJson("/api/auth/verifications", Map.of("purpose", "reset", "email", "a@example.com")))
                .andExpect(status().isAccepted());
        assertThat(mail.sent).isEmpty();

        // 로그인하면 복구할지 묻고(ACCOUNT_WITHDRAWN), 복구하면 다시 보인다
        mvc.perform(postJson("/api/auth/login", Map.of("email", "a@example.com", "password", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_WITHDRAWN"))
                .andExpect(jsonPath("$.error.restorableUntil").exists());
        mvc.perform(postJson("/api/auth/restore", Map.of("email", "a@example.com", "password", "wrong1!a")))
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
        MockHttpSession again = new MockHttpSession();
        mvc.perform(postJson("/api/auth/restore", Map.of("email", "a@example.com", "password", PASSWORD)).session(again))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me").session(again)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postA)).andExpect(status().isOk());
        mvc.perform(get("/api/posts/" + postB + "/comments")).andExpect(jsonPath("$[0].author.nickname").value("에이"));
        // 탈퇴하지 않은 회원은 복구할 것이 없다
        mvc.perform(postJson("/api/auth/restore", Map.of("email", "b@example.com", "password", PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));
    }

    @Test
    void CF_15_withdrawnMemberIsAnonymizedAfterKeepPeriod() throws Exception {
        MockHttpSession a = member("에이", "a@example.com");
        MockHttpSession b = member("비이", "b@example.com");
        long postB = writePost(b, "비이의 글", "본문", "PUBLIC");
        writePost(a, "에이의 글", "본문", "PUBLIC");
        mvc.perform(postJson("/api/posts/" + postB + "/comments", Map.of("body", "남의 글 댓글")).session(a));
        mvc.perform(jsonRequest(put("/api/posts/" + postB + "/like"), Map.of()).session(a)).andExpect(status().isOk());
        mvc.perform(jsonRequest(delete("/api/me"), Map.of("password", PASSWORD, "acknowledged", true)).session(a))
                .andExpect(status().isNoContent());

        assertThat(cleaner.cleanUp()).isZero(); // 아직 보관 기간
        jdbc.sql("UPDATE members SET deleted_at = deleted_at - interval '31 days' WHERE email = 'a@example.com'").update();
        assertThat(cleaner.cleanUp()).isEqualTo(1);

        // 블로그·글·좋아요는 지우고, 회원 줄은 개인정보만 지운 채 남긴다. 남의 글에 단 댓글은 남는다
        assertThat(jdbc.sql("SELECT count(*) FROM blogs").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM posts").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT count(*) FROM post_likes").query(Long.class).single()).isZero();
        assertThat(jdbc.sql("SELECT count(*) FROM members").query(Long.class).single()).isEqualTo(2);
        var anon = jdbc.sql("SELECT email, nickname, bio, anonymized_at IS NOT NULL AS done FROM members WHERE email <> 'b@example.com'")
                .query().singleRow();
        assertThat((String) anon.get("email")).startsWith("del_").endsWith("@deleted.invalid");
        assertThat((String) anon.get("nickname")).startsWith("del").isNotEqualTo("에이");
        assertThat(anon.get("bio")).isEqualTo("");
        assertThat(anon.get("done")).isEqualTo(true);
        mvc.perform(get("/api/posts/" + postB + "/comments"))
                .andExpect(jsonPath("$[0].author").doesNotExist())
                .andExpect(jsonPath("$[0].body").value("남의 글 댓글"));
        mvc.perform(postJson("/api/auth/login", Map.of("email", "a@example.com", "password", PASSWORD)))
                .andExpect(jsonPath("$.error.code").value("LOGIN_FAILED"));

        // 이제 같은 이메일·닉네임으로 다시 가입할 수 있다
        redis.delete(redis.keys("*"));
        signup("에이", "a@example.com");
    }
}
