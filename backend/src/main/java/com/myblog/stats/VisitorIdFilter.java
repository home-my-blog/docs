package com.myblog.stats;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 비로그인 방문자 구분값 MYBLOG_VID 쿠키(1년)를 준다 (research §9). */
@Component
public class VisitorIdFilter extends OncePerRequestFilter {
    public static final String COOKIE = "MYBLOG_VID";
    public static final String ATTRIBUTE = "myblog.vid";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String vid = req.getCookies() == null ? null : Arrays.stream(req.getCookies())
                .filter(c -> COOKIE.equals(c.getName())).map(Cookie::getValue)
                .filter(v -> v.matches("[0-9a-f-]{36}")).findFirst().orElse(null);
        if (vid == null) {
            vid = UUID.randomUUID().toString();
            res.addHeader("Set-Cookie", ResponseCookie.from(COOKIE, vid).path("/").httpOnly(true).sameSite("Lax")
                    .maxAge(Duration.ofDays(365)).build().toString());
        }
        req.setAttribute(ATTRIBUTE, vid);
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/posts/");
    }
}
