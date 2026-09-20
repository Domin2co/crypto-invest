package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 모의투자 잔고를 DB에서 잠근 뒤 갱신한다. 통화별 행 잠금으로 음수 잔고를 막는다. */
@Repository
public class PaperWalletRepository {
    private final JdbcTemplate jdbcTemplate;

    public PaperWalletRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void initializeKrw(UUID userId, BigDecimal initialKrw) {
        jdbcTemplate.update("""
                INSERT INTO paper_wallet (id, user_id, currency, available_amount)
                VALUES (?, ?, 'KRW', ?)
                ON CONFLICT (user_id, currency) DO NOTHING
                """, UUID.randomUUID(), userId, initialKrw);
    }

    public BigDecimal balanceForUpdate(UUID userId, String currency) {
        return jdbcTemplate.query("""
                SELECT available_amount FROM paper_wallet
                WHERE user_id = ? AND currency = ? FOR UPDATE
                """, rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, userId, currency);
    }

    public void add(UUID userId, String currency, BigDecimal amount) {
        int changed = jdbcTemplate.update("""
                UPDATE paper_wallet SET available_amount = available_amount + ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND currency = ?
                """, amount, userId, currency);
        if (changed == 0) jdbcTemplate.update("""
                INSERT INTO paper_wallet (id, user_id, currency, available_amount)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), userId, currency, amount);
    }

    public void subtract(UUID userId, String currency, BigDecimal amount) {
        int changed = jdbcTemplate.update("""
                UPDATE paper_wallet SET available_amount = available_amount - ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND currency = ? AND available_amount >= ?
                """, amount, userId, currency, amount);
        if (changed == 0) throw new IllegalStateException("Insufficient paper balance");
    }
}
