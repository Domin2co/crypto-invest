package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Paper 체결 이력과 비밀정보가 제거된 감사 이벤트를 같은 transaction 경계에서 기록한다. */
@Repository
public class PaperOrderAuditRepository {
    private final JdbcTemplate jdbcTemplate;
    public PaperOrderAuditRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }
    @Transactional
    public boolean save(UUID userId, UUID planId, Exchange exchange, PaperTradingService.PaperFill fill, BigDecimal price) { return save(userId, planId, exchange, fill, price, "MARKET", null); }

    @Transactional
    public boolean save(UUID userId, UUID planId, Exchange exchange, PaperTradingService.PaperFill fill, BigDecimal price, String orderType) { return save(userId, planId, exchange, fill, price, orderType, null); }

    @Transactional
    public boolean save(UUID userId, UUID planId, Exchange exchange, PaperTradingService.PaperFill fill, BigDecimal price, String orderType, BigDecimal limitPrice) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO trade_order (id, user_id, order_plan_id, exchange, trading_mode, symbol, side, order_type,
                requested_quantity, requested_amount, limit_price, executed_quantity, executed_amount, executed_price, fee, status, idempotency_key, completed_at)
                VALUES (?, ?, ?, ?, 'PAPER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, UUID.randomUUID(), userId, planId, exchange.name(), fill.symbol(), fill.side(), orderType, fill.quantity(), fill.amount(), limitPrice, fill.quantity(), fill.amount(), price, fill.fee(), fill.status(), fill.idempotencyKey());
        if (inserted == 0) return false;
        jdbcTemplate.update("INSERT INTO audit_log (id, user_id, event_type, exchange, symbol, details) VALUES (?, ?, 'PAPER_ORDER_FILLED', ?, ?, '{}'::jsonb)",
                UUID.randomUUID(), userId, exchange.name(), fill.symbol());
        return true;
    }

    public Optional<PaperTradingService.PaperFill> findByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT symbol, side, executed_quantity, executed_amount, fee, status, idempotency_key
                FROM trade_order WHERE user_id = ? AND trading_mode = 'PAPER' AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(new PaperTradingService.PaperFill(
                rs.getString("symbol"), rs.getString("side"), rs.getBigDecimal("executed_quantity"),
                rs.getBigDecimal("executed_amount"), rs.getBigDecimal("fee"), rs.getString("status"),
                rs.getString("idempotency_key"))) : Optional.empty(), userId, idempotencyKey);
    }

    public List<PaperOrder> findByUserId(UUID userId) {
        return jdbcTemplate.query("""
                SELECT exchange, symbol, side, order_type, limit_price, executed_quantity, executed_amount, executed_price, fee, status, created_at
                FROM trade_order WHERE user_id = ? AND trading_mode = 'PAPER' ORDER BY created_at DESC LIMIT 20
                """, (rs, row) -> new PaperOrder(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getString(10), rs.getObject(11, java.time.OffsetDateTime.class)), userId);
    }

    public record PaperOrder(String exchange, String symbol, String side, String orderType, BigDecimal limitPrice, BigDecimal quantity, BigDecimal amount, BigDecimal price, BigDecimal fee,
            String status, java.time.OffsetDateTime createdAt) {}
}
