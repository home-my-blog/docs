package com.myblog.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myblog.mail.MailSender;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 실제 PostgreSQL 16과 Redis에 붙는 통합 테스트 공용 베이스.
 * 접속 주소는 application-test.yml (TEST_DB_URL, REDIS_HOST). 테스트마다 표와 Redis를 비운다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(IntegrationTest.MailConfig.class)
public abstract class IntegrationTest {

    @TestConfiguration
    static class MailConfig {
        @Bean
        @Primary
        CapturingMailSender capturingMailSender() {
            return new CapturingMailSender();
        }
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper json;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected StringRedisTemplate redis;
    @Autowired
    protected CapturingMailSender mail;
    @Autowired
    protected MailSender mailSender;

    protected static final String PASSWORD = "abc123!@";

    @BeforeEach
    void cleanUp() {
        jdbc.sql("""
                TRUNCATE search_logs, daily_stats, comments, post_flags, post_likes, post_tags, tags, posts, post_images,
                         categories, blogs, members RESTART IDENTITY CASCADE""").update();
        var keys = redis.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        mail.sent.clear();
        mail.available = true;
    }

    protected String body(Object o) {
        try {
            return json.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected JsonNode read(MvcResult r) throws Exception {
        String s = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return s.isEmpty() ? json.nullNode() : json.readTree(s);
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object payload) {
        return post(url).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    protected MockHttpServletRequestBuilder jsonRequest(MockHttpServletRequestBuilder b, Object payload) {
        return b.with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body(payload));
    }

    /** 인증번호 받기 → 확인까지. */
    protected void verify(String purpose, String email, String nickname) throws Exception {
        var req = new java.util.HashMap<String, Object>();
        req.put("purpose", purpose);
        req.put("email", email);
        if (nickname != null) {
            req.put("nickname", nickname);
        }
        mvc.perform(postJson("/api/auth/verifications", req)).andExpect(status().isAccepted());
        mvc.perform(postJson("/api/auth/verifications/confirm",
                java.util.Map.of("purpose", purpose, "email", email, "code", mail.lastCode(email.strip().toLowerCase()))))
                .andExpect(status().isOk());
    }

    protected long signup(String nickname, String email) throws Exception {
        verify("signup", email, nickname);
        var r = mvc.perform(postJson("/api/auth/signup", java.util.Map.of("nickname", nickname, "email", email,
                "password", PASSWORD, "passwordConfirm", PASSWORD))).andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(postJson("/api/auth/login", java.util.Map.of("email", email, "password", password))
                .session(session)).andExpect(status().isOk());
        return session;
    }

    /** 가입하고 로그인한 세션. */
    protected MockHttpSession member(String nickname, String email) throws Exception {
        signup(nickname, email);
        return login(email, PASSWORD);
    }

    protected long blogIdOf(MockHttpSession s) throws Exception {
        return read(mvc.perform(get("/api/auth/me").session(s)).andReturn()).get("blog").get("id").asLong();
    }

    protected long defaultCategory(long blogId) throws Exception {
        var list = read(mvc.perform(get("/api/blogs/" + blogId + "/categories")).andReturn());
        return list.get(0).get("id").asLong();
    }

    protected long writePost(MockHttpSession s, String title, String bodyText, String visibility) throws Exception {
        long blogId = blogIdOf(s);
        var payload = new java.util.HashMap<String, Object>();
        payload.put("title", title);
        payload.put("body", bodyText);
        payload.put("categoryId", defaultCategory(blogId));
        payload.put("visibility", visibility);
        var r = mvc.perform(postJson("/api/blogs/" + blogId + "/posts", payload).session(s))
                .andExpect(status().isCreated()).andReturn();
        return read(r).get("id").asLong();
    }
}
