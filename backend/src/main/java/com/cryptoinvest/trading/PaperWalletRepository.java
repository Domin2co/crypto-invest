package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 모의투자 잔고를 DB에서 잠근 뒤 갱신한다. 통화별 행 잠금으로 음수 잔고를 막는다. */
@Repository
public class PaperWalletRepository {
    private final JdbcTemplate jdbcTemplate;

    public PaperWalletRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void initializeKrw(UUID userId, Exchange exchange, BigDecimal initialKrw) {
        jdbcTemplate.update("""
                INSERT INTO paper_wallet (id, user_id, exchange, currency, available_amount)
                VALUES (?, ?, ?, 'KRW', ?)
                ON CONFLICT (user_id, exchange, currency) DO NOTHING
                """, UUID.randomUUID(), userId, exchange.name(), initialKrw);
    }

    public BigDecimal balanceForUpdate(UUID userId, Exchange exchange, String currency) {
        return jdbcTemplate.query("""
                SELECT available_amount FROM paper_wallet
                WHERE user_id = ? AND exchange = ? AND currency = ? FOR UPDATE
                """, rs -> rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO, userId, exchange.name(), currency);
    }

    public void add(UUID userId, Exchange exchange, String currency, BigDecimal amount) {
        int changed = jdbcTemplate.update("""
                UPDATE paper_wallet SET available_amount = available_amount + ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND exchange = ? AND currency = ?
                """, amount, userId, exchange.name(), currency);
        if (changed == 0) jdbcTemplate.update("""
                INSERT INTO paper_wallet (id, user_id, exchange, currency, available_amount)
                VALUES (?, ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, exchange.name(), currency, amount);
    }

    public void subtract(UUID userId, Exchange exchange, String currency, BigDecimal amount) {
        int changed = jdbcTemplate.update("""
                UPDATE paper_wallet SET available_amount = available_amount - ?, updated_at = CURRENT_TIMESTAMP
                WHERE user_id = ? AND exchange = ? AND currency = ? AND available_amount >= ?
                """, amount, userId, exchange.name(), currency, amount);
        if (changed == 0) throw new IllegalStateException("Insufficient paper balance");
    }

    public List<WalletBalance> findByUserId(UUID userId) {
        return jdbcTemplate.query("SELECT exchange, currency, available_amount FROM paper_wallet WHERE user_id = ? ORDER BY exchange, currency",
                (rs, row) -> new WalletBalance(Exchange.valueOf(rs.getString(1)), rs.getString(2), rs.getBigDecimal(3)), userId);
    }

    public record WalletBalance(Exchange exchange, String currency, BigDecimal availableAmount) {}
}
