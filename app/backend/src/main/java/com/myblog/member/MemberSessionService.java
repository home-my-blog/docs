package com.myblog.member;

import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

/** 회원 기준으로 로그인(세션)을 끊는다 (CF-15-15, CF-15-20, CF-25-8). */
@Service
public class MemberSessionService {
    private final SessionRegistry registry;

    public MemberSessionService(SessionRegistry registry) {
        this.registry = registry;
    }

    /** exceptSessionId가 null이면 모든 세션을 끊는다. */
    public void expireAll(long memberId, String exceptSessionId) {
        for (SessionInformation info : registry.getAllSessions(memberId, false)) {
            if (!info.getSessionId().equals(exceptSessionId)) {
                info.expireNow();
            }
        }
    }
}
