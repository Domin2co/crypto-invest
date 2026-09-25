package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import com.cryptoinvest.market.MarketPrice;
import com.cryptoinvest.security.UserConsentRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaperLeagueService {
    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private final PaperLeagueRepository league;
    private final PaperWalletRepository wallets;
    private final UserConsentRepository consents;
    private final Map<Exchange, ExchangePublicClient> markets;
    private final BigDecimal initialKrw;

    public PaperLeagueService(PaperLeagueRepository league, PaperWalletRepository wallets, UserConsentRepository consents,
            List<ExchangePublicClient> markets, @Value("${app.paper-initial-krw:1000000}") BigDecimal initialKrw) {
        this.league = league; this.wallets = wallets; this.consents = consents; this.initialKrw = initialKrw;
        this.markets = markets.stream().collect(Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
    }

    public EntryStatus entry(UUID userId) {
        YearMonth next = currentMonth().plusMonths(1);
        return new EntryStatus(consents.isActive(userId, "PAPER_LEADERBOARD"), next.toString(), league.enrolled(userId, next));
    }

    public EntryStatus enroll(UUID userId) {
        if (!consents.isActive(userId, "PAPER_LEADERBOARD")) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PAPER_LEADERBOARD_CONSENT_REQUIRED");
        YearMonth next = currentMonth().plusMonths(1);
        for (Exchange exchange : Exchange.values()) wallets.initializeKrw(userId, exchange, initialKrw);
        league.enroll(userId, next);
        return entry(userId);
    }

    public Board board(YearMonth month) {
        YearMonth current = currentMonth();
        List<Standing> standings;
        Instant capturedAt = null;
        String status;
        if (month.equals(current)) {
            List<PaperLeagueRepository.Entry> entries = league.findForMonth(month).stream()
                    .filter(e -> e.startingValue() != null && e.startingValue().signum() > 0 && e.tradeCount() > 0).toList();
            Valuations valuations = entries.isEmpty() ? new Valuations(Map.of(), null)
                    : values(entries.stream().map(PaperLeagueRepository.Entry::userId).toList());
            Map<UUID, BigDecimal> currentValues = valuations.values();
            capturedAt = valuations.capturedAt();
            standings = rank(entries.stream().map(e -> standing(e, currentValues.getOrDefault(e.userId(), e.startingValue()))).toList());
            status = standings.isEmpty() ? "WAITING" : "ACTIVE";
        } else {
            List<PaperLeagueRepository.Entry> entries = league.findForMonth(month);
            standings = entries.stream().filter(e -> e.place() != null).map(e -> new Standing(e.nickname(), e.returnPercent(), e.tradeCount(), e.place(), e.badge())).toList();
            capturedAt = entries.stream().map(PaperLeagueRepository.Entry::finalCapturedAt).filter(java.util.Objects::nonNull).min(Instant::compareTo).orElse(null);
            status = standings.isEmpty() ? "WAITING" : "CLOSED";
        }
        YearMonth latest = league.latestCompletedMonth();
        List<Standing> winners = latest == null ? List.of() : league.findForMonth(latest).stream()
                .filter(e -> e.place() != null && e.place() <= 3).map(e -> new Standing(e.nickname(), e.returnPercent(), e.tradeCount(), e.place(), e.badge())).toList();
        return new Board(month.toString(), status, capturedAt, standings, latest == null ? null : latest.toString(), winners);
    }

    @Transactional
    public void openMonth(YearMonth month) {
        if (!league.tryBoundaryLock(month, "open")) return;
        List<UUID> pending = league.pendingStarts(month);
        if (pending.isEmpty()) return;
        for (UUID userId : pending) for (Exchange exchange : Exchange.values()) wallets.initializeKrw(userId, exchange, initialKrw);
        Valuations valuation = values(pending);
        pending.forEach(userId -> league.setStartingValue(userId, month, valuation.values().get(userId), valuation.capturedAt()));
    }

    @Transactional
    public void closeMonth(YearMonth month) {
        if (!league.tryBoundaryLock(month, "close")) return;
        List<UUID> pending = league.pendingFinals(month);
        if (pending.isEmpty()) return;
        List<PaperLeagueRepository.Entry> entries = league.findForMonth(month).stream().filter(e -> pending.contains(e.userId())).toList();
        Valuations valuation = values(pending);
        Map<UUID, BigDecimal> values = valuation.values();
        for (PaperLeagueRepository.Entry entry : entries) {
            BigDecimal value = values.getOrDefault(entry.userId(), BigDecimal.ZERO);
            BigDecimal starting = entry.startingValue();
            BigDecimal result = starting.signum() == 0 ? BigDecimal.ZERO : value.subtract(starting).multiply(ONE_HUNDRED).divide(starting, 8, RoundingMode.HALF_UP);
            league.setFinalValue(entry.userId(), month, value, result, entry.tradeCount(), valuation.capturedAt());
        }
        List<Standing> ranked = rank(league.findForMonth(month).stream().filter(e -> e.tradeCount() > 0)
                .map(e -> new Standing(e.nickname(), e.returnPercent(), e.tradeCount(), null, null)).toList());
        Map<String, Standing> byNickname = ranked.stream().collect(Collectors.toMap(Standing::nickname, Function.identity()));
        for (PaperLeagueRepository.Entry entry : league.findForMonth(month)) {
            Standing standing = byNickname.get(entry.nickname());
            if (standing == null) league.clearPlace(entry.userId(), month);
            else league.setPlace(entry.userId(), month, standing.place(), badge(standing.place()));
        }
    }

    public YearMonth currentMonth() { return YearMonth.now(ZONE); }

    private Valuations values(List<UUID> userIds) {
        List<PaperLeagueRepository.Wallet> balances = league.wallets(userIds);
        Map<QuoteKey, BigDecimal> prices = new LinkedHashMap<>();
        Instant capturedAt = null;
        for (PaperLeagueRepository.Wallet wallet : balances) {
            if (wallet.currency().equals("KRW")) continue;
            QuoteKey key = new QuoteKey(wallet.exchange(), wallet.currency());
            if (prices.containsKey(key)) continue;
            ExchangePublicClient client = markets.get(wallet.exchange());
            if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Market quote unavailable");
            try {
                MarketPrice quote = client.getPrice("KRW-" + wallet.currency());
                if (!quote.market().equals("KRW-" + wallet.currency()) || quote.price() == null || quote.price().signum() <= 0
                        || quote.capturedAt() == null || quote.capturedAt().isBefore(Instant.now().minusSeconds(30))) {
                    throw new IllegalStateException("Invalid market quote");
                }
                prices.put(key, quote.price());
                if (capturedAt == null || quote.capturedAt().isBefore(capturedAt)) capturedAt = quote.capturedAt();
            } catch (RuntimeException exception) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Market quote unavailable");
            }
        }
        Map<UUID, BigDecimal> totals = userIds.stream().distinct().collect(Collectors.toMap(Function.identity(), id -> BigDecimal.ZERO));
        for (PaperLeagueRepository.Wallet wallet : balances) {
            BigDecimal value = wallet.currency().equals("KRW") ? wallet.amount() : wallet.amount().multiply(prices.get(new QuoteKey(wallet.exchange(), wallet.currency())));
            totals.compute(wallet.userId(), (id, total) -> total.add(value));
        }
        return new Valuations(totals, capturedAt == null ? Instant.now() : capturedAt);
    }

    private static Standing standing(PaperLeagueRepository.Entry entry, BigDecimal value) {
        BigDecimal change = value.subtract(entry.startingValue());
        BigDecimal percent = change.multiply(ONE_HUNDRED).divide(entry.startingValue(), 8, RoundingMode.HALF_UP);
        return new Standing(entry.nickname(), percent, entry.tradeCount(), entry.place(), entry.badge());
    }
    private static List<Standing> rank(List<Standing> entries) {
        List<Standing> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparing(Standing::returnPercent).reversed()
                .thenComparing(Standing::nickname, String.CASE_INSENSITIVE_ORDER));
        List<Standing> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Standing row = sorted.get(i);
            result.add(new Standing(row.nickname(), row.returnPercent(), row.tradeCount(), i + 1, null));
        }
        return List.copyOf(result);
    }
    private static String badge(int place) { return switch (place) { case 1 -> "GOLD"; case 2 -> "SILVER"; case 3 -> "BRONZE"; default -> null; }; }

    private record QuoteKey(Exchange exchange, String currency) {}
    private record Valuations(Map<UUID, BigDecimal> values, Instant capturedAt) {}
    public record EntryStatus(boolean publicConsent, String nextMonth, boolean enrolled) {}
    public record Standing(String nickname, BigDecimal returnPercent, int tradeCount, Integer place, String badge) {}
    public record Board(String month, String status, Instant marketDataAt, List<Standing> standings,
            String latestCompletedMonth, List<Standing> latestAwards) {}
}