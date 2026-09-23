package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** 실제 PostgreSQL에서 거래소 호출 전에 LIVE 주문 의도와 감사 이력이 커밋되는지 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "app.auth-token-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
class LiveOrderRepositoryIT {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired LiveOrderRepository orders;

    @Test
    void commitsSubmittedIntentAndPersistsTerminalPartialFill() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String key = "live-intent-" + UUID.randomUUID();
        String clientOrderId = UUID.randomUUID().toString();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, ?)",
                userId, key + "@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_amount, status, idempotency_key)
                VALUES (?, ?, 'UPBIT', 'KRW-BTC', 'BUY', 'MARKET', 10000, 'ACCEPTED', ?)
                """, planId, userId, key);

        try {
            OrderPlan plan = new OrderPlan(userId, Exchange.UPBIT, "KRW-BTC", "BUY", new BigDecimal("10000"),
                    new BigDecimal("0.1"), key);
            LiveOrder submitted = orders.createSubmitted(planId, plan, new BigDecimal("0.1"), clientOrderId);

            assertThat(submitted.status()).isEqualTo("SUBMITTED");
            assertThat(jdbcTemplate.queryForObject("SELECT status FROM trade_order WHERE idempotency_key = ?", String.class, key))
                    .isEqualTo("SUBMITTED");
            LiveOrder partial = new LiveOrder(submitted.id(), Exchange.UPBIT, clientOrderId, "exchange-id",
                    "PARTIALLY_FILLED", new BigDecimal("0.006"), new BigDecimal("6000"), new BigDecimal("3"), true);
            orders.update(submitted, partial);
            LiveOrder persisted = orders.findByUserAndIdempotencyKey(userId, key).orElseThrow();
            assertThat(persisted.status()).isEqualTo("PARTIALLY_FILLED");
            assertThat(persisted.terminal()).isTrue();
            assertThat(orders.findByUserAndIdempotencyKey(UUID.randomUUID(), key)).isEmpty();
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE user_id = ? AND event_type = 'LIVE_ORDER_SUBMISSION_STARTED'",
                    Integer.class, userId)).isEqualTo(1);
        } finally {
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM trade_order WHERE idempotency_key = ?", key);
            jdbcTemplate.update("DELETE FROM order_plan WHERE id = ?", planId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id = ?", userId);
        }
    }
}
