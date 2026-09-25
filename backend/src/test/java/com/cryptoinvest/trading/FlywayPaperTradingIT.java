package com.cryptoinvest.trading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.portfolio.PortfolioTargetRepository;
import com.cryptoinvest.risk.RiskPolicy;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    @Autowired PaperLeagueRepository paperLeague;
    @Autowired PaperLeagueService paperLeagueService;
    @Autowired com.cryptoinvest.security.UserConsentRepository consents;
    @Autowired PaperWalletRepository paperWallets;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void detectsPastMonthSettlementGapsAndOnlyFlagsCurrentOpeningAfterRecoveryWindow() {
        YearMonth month = YearMonth.now(PaperLeagueService.ZONE);
        UUID oldOpening = UUID.randomUUID();
        UUID oldClosing = UUID.randomUUID();
        UUID currentOpening = UUID.randomUUID();
        UUID futureOpening = UUID.randomUUID();
        for (UUID userId : List.of(oldOpening, oldClosing, currentOpening, futureOpening)) {
            jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')",
                    userId, userId + "@example.com");
        }
        paperLeague.enroll(oldOpening, month.minusMonths(1));
        paperLeague.enroll(oldClosing, month.minusMonths(1));
        paperLeague.enroll(currentOpening, month);
        paperLeague.enroll(futureOpening, month.plusMonths(1));
        paperLeague.setStartingValue(oldClosing, month.minusMonths(1), BigDecimal.ONE, java.time.Instant.now());

        assertThat(paperLeague.overdueStartingSnapshots(month, false)).isEqualTo(1);
        assertThat(paperLeague.overdueStartingSnapshots(month, true)).isEqualTo(2);
        assertThat(paperLeague.overdueFinalSnapshots(month)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void monthlyBoundaryLockSerializesInstancesPerMonthAndOperation() {
        YearMonth month = YearMonth.of(2026, 10);
        TransactionTemplate first = new TransactionTemplate(transactionManager);
        TransactionTemplate concurrent = new TransactionTemplate(transactionManager);
        concurrent.setPropagationBehavior(Propagation.REQUIRES_NEW.value());

        first.execute(status -> {
            assertThat(paperLeague.tryBoundaryLock(month, "open")).isTrue();
            boolean duplicateOpen = concurrent.execute(inner -> paperLeague.tryBoundaryLock(month, "open"));
            assertThat(duplicateOpen).isFalse();
            boolean separateClose = concurrent.execute(inner -> paperLeague.tryBoundaryLock(month, "close"));
            assertThat(separateClose).isTrue();
            return null;
        });
        boolean releasedAfterCommit = concurrent.execute(status -> paperLeague.tryBoundaryLock(month, "open"));
        assertThat(releasedAfterCommit).isTrue();
    }
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
    void persistsRecommendationSnapshotAndEnforcesSignedScoreBounds() {
        UUID snapshotId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO recommendation_evaluation_snapshot
                    (id, exchange, market, rule_version, market_regime, market_regime_score,
                     asset_score, confidence_score, rating, evaluated_at)
                VALUES (?, 'UPBIT', 'KRW-BTC', 'rules-v1', 'RISK_OFF', -70, -60, 35,
                        'STRONG_REDUCE', CURRENT_TIMESTAMP)
                """, snapshotId);
        assertThat(jdbcTemplate.queryForObject("SELECT asset_score FROM recommendation_evaluation_snapshot WHERE id = ?", Short.class, snapshotId))
                .isEqualTo((short) -60);
        assertThat(jdbcTemplate.queryForObject("SELECT indicator_values::text FROM recommendation_evaluation_snapshot WHERE id = ?", String.class, snapshotId))
                .isEqualTo("{}");

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO recommendation_evaluation_snapshot
                    (id, exchange, market, rule_version, market_regime, market_regime_score,
                     asset_score, confidence_score, rating, evaluated_at)
                VALUES (?, 'UPBIT', 'KRW-BTC', 'rules-v1', 'NEUTRAL', 0, 101, 35,
                        'STRONG_BUY', CURRENT_TIMESTAMP)
                """, UUID.randomUUID()))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void persistsMarketablePaperLimitPriceAndOrderType() {
        UUID userId = UUID.randomUUID();
        String key = "limit-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash) VALUES (?, ?, 'hash')", userId, key + "@example.com");
        OrderPlan plan = new OrderPlan(userId, Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
        BigDecimal limitPrice = new BigDecimal("1200");
        UUID planId = paperPlans.createOrFind(plan, null, "LIMIT", limitPrice).orElseThrow();
        paperTrading.execute(planId, plan, new BigDecimal("1000"), null, BigDecimal.ZERO,
                new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE), "LIMIT", limitPrice);

        assertThat(jdbcTemplate.queryForObject("SELECT limit_price FROM order_plan WHERE id = ?", BigDecimal.class, planId)).isEqualByComparingTo(limitPrice);
        assertThat(jdbcTemplate.queryForObject("SELECT limit_price FROM trade_order WHERE order_plan_id = ?", BigDecimal.class, planId)).isEqualByComparingTo(limitPrice);
        assertThat(jdbcTemplate.queryForObject("SELECT order_type FROM trade_order WHERE order_plan_id = ?", String.class, planId)).isEqualTo("LIMIT");
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
        assertThat(paperOrders.save(ownerId, ownerPlanId, Exchange.UPBIT, fill, new BigDecimal("1000"))).isTrue();
        PaperTradingService.PaperFill persisted = paperOrders.findByUserAndIdempotencyKey(ownerId, key).orElseThrow();
        assertThat(persisted.symbol()).isEqualTo("BTC");
        assertThat(persisted.quantity()).isEqualByComparingTo("0.01");
        assertThat(paperOrders.findByUserAndIdempotencyKey(otherUserId, key)).isEmpty();
        assertThat(paperOrders.save(otherUserId, ownerPlanId, Exchange.UPBIT, fill, new BigDecimal("1000"))).isFalse();
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
            assertThat(paperOrders.save(ownerId, ownerPlanId, Exchange.UPBIT, existingFill, new BigDecimal("1000"))).isTrue();

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
    @Test
    void participantPaperFillsWaitUntilOpeningValuationSnapshotIsStored() {
        UUID userId = UUID.randomUUID();
        String key = "opening-snapshot-" + UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash, nickname) VALUES (?, ?, 'hash', 'SnapUser')", userId, userId + "@example.com");
        consents.grant(userId, "PAPER_LEADERBOARD", "test-v1");
        YearMonth month = paperLeagueService.currentMonth();
        paperLeague.enroll(userId, month);
        UUID planId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_amount, status, idempotency_key) VALUES (?, ?, 'UPBIT', 'BTC', 'BUY', 'MARKET', 10000, 'ACCEPTED', ?)", planId, userId, key);
        OrderPlan plan = new OrderPlan(userId, Exchange.UPBIT, "BTC", "BUY", new BigDecimal("10000"), BigDecimal.ZERO, key);
        RiskPolicy policy = new RiskPolicy(false, BigDecimal.ONE, new BigDecimal("20000"), BigDecimal.ONE);

        assertThat(paperLeague.hasPendingStart(userId, month)).isTrue();
        assertThatThrownBy(() -> paperTrading.execute(planId, plan, new BigDecimal("1000"), null, BigDecimal.ZERO, policy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Monthly PAPER league start valuation is pending");
        paperLeague.setStartingValue(userId, month, new BigDecimal("2000000"), java.time.Instant.now());
        assertThat(paperLeague.hasPendingStart(userId, month)).isFalse();
        assertThat(paperTrading.execute(planId, plan, new BigDecimal("1000"), null, BigDecimal.ZERO, policy).status()).isEqualTo("FILLED");
    }

    @Test
    void closesMonthlyLeagueWithKrwPaperWalletsAndAwardsOnlyTopThree() {
        YearMonth month = paperLeagueService.currentMonth().minusMonths(1);
        UUID[] users = new UUID[4];
        String[] nicknames = {"LeagueA", "LeagueB", "LeagueC", "LeagueD"};
        String[] balances = {"1400000", "1200000", "1100000", "900000"};
        for (int i = 0; i < users.length; i++) {
            users[i] = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO app_user (id, email, password_hash, nickname) VALUES (?, ?, 'hash', ?)", users[i], users[i] + "@example.com", nicknames[i]);
            consents.grant(users[i], "PAPER_LEADERBOARD", "test-v1");
            paperLeague.enroll(users[i], month);
            paperWallets.initializeKrw(users[i], Exchange.UPBIT, new BigDecimal("1000000"));
            paperWallets.initializeKrw(users[i], Exchange.BITHUMB, new BigDecimal("1000000"));
            String key = "league-" + UUID.randomUUID();
            UUID planId = UUID.randomUUID();
            jdbcTemplate.update("INSERT INTO order_plan (id, user_id, exchange, symbol, side, order_type, requested_amount, status, idempotency_key) VALUES (?, ?, 'UPBIT', 'BTC', 'BUY', 'MARKET', 10000, 'ACCEPTED', ?)", planId, users[i], key);
            jdbcTemplate.update("INSERT INTO trade_order (id, user_id, order_plan_id, exchange, trading_mode, symbol, side, order_type, requested_amount, status, idempotency_key, created_at) VALUES (?, ?, ?, 'UPBIT', 'PAPER', 'BTC', 'BUY', 'MARKET', 10000, 'FILLED', ?, ?)", UUID.randomUUID(), users[i], planId, key, month.atDay(15).atStartOfDay(PaperLeagueService.ZONE).toOffsetDateTime());
        }
        paperLeagueService.openMonth(month);
        for (int i = 0; i < users.length; i++) jdbcTemplate.update("UPDATE paper_wallet SET available_amount = ? WHERE user_id = ? AND exchange = 'UPBIT' AND currency = 'KRW'", new BigDecimal(balances[i]), users[i]);
        paperLeagueService.closeMonth(month);
        var board = paperLeagueService.board(month);
        assertThat(board.status()).isEqualTo("CLOSED");
        assertThat(board.marketDataAt()).isNotNull();
        assertThat(board.standings()).extracting(PaperLeagueService.Standing::nickname).containsExactly("LeagueA", "LeagueB", "LeagueC", "LeagueD");
        assertThat(board.standings()).extracting(PaperLeagueService.Standing::place).containsExactly(1, 2, 3, 4);
        assertThat(board.standings()).extracting(PaperLeagueService.Standing::badge).containsExactly("GOLD", "SILVER", "BRONZE", null);
        assertThat(board.standings()).allSatisfy(row -> assertThat(row.tradeCount()).isEqualTo(1));
    }
}
