package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** PAPER 또는 LIVE 주문 계획을 만들고 같은 사용자 멱등 키는 기존 계획을 재사용한다. */
@Repository
public class PaperOrderPlanRepository {
    private final JdbcTemplate jdbcTemplate;
    public PaperOrderPlanRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Optional<UUID> createOrFind(OrderPlan plan, BigDecimal sellQuantity) { return createOrFind(plan, sellQuantity, "MARKET", null); }

    public Optional<UUID> createOrFind(OrderPlan plan, BigDecimal sellQuantity, String orderType) { return createOrFind(plan, sellQuantity, orderType, null); }

    public Optional<UUID> createOrFind(OrderPlan plan, BigDecimal sellQuantity, String orderType, BigDecimal limitPrice) {
        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update("""
                INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_quantity, requested_amount, limit_price, status, idempotency_key)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACCEPTED', ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """, id, plan.userId(), plan.exchange().name(), plan.symbol(), plan.side(), orderType, sellQuantity, plan.amount(), limitPrice, plan.idempotencyKey());
        if (inserted == 1) return Optional.of(id);
        List<UUID> existing = jdbcTemplate.query("SELECT id FROM order_plan WHERE user_id = ? AND idempotency_key = ?",
                (rs, row) -> rs.getObject(1, UUID.class), plan.userId(), plan.idempotencyKey());
        return existing.stream().findFirst();
    }
}
