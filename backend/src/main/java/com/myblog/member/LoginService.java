package com.myblog.member;

import com.myblog.common.KoreanClock;
import com.myblog.common.MyBlogProperties;
import com.myblog.common.error.ApiException;
import com.myblog.common.error.ErrorCode;
import com.myblog.common.error.Messages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 로그인과 잠금 (CF-02-3~9, NF-12). */
@Service
public class LoginService {
    private final MemberRepository members;
    private final PasswordEncoder encoder;
    private final MyBlogProperties.Login rules;
    private final KoreanClock clock;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contextRepository;
    // 없는 이메일에도 같은 시간을 쓰게 해 응답 시간으로 가입 여부를 짐작하지 못하게 한다
    private final String dummyHash;

    public LoginService(MemberRepository members, PasswordEncoder encoder, MyBlogProperties props, KoreanClock clock,
                        SessionAuthenticationStrategy sessionStrategy, SecurityContextRepository contextRepository) {
        this.members = members;
        this.encoder = encoder;
        this.rules = props.login();
        this.clock = clock;
        this.sessionStrategy = sessionStrategy;
        this.contextRepository = contextRepository;
        this.dummyHash = encoder.encode("dummy-password-1!");
    }

    public Member login(String rawEmail, String password, HttpServletRequest req, HttpServletResponse res) {
        String email = rawEmail == null ? "" : rawEmail.strip().toLowerCase(java.util.Locale.ROOT);
        var found = members.findByEmail(email);
        if (found.isEmpty()) {
            encoder.matches(password == null ? "" : password, dummyHash);
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        Member m = found.get();
        verifyPassword(m, password, ErrorCode.LOGIN_FAILED);
        members.clearFailures(m.id());

        var auth = new UsernamePasswordAuthenticationToken(m.id(), null,
                List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));
        req.getSession(true);
        sessionStrategy.onAuthentication(auth, req, res); // 세션 ID 새로 발급 + 회원별 세션 목록에 등록
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, req, res);
        return m;
    }

    /**
     * 비밀번호를 확인한다. 잠겨 있으면 맞아도 거절하고, 틀리면 실패 횟수를 올린다.
     * 마이페이지의 현재 비밀번호 확인도 이 횟수에 합산한다 (CF-15-14).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, noRollbackFor = ApiException.class)
    public void verifyPassword(Member m, String password, ErrorCode wrongPasswordCode) {
        OffsetDateTime now = clock.nowOffset();
        if (m.lockedUntil() != null && m.lockedUntil().isAfter(now)) {
            throw locked(m.lockedUntil(), now);
        }
        int failures = m.lockedUntil() != null ? 0 : m.failedLoginCount(); // 잠금이 풀렸으면 0부터 (CF-02-7)
        if (password == null || !encoder.matches(password, m.passwordHash())) {
            failures++;
            if (failures >= rules.maxFailures()) {
                OffsetDateTime until = now.plus(rules.lockDuration());
                members.recordFailure(m.id(), 0, until);
                throw locked(until, now);
            }
            members.recordFailure(m.id(), failures, null);
            throw new ApiException(wrongPasswordCode);
        }
    }

    private static ApiException locked(OffsetDateTime until, OffsetDateTime now) {
        long minutes = Math.max(1, (Duration.between(now, until).toSeconds() + 59) / 60);
        return ApiException.withDetails(ErrorCode.ACCOUNT_LOCKED, Messages.accountLocked(minutes),
                Map.of("unlockAt", until.atZoneSameInstant(com.myblog.common.KoreanClock.SEOUL).toOffsetDateTime()));
    }
}
