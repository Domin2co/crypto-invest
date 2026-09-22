package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PAPER 체결 전에 사용자별 주문 계획을 만들고 같은 멱등 키는 기존 계획을 재사용한다. */
@Repository
public class PaperOrderPlanRepository {
    private final JdbcTemplate jdbcTemplate;
    public PaperOrderPlanRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public UUID createOrFind(OrderPlan plan, BigDecimal sellQuantity) {
        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update("""
                INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_quantity, requested_amount, status, idempotency_key)
                VALUES (?, ?, ?, ?, ?, 'MARKET', ?, ?, 'ACCEPTED', ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, id, plan.userId(), plan.exchange().name(), plan.symbol(), plan.side(), sellQuantity, plan.amount(), plan.idempotencyKey());
        if (inserted == 1) return id;
        UUID existing = jdbcTemplate.query("SELECT id FROM order_plan WHERE idempotency_key = ?",
                rs -> rs.next() ? rs.getObject(1, UUID.class) : null, plan.idempotencyKey());
        if (existing == null) throw new IllegalStateException("Paper order plan is unavailable");
        return existing;
    }
}
