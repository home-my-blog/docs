package com.myblog.member;

import java.time.OffsetDateTime;

public record Member(long id, String email, String nickname, String passwordHash, String bio,
                     int failedLoginCount, OffsetDateTime lockedUntil, OffsetDateTime createdAt,
                     OffsetDateTime deletedAt) {
    /** 탈퇴 신청 후 보관 기간 안 (아직 익명화 전). 로그인하면 복구할 수 있다. */
    public boolean withdrawn() {
        return deletedAt != null;
    }
}
