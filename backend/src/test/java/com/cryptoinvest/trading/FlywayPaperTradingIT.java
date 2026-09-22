package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.portfolio.PortfolioTargetRepository;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 Flyway와 PAPER 체결 transaction을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class FlywayPaperTradingIT {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PersistentPaperTradingService paperTrading;
    @Autowired LiveOrderRepository liveOrders;
    @Autowired PortfolioTargetRepository portfolioTargets;

    @Test
    void migratesCommentedSchemaAndPersistsOnePaperFill() {
        UUID userId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        String key = "it-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, ?)", userId, key + "@example.com", "hash");
        jdbcTemplate.update("""
                INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_amount, status, idempotency_key)
                VALUES (?, ?, 'UPBIT', 'BTC', 'BUY', 'MARKET', 10000, 'ACCEPTED', ?)
                """, planId, userId, key);

        OrderPlan plan = new OrderPlan(userId, Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), new BigDecimal("0.1"), key);
        var fill = paperTrading.execute(planId, plan, new BigDecimal("1000"), null, new BigDecimal("0.001"),
                new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE));

        assertThat(fill.fee()).isEqualByComparingTo("10");
        assertThat(jdbcTemplate.queryForObject("SELECT available_amount FROM paper_wallet WHERE user_id = ? AND currency = 'KRW'", BigDecimal.class, userId)).isEqualByComparingTo("989990");
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM trade_order WHERE idempotency_key = ?", Integer.class, key)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        portfolioTargets.replace(userId, Exchange.UPBIT, java.util.List.of(
                new PortfolioTargetRepository.Target("BTC", new BigDecimal("0.35")),
                new PortfolioTargetRepository.Target("KRW", new BigDecimal("0.60"))));
        assertThat(portfolioTargets.findByUserAndExchange(userId, Exchange.UPBIT).get("BTC")).isEqualByComparingTo("0.35");
        UUID liveOrderId = UUID.randomUUID();
        String liveClientOrderId = UUID.randomUUID().toString();
        String liveIdempotencyKey = "live-" + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO trade_order (id, user_id, order_plan_id, exchange, trading_mode, client_order_id, symbol, side,
                order_type, requested_amount, status, idempotency_key)
                VALUES (?, ?, ?, 'UPBIT', 'LIVE', ?, 'BTC', 'BUY', 'MARKET', 10000, 'SUBMITTED', ?)
                """, liveOrderId, userId, planId, liveClientOrderId, liveIdempotencyKey);
        liveOrders.update(new LiveOrder(liveOrderId, Exchange.UPBIT, liveClientOrderId, null, "SUBMITTED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new LiveOrder(null, Exchange.UPBIT, liveClientOrderId, "exchange-id", "FILLED", BigDecimal.ONE, new BigDecimal("10000"), BigDecimal.ZERO));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE event_type = 'LIVE_ORDER_STATUS_UPDATED'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relname IN ('app_user', 'exchange_account', 'asset_snapshot', 'market_candle',
                'recommendation', 'order_plan', 'trade_order', 'audit_log', 'paper_wallet', 'user_consent', 'portfolio_target') AND obj_description(c.oid, 'pg_class') IS NULL
                """, Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE n.nspname = 'public' AND c.relname IN ('app_user', 'exchange_account', 'asset_snapshot', 'market_candle',
                'recommendation', 'order_plan', 'trade_order', 'audit_log', 'paper_wallet', 'user_consent', 'portfolio_target') AND a.attnum > 0 AND NOT a.attisdropped
                AND col_description(c.oid, a.attnum) IS NULL
                """, Integer.class)).isZero();
    }
}
