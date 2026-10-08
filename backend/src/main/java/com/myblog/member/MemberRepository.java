package com.myblog.member;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {
    private static final String COLUMNS =
            "id, email, nickname, password_hash, bio, failed_login_count, locked_until, created_at";
    private final JdbcClient jdbc;

    public MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Member> findById(long id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM members WHERE id = ?").param(id).query(Member.class).optional();
    }

    public Optional<Member> findByEmail(String email) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM members WHERE email = ?").param(email).query(Member.class).optional();
    }

    public boolean existsByEmail(String email) {
        return jdbc.sql("SELECT count(*) FROM members WHERE email = ?").param(email).query(Long.class).single() > 0;
    }

    public boolean existsByNicknameKey(String key, Long exceptId) {
        return jdbc.sql("SELECT count(*) FROM members WHERE nickname_key = ? AND id <> ?")
                .params(key, exceptId == null ? -1L : exceptId).query(Long.class).single() > 0;
    }

    public long insert(String email, String nickname, String nicknameKey, String passwordHash, OffsetDateTime now) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO members (email, nickname, nickname_key, password_hash, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)""")
                .params(email, nickname, nicknameKey, passwordHash, now, now)
                .update(keys, "id");
        return keys.getKey().longValue();
    }

    public void recordFailure(long id, int newCount, OffsetDateTime lockedUntil) {
        jdbc.sql("UPDATE members SET failed_login_count = ?, locked_until = ? WHERE id = ?")
                .params(newCount, lockedUntil, id).update();
    }

    public void clearFailures(long id) {
        jdbc.sql("UPDATE members SET failed_login_count = 0, locked_until = NULL WHERE id = ?").param(id).update();
    }

    public void updatePassword(long id, String hash, OffsetDateTime now) {
        jdbc.sql("UPDATE members SET password_hash = ?, failed_login_count = 0, locked_until = NULL, updated_at = ? WHERE id = ?")
                .params(hash, now, id).update();
    }

    public void updateProfile(long id, String nickname, String nicknameKey, String bio, OffsetDateTime now) {
        jdbc.sql("UPDATE members SET nickname = ?, nickname_key = ?, bio = ?, updated_at = ? WHERE id = ?")
                .params(nickname, nicknameKey, bio, now, id).update();
    }

    public void delete(long id) {
        jdbc.sql("DELETE FROM members WHERE id = ?").param(id).update();
    }
}
