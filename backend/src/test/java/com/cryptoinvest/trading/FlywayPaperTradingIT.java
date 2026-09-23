package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.portfolio.PortfolioTargetRepository;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 실제 PostgreSQL에서 Flyway와 PAPER 체결 transaction을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "app.auth-token-secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
@Transactional
class FlywayPaperTradingIT {
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PersistentPaperTradingService paperTrading;
    @Autowired LiveOrderRepository liveOrders;
    @Autowired PortfolioTargetRepository portfolioTargets;
    @Autowired PaperOrderPlanRepository paperPlans;
    @Autowired PaperOrderAuditRepository paperOrders;

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
        liveOrders.update(new LiveOrder(liveOrderId, Exchange.UPBIT, liveClientOrderId, null, "SUBMITTED", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false),
                new LiveOrder(null, Exchange.UPBIT, liveClientOrderId, "exchange-id", "FILLED", BigDecimal.ONE, new BigDecimal("10000"), BigDecimal.ZERO, true));
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM audit_log WHERE event_type = 'LIVE_ORDER_STATUS_UPDATED'", Integer.class)).isEqualTo(1);
        // 모든 애플리케이션 테이블과 컬럼에 comment가 있고, 유지보수자가 읽을 수 있도록 한글을 포함하는지 확인한다.
        List<String> schemaComments = jdbcTemplate.queryForList("""
                SELECT obj_description(c.oid, 'pg_class')
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND c.relname <> 'flyway_schema_history'
                UNION ALL
                SELECT col_description(c.oid, a.attnum)
                FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p') AND c.relname <> 'flyway_schema_history'
                AND a.attnum > 0 AND NOT a.attisdropped
                """, String.class);
        assertThat(schemaComments).isNotEmpty().allSatisfy(comment ->
                assertThat(comment).isNotBlank().containsPattern("[가-힣]"));
    }

    @Test
    void scopesIdempotencyLookupsAndPlansToTheirOwner() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String key = "shared-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')", ownerId, key + "-a@example.com");
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')", otherUserId, key + "-b@example.com");

        OrderPlan ownerPlan = new OrderPlan(ownerId, Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
        UUID ownerPlanId = paperPlans.createOrFind(ownerPlan, null).orElseThrow();
        OrderPlan otherPlan = new OrderPlan(otherUserId, Exchange.UPBIT, "ETH", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
        assertThat(paperPlans.createOrFind(otherPlan, null)).isEmpty();

        PaperTradingService.PaperFill fill = new PaperTradingService.PaperFill(
                "BTC", "BUY", new BigDecimal("0.01"), new BigDecimal("10000"), BigDecimal.ZERO, "FILLED", key);
        assertThat(paperOrders.save(ownerId, ownerPlanId, Exchange.UPBIT, fill)).isTrue();
        PaperTradingService.PaperFill persisted = paperOrders.findByUserAndIdempotencyKey(ownerId, key).orElseThrow();
        assertThat(persisted.symbol()).isEqualTo("BTC");
        assertThat(persisted.quantity()).isEqualByComparingTo("0.01");
        assertThat(paperOrders.findByUserAndIdempotencyKey(otherUserId, key)).isEmpty();
        assertThat(paperOrders.save(otherUserId, ownerPlanId, Exchange.UPBIT, fill)).isFalse();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackWalletWhenAnotherUserAlreadyOwnsTheGlobalIdempotencyKey() {
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        String key = "collision-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')", ownerId, key + "-a@example.com");
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')", otherUserId, key + "-b@example.com");
        UUID planId = null;
        try {
            OrderPlan ownerPlan = new OrderPlan(ownerId, Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
            UUID ownerPlanId = paperPlans.createOrFind(ownerPlan, null).orElseThrow();
            planId = ownerPlanId;
            PaperTradingService.PaperFill existingFill = new PaperTradingService.PaperFill(
                    "BTC", "BUY", new BigDecimal("0.01"), new BigDecimal("10000"), BigDecimal.ZERO, "FILLED", key);
            assertThat(paperOrders.save(ownerId, ownerPlanId, Exchange.UPBIT, existingFill)).isTrue();

            OrderPlan otherPlan = new OrderPlan(otherUserId, Exchange.UPBIT, "ETH", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
            assertThatThrownBy(() -> paperTrading.execute(ownerPlanId, otherPlan, new BigDecimal("1000"), null,
                    BigDecimal.ZERO, new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Paper order idempotency conflict");
            assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM paper_wallet WHERE user_id = ?", Integer.class, otherUserId)).isZero();
            assertThat(paperOrders.findByUserAndIdempotencyKey(otherUserId, key)).isEmpty();
        } finally {
            jdbcTemplate.update("DELETE FROM audit_log WHERE user_id IN (?, ?)", ownerId, otherUserId);
            jdbcTemplate.update("DELETE FROM trade_order WHERE user_id IN (?, ?)", ownerId, otherUserId);
            jdbcTemplate.update("DELETE FROM paper_wallet WHERE user_id IN (?, ?)", ownerId, otherUserId);
            if (planId != null) jdbcTemplate.update("DELETE FROM order_plan WHERE id = ?", planId);
            jdbcTemplate.update("DELETE FROM app_user WHERE id IN (?, ?)", ownerId, otherUserId);
        }
    }
}
