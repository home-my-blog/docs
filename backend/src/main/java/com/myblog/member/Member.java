package com.myblog.member;

import java.time.OffsetDateTime;

public record Member(long id, String email, String nickname, String passwordHash, String bio,
                     int failedLoginCount, OffsetDateTime lockedUntil, OffsetDateTime createdAt) {}
