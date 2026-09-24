package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 실주문은 먼저 제출 후보를 저장해 재시작 뒤에도 재전송하지 않게 한다. */
@Repository
public class LiveOrderRepository {
    private final JdbcTemplate jdbcTemplate;
    public LiveOrderRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<LiveOrder> findByUserAndIdempotencyKey(UUID userId, String idempotencyKey) {
        return jdbcTemplate.query("""
                SELECT id, exchange, client_order_id, exchange_order_id, status, executed_quantity, executed_amount, fee, completed_at
                FROM trade_order WHERE user_id = ? AND trading_mode = 'LIVE' AND idempotency_key = ?
                """, rs -> rs.next() ? Optional.of(row(rs)) : Optional.empty(), userId, idempotencyKey);
    }

    public List<LiveOrderHistory> findRecentByUserId(UUID userId) {
        return jdbcTemplate.query("""
                SELECT exchange, symbol, side, order_type, limit_price, requested_quantity, requested_amount,
                       executed_quantity, executed_amount, fee, status, created_at
                FROM trade_order WHERE user_id = ? AND trading_mode = 'LIVE'
                ORDER BY created_at DESC LIMIT 100
                """, (rs, rowNum) -> new LiveOrderHistory(rs.getString("exchange"), rs.getString("symbol"),
                rs.getString("side"), rs.getString("order_type"), rs.getBigDecimal("limit_price"),
                rs.getBigDecimal("requested_quantity"), rs.getBigDecimal("requested_amount"),
                rs.getBigDecimal("executed_quantity"), rs.getBigDecimal("executed_amount"), rs.getBigDecimal("fee"),
                rs.getString("status"), rs.getTimestamp("created_at").toInstant()), userId);
    }

    public BigDecimal submittedAmountToday(UUID userId) {
        BigDecimal result = jdbcTemplate.queryForObject("""
                SELECT COALESCE(SUM(requested_amount), 0) FROM trade_order
                WHERE user_id = ? AND trading_mode = 'LIVE' AND created_at >= CURRENT_DATE
                AND status IN ('SUBMITTED', 'PARTIALLY_FILLED', 'FILLED', 'UNKNOWN')
                """, BigDecimal.class, userId);
        return result == null ? BigDecimal.ZERO : result;
    }

    @Transactional
    public LiveOrder createSubmitted(UUID orderPlanId, OrderPlan plan, BigDecimal quantity, String clientOrderId) {
        return createSubmitted(orderPlanId, plan, quantity, clientOrderId, "MARKET", null);
    }

    @Transactional
    public LiveOrder createSubmitted(UUID orderPlanId, OrderPlan plan, BigDecimal quantity, String clientOrderId,
            String orderType, BigDecimal limitPrice) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO trade_order (id, user_id, order_plan_id, exchange, trading_mode, client_order_id, symbol, side,
                order_type, limit_price, requested_quantity, requested_amount, status, idempotency_key)
                VALUES (?, ?, ?, ?, 'LIVE', ?, ?, ?, ?, ?, ?, ?, 'SUBMITTED', ?)
                """, id, plan.userId(), orderPlanId, plan.exchange().name(), clientOrderId, plan.symbol(), plan.side(),
                orderType, limitPrice, quantity, plan.amount(), plan.idempotencyKey());
        jdbcTemplate.update("INSERT INTO audit_log (id, user_id, event_type, exchange, symbol, details) VALUES (?, ?, 'LIVE_ORDER_SUBMISSION_STARTED', ?, ?, '{}'::jsonb)",
                UUID.randomUUID(), plan.userId(), plan.exchange().name(), plan.symbol());
        return new LiveOrder(id, plan.exchange(), clientOrderId, null, "SUBMITTED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    @Transactional
    public LiveOrder update(LiveOrder previous, LiveOrder result) {
        boolean complete = result.terminal() || "FILLED".equals(result.status()) || "CANCELLED".equals(result.status()) || "FAILED".equals(result.status());
        jdbcTemplate.update("""
                UPDATE trade_order SET exchange_order_id = ?, status = ?, executed_quantity = ?, executed_amount = ?, fee = ?,
                completed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE completed_at END WHERE id = ?
                """, result.exchangeOrderId(), result.status(), result.executedQuantity(), result.executedAmount(), result.fee(), complete, previous.id());
        jdbcTemplate.update("""
                INSERT INTO audit_log (id, user_id, event_type, exchange, symbol, details)
                SELECT ?, user_id, 'LIVE_ORDER_STATUS_UPDATED', exchange, symbol, jsonb_build_object('status', ?)
                FROM trade_order WHERE id = ?
                """, UUID.randomUUID(), result.status(), previous.id());
        return new LiveOrder(previous.id(), previous.exchange(), previous.clientOrderId(), result.exchangeOrderId(), result.status(),
                result.executedQuantity(), result.executedAmount(), result.fee(), complete);
    }

    private static LiveOrder row(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new LiveOrder(rs.getObject("id", UUID.class), Exchange.valueOf(rs.getString("exchange")), rs.getString("client_order_id"),
                rs.getString("exchange_order_id"), rs.getString("status"), rs.getBigDecimal("executed_quantity"),
                rs.getBigDecimal("executed_amount"), rs.getBigDecimal("fee"), rs.getTimestamp("completed_at") != null);
    }
}