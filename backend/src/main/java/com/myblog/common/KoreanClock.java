package com.myblog.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

/** 날짜는 한국 시간 자정에 바뀐다 (BM-06-6). 시각은 UTC로 저장한다. */
@Component
public class KoreanClock {
    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock;

    public KoreanClock() {
        this(Clock.systemUTC());
    }

    public KoreanClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public OffsetDateTime nowOffset() {
        return OffsetDateTime.ofInstant(now(), SEOUL);
    }

    public LocalDate today() {
        return LocalDate.ofInstant(now(), SEOUL);
    }

    public Duration untilMidnight() {
        ZonedDateTime next = today().plusDays(1).atStartOfDay(SEOUL);
        return Duration.between(now(), next.toInstant());
    }
}
