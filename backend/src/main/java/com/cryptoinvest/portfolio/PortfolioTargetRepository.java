package com.cryptoinvest.portfolio;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 사용자별 목표 비중만 저장하며 주문·거래소 자격증명은 다루지 않는다. */
@Repository
public class PortfolioTargetRepository {
    private final JdbcTemplate jdbcTemplate;
    public PortfolioTargetRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public Map<String, BigDecimal> findByUserAndExchange(UUID userId, Exchange exchange) {
        return jdbcTemplate.query("SELECT currency, target_weight FROM portfolio_target WHERE user_id = ? AND exchange = ?", rs -> {
            Map<String, BigDecimal> targets = new java.util.HashMap<>();
            while (rs.next()) targets.put(rs.getString("currency"), rs.getBigDecimal("target_weight"));
            return Map.copyOf(targets);
        }, userId, exchange.name());
    }

    @Transactional
    public void replace(UUID userId, Exchange exchange, List<Target> targets) {
        if (targets == null || targets.isEmpty() || targets.stream().anyMatch(target -> target == null || target.currency() == null
                || !target.currency().matches("[A-Z0-9]{2,20}") || target.weight() == null || target.weight().signum() < 0 || target.weight().compareTo(BigDecimal.ONE) > 0)
                || targets.stream().map(Target::currency).distinct().count() != targets.size()
                || targets.stream().map(Target::weight).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("Invalid portfolio targets");
        }
        jdbcTemplate.update("DELETE FROM portfolio_target WHERE user_id = ? AND exchange = ?", userId, exchange.name());
        for (Target target : targets) jdbcTemplate.update("INSERT INTO portfolio_target (user_id, exchange, currency, target_weight) VALUES (?, ?, ?, ?)",
                userId, exchange.name(), target.currency(), target.weight());
    }

    public record Target(String currency, BigDecimal weight) {}
}
