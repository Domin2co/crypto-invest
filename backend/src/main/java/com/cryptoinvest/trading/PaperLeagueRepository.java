package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PaperLeagueRepository {
    private final JdbcTemplate jdbc;
    public PaperLeagueRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }    public boolean tryBoundaryLock(YearMonth month, String operation) {
        if (!operation.equals("open") && !operation.equals("close")) throw new IllegalArgumentException("Invalid boundary operation");
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtext('paper-league'), hashtext(?))", Boolean.class,
                month + ":" + operation));
    }

    public void enroll(UUID userId, YearMonth month) {
        jdbc.update("INSERT INTO paper_league_entry (id, user_id, league_month) VALUES (?, ?, ?) ON CONFLICT (user_id, league_month) DO NOTHING",
                UUID.randomUUID(), userId, Date.valueOf(month.atDay(1)));
    }
    public boolean enrolled(UUID userId, YearMonth month) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM paper_league_entry WHERE user_id = ? AND league_month = ?)",
                Boolean.class, userId, Date.valueOf(month.atDay(1))));
    }
    public List<Entry> findForMonth(YearMonth month) {
        return jdbc.query("""
                SELECT e.id, e.user_id, u.nickname, e.starting_value, e.final_value, e.return_percent,
                    (SELECT COUNT(*) FROM trade_order t WHERE t.user_id = e.user_id AND t.trading_mode = 'PAPER'
                     AND t.status = 'FILLED' AND t.created_at >= ? AND t.created_at < ?) AS trade_count,
                    e.place, e.badge
                FROM paper_league_entry e
                JOIN app_user u ON u.id = e.user_id AND u.enabled = TRUE AND u.nickname IS NOT NULL
                JOIN user_consent c ON c.user_id = e.user_id AND c.consent_type = 'PAPER_LEADERBOARD' AND c.withdrawn_at IS NULL
                WHERE e.league_month = ?
                ORDER BY e.created_at, u.nickname
                """, (rs, row) -> new Entry(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), rs.getString(3),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getInt(7),
                (Integer) rs.getObject(8), rs.getString(9)),
                month.atDay(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                month.plusMonths(1).atDay(1).atStartOfDay(java.time.ZoneId.of("Asia/Seoul")).toOffsetDateTime(),
                Date.valueOf(month.atDay(1)));
    }
    public List<Wallet> wallets(List<UUID> userIds) {
        if (userIds.isEmpty()) return List.of();
        String marks = userIds.stream().map(id -> "?").collect(Collectors.joining(","));
        return jdbc.query("SELECT user_id, exchange, currency, available_amount FROM paper_wallet WHERE user_id IN (" + marks + ") ORDER BY user_id, exchange, currency",
                (rs, row) -> new Wallet(UUID.fromString(rs.getString(1)), Exchange.valueOf(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4)), userIds.toArray());
    }
    public List<UUID> pendingStarts(YearMonth month) {
        return jdbc.query("SELECT user_id FROM paper_league_entry WHERE league_month = ? AND starting_value IS NULL ORDER BY user_id",
                (rs, row) -> UUID.fromString(rs.getString(1)), Date.valueOf(month.atDay(1)));
    }
    public List<UUID> pendingFinals(YearMonth month) {
        return jdbc.query("SELECT user_id FROM paper_league_entry WHERE league_month = ? AND starting_value IS NOT NULL AND final_value IS NULL ORDER BY user_id",
                (rs, row) -> UUID.fromString(rs.getString(1)), Date.valueOf(month.atDay(1)));
    }
    public void setStartingValue(UUID userId, YearMonth month, BigDecimal value) {
        jdbc.update("UPDATE paper_league_entry SET starting_value = ? WHERE user_id = ? AND league_month = ? AND starting_value IS NULL",
                value, userId, Date.valueOf(month.atDay(1)));
    }
    public void setFinalValue(UUID userId, YearMonth month, BigDecimal value, BigDecimal returnPercent, int tradeCount) {
        jdbc.update("UPDATE paper_league_entry SET final_value = ?, return_percent = ?, trade_count = ? WHERE user_id = ? AND league_month = ? AND final_value IS NULL",
                value, returnPercent, tradeCount, userId, Date.valueOf(month.atDay(1)));
    }
    public void setPlace(UUID userId, YearMonth month, int place, String badge) {
        jdbc.update("UPDATE paper_league_entry SET place = ?, badge = ? WHERE user_id = ? AND league_month = ? AND final_value IS NOT NULL",
                place, badge, userId, Date.valueOf(month.atDay(1)));
    }
    public void clearPlace(UUID userId, YearMonth month) {
        jdbc.update("UPDATE paper_league_entry SET place = NULL, badge = NULL WHERE user_id = ? AND league_month = ? AND final_value IS NOT NULL",
                userId, Date.valueOf(month.atDay(1)));
    }
    public YearMonth latestCompletedMonth() {
        Date date = jdbc.queryForObject("SELECT MAX(league_month) FROM paper_league_entry WHERE final_value IS NOT NULL AND place IS NOT NULL", Date.class);
        return date == null ? null : YearMonth.from(date.toLocalDate());
    }
    public void deleteByUserId(UUID userId) { jdbc.update("DELETE FROM paper_league_entry WHERE user_id = ?", userId); }

    public record Entry(UUID id, UUID userId, String nickname, BigDecimal startingValue, BigDecimal finalValue,
            BigDecimal returnPercent, int tradeCount, Integer place, String badge) {}
    public record Wallet(UUID userId, Exchange exchange, String currency, BigDecimal amount) {}
}