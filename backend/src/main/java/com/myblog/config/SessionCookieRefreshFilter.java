package com.myblog.config;

import com.myblog.common.MyBlogProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 쓸 때마다 로그인 쿠키 만료를 7일 뒤로 늘린다 (CF-02-10 "계속 쓰면 기간이 연장"). */
@Component
public class SessionCookieRefreshFilter extends OncePerRequestFilter {
    private final MyBlogProperties props;

    public SessionCookieRefreshFilter(MyBlogProperties props) {
        this.props = props;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(req, res);
        HttpSession session = req.getSession(false);
        if (session != null && !res.isCommitted() && req.isRequestedSessionIdValid()
                && session.getId().equals(req.getRequestedSessionId())) {
            res.addHeader("Set-Cookie", ResponseCookie.from(props.session().cookieName(), session.getId())
                    .path("/").httpOnly(true).secure(props.session().secureCookie()).sameSite("Lax")
                    .maxAge(props.session().timeout()).build().toString());
        }
    }
}
