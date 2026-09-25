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
    public Optional<String> nickname(UUID userId) {
        return jdbcTemplate.query("SELECT nickname FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(), userId);
    }
    public boolean hasNickname(UUID userId) { return nickname(userId).filter(value -> !value.isBlank()).isPresent(); }

    public String role(UUID userId) { return jdbcTemplate.query("SELECT role FROM app_user WHERE id=? AND enabled=TRUE", rs -> rs.next() ? rs.getString(1) : "USER", userId); }
    public boolean nicknameAvailable(String nickname, UUID userId) {
        return !Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM app_user WHERE LOWER(nickname) = LOWER(?) AND id <> ?)", Boolean.class, nickname, userId));
    }
    public void setNickname(UUID userId, String nickname) {
        if (jdbcTemplate.update("UPDATE app_user SET nickname = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND enabled = TRUE", nickname, userId) != 1) {
            throw new IllegalStateException("Account is unavailable");
        }
    }
    public UUID create(String email, String passwordHash) {
        UUID id = UUID.randomUUID(); jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash, email_verified, email_changed_at) VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)", id, email, passwordHash); return id;
    }
    public Optional<UserCredentials> findEnabledById(UUID userId) {
        return jdbcTemplate.query("SELECT id, password_hash FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? Optional.of(new UserCredentials(UUID.fromString(rs.getString("id")), rs.getString("password_hash"))) : Optional.empty(), userId);
    }
    public void updatePassword(UUID userId, String passwordHash) { jdbcTemplate.update("UPDATE app_user SET password_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND enabled = TRUE", passwordHash, userId); }
    public int authTokenVersion(UUID userId) {
        Integer version = jdbcTemplate.query("SELECT auth_token_version FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? rs.getInt(1) : null, userId);
        if (version == null) throw new IllegalArgumentException("Account is unavailable");
        return version;
    }
    public void revokeAuthTokens(UUID userId) { jdbcTemplate.update("UPDATE app_user SET auth_token_version = auth_token_version + 1 WHERE id = ? AND enabled = TRUE", userId); }

    public boolean emailAvailable(String email) { return !Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM app_user WHERE LOWER(email) = LOWER(?) AND enabled = TRUE)", Boolean.class, email)); }
    public EmailStatus emailStatus(UUID userId) {
        return jdbcTemplate.query("SELECT email, email_verified, email_changed_at + INTERVAL '90 days' FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? new EmailStatus(rs.getString(1), rs.getBoolean(2), rs.getTimestamp(3).toInstant()) : null, userId);
    }
    public java.time.Instant emailChangeAvailableAt(UUID userId) {
        return jdbcTemplate.query("SELECT email_changed_at + INTERVAL '90 days' FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? rs.getTimestamp(1).toInstant() : null, userId);
    }
    public void updateEmail(UUID userId, String email) {
        if (jdbcTemplate.update("UPDATE app_user SET email = ?, email_verified = TRUE, email_changed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP WHERE id = ? AND enabled = TRUE", email, userId) != 1) throw new IllegalStateException("Account is unavailable");
    }
    public Optional<UserCredentials> findEnabledByEmail(String email) {
        return jdbcTemplate.query("SELECT id, password_hash FROM app_user WHERE email = ? AND enabled = TRUE", rs -> rs.next()
                ? Optional.of(new UserCredentials(UUID.fromString(rs.getString("id")), rs.getString("password_hash"))) : Optional.empty(), email);
    }
    public Optional<UUID> findEnabledIdByEmail(String email) {
        return jdbcTemplate.query("SELECT id FROM app_user WHERE LOWER(email) = LOWER(?) AND enabled = TRUE", rs -> rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty(), email);
    }
    public Optional<String> findEnabledEmail(UUID userId) {
        return jdbcTemplate.query("SELECT email FROM app_user WHERE id = ? AND enabled = TRUE", rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), userId);
    }
    /** 계정 삭제 요청은 로그인·API key 접근을 차단하고 직접 식별 정보를 익명화한다. */
    public void anonymizeAndDisable(UUID userId) {
        jdbcTemplate.update("DELETE FROM portfolio_target WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM exchange_account WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_totp_mfa WHERE user_id = ?", userId);
        jdbcTemplate.update("UPDATE app_user SET email = ?, password_hash = 'DELETED', enabled = FALSE, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                "deleted-" + userId + "@deleted.invalid", userId);
    }
    public record UserCredentials(UUID id, String passwordHash) {}
    public record EmailStatus(String email, boolean verified, java.time.Instant changeAvailableAt) {}
}
