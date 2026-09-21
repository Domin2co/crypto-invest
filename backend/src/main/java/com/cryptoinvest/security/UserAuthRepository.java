package com.cryptoinvest.security;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 로그인에 필요한 최소 사용자 정보만 조회·저장한다. */
@Repository
public class UserAuthRepository {
    private final JdbcTemplate jdbcTemplate;
    public UserAuthRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    public boolean existsByEmail(String email) { return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM app_user WHERE email = ?)", Boolean.class, email)); }
    public UUID create(String email, String passwordHash) {
        UUID id = UUID.randomUUID(); jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, ?)", id, email, passwordHash); return id;
    }
    public Optional<UserCredentials> findEnabledByEmail(String email) {
        return jdbcTemplate.query("SELECT id, password_hash FROM app_user WHERE email = ? AND enabled = TRUE", rs -> rs.next()
                ? Optional.of(new UserCredentials(UUID.fromString(rs.getString("id")), rs.getString("password_hash"))) : Optional.empty(), email);
    }
    public Optional<String> findEnabledEmail(UUID userId) {
        return jdbcTemplate.query("SELECT email FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), userId);
    }
    /** 계정 삭제 요청은 로그인·API key 접근을 차단하고 직접 식별 정보를 익명화한다. */
    public void anonymizeAndDisable(UUID userId) {
        jdbcTemplate.update("DELETE FROM exchange_account WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE app_user SET email = ?, password_hash = 'DELETED', enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "deleted-" + userId + "@deleted.invalid", userId);
    }
    public record UserCredentials(UUID id, String passwordHash) {}
}
