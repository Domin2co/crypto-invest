package com.cryptoinvest.security;

import java.util.List;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 동의 원문 대신 정책 버전과 동의·철회 시각만 보관한다. */
@Repository
public class UserConsentRepository {
    private final JdbcTemplate jdbcTemplate;
    public UserConsentRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void grant(UUID userId, String type, String policyVersion) {
        jdbcTemplate.update("""
                INSERT INTO user_consent (id, user_id, consent_type, policy_version)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (user_id, consent_type) DO UPDATE SET policy_version = EXCLUDED.policy_version,
                granted_at = CURRENT_TIMESTAMP, withdrawn_at = NULL
                """, UUID.randomUUID(), userId, type, policyVersion);
    }
    public void withdraw(UUID userId, String type) {
        jdbcTemplate.update("UPDATE user_consent SET withdrawn_at = CURRENT_TIMESTAMP WHERE user_id = ? AND consent_type = ?", userId, type);
    }
    public boolean isActive(UUID userId, String type) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("SELECT EXISTS (SELECT 1 FROM user_consent WHERE user_id = ? AND consent_type = ? AND withdrawn_at IS NULL)", Boolean.class, userId, type));
    }
    public boolean hasActive(UUID userId, String type, OffsetDateTime since) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM user_consent
                WHERE user_id = ? AND consent_type = ? AND withdrawn_at IS NULL AND granted_at >= ?)
                """, Boolean.class, userId, type, since));
    }
    public void recordLiveTradingConfirmation(UUID userId, String policyVersion) {
        grant(userId, "LIVE_TRADING", policyVersion);
        jdbcTemplate.update("INSERT INTO audit_log (id, user_id, event_type, details) VALUES (?, ?, 'LIVE_TRADING_CONFIRMED', '{}'::jsonb)",
                UUID.randomUUID(), userId);
    }
    public List<Consent> findByUserId(UUID userId) {
        return jdbcTemplate.query("SELECT consent_type, policy_version, granted_at, withdrawn_at FROM user_consent WHERE user_id = ? ORDER BY consent_type",
                (rs, row) -> new Consent(rs.getString(1), rs.getString(2), rs.getObject(3, java.time.OffsetDateTime.class), rs.getObject(4, java.time.OffsetDateTime.class)), userId);
    }
    public record Consent(String type, String policyVersion, java.time.OffsetDateTime grantedAt, java.time.OffsetDateTime withdrawnAt) {}
}
