package com.cryptoinvest.recommendation;

import static com.cryptoinvest.recommendation.RecommendationModels.*;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Free public sources only. Each source fails independently and missing values stay unavailable. */
@Service
public class PublicMarketDataService {
    private static final long CACHE_MILLIS = 300_000;
    private final RestClient http;
    private final String coinGeckoDemoKey;
    private volatile Snapshot cache = new Snapshot(Instant.EPOCH, Map.of());

    public PublicMarketDataService(RestClient.Builder builder, @Value("${recommendation.sources.coingecko-demo-api-key:}") String coinGeckoDemoKey) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1500);
        factory.setReadTimeout(2500);
        this.http = builder.requestFactory(factory).build();
        this.coinGeckoDemoKey = coinGeckoDemoKey;
    }

    public Map<String, MetricObservation> metrics() {
        Snapshot current = cache;
        if (current.capturedAt().plusMillis(CACHE_MILLIS).isBefore(Instant.now())) {
            synchronized (this) {
                current = cache;
                if (current.capturedAt().plusMillis(CACHE_MILLIS).isBefore(Instant.now())) cache = current = refresh();
            }
        }
        return current.metrics();
    }

    public Map<String, MetricObservation> assetMetrics(String symbol) {
        Map<String, MetricObservation> all = metrics();
        String chain = switch (symbol) { case "ETH" -> "Ethereum"; case "SOL" -> "Solana"; default -> null; };
        if (chain == null) return Map.of();
        return Map.of("TVL_CHANGE_24H", all.getOrDefault("TVL_CHANGE_24H_" + symbol, unavailable("TVL_CHANGE_24H_" + symbol)),
                "STABLECOIN_CHANGE_24H", all.getOrDefault("STABLECOIN_CHANGE_24H_" + symbol, unavailable("STABLECOIN_CHANGE_24H_" + symbol)),
                "DEX_VOLUME_24H", all.getOrDefault("DEX_VOLUME_24H_" + symbol, unavailable("DEX_VOLUME_24H_" + symbol)));
    }

    private Snapshot refresh() {
        Map<String, MetricObservation> out = new LinkedHashMap<>(unavailableDefaults());
        fetchFearGreed(out);
        fetchCoinGecko(out);
        fetchBinance(out);
        fetchDefiLlama(out, "ETH", "Ethereum");
        fetchDefiLlama(out, "SOL", "Solana");
        return new Snapshot(Instant.now(), Map.copyOf(out));
    }

    private void fetchFearGreed(Map<String, MetricObservation> out) {
        try {
            JsonNode response = http.get().uri("https://api.alternative.me/fng/?limit=1").retrieve().body(JsonNode.class);
            if (response == null) return;
            JsonNode row = response.path("data").path(0);
            BigDecimal value = decimal(row.path("value"));
            Instant at = Instant.ofEpochSecond(Long.parseLong(row.path("timestamp").asText()));
            put(out, "FEAR_GREED", value, "Alternative.me Fear & Greed Index", at, "Daily sentiment index, 0-100; attribution required");
        } catch (RuntimeException ignored) { }
    }

    private void fetchCoinGecko(Map<String, MetricObservation> out) {
        if (coinGeckoDemoKey.isBlank()) return;
        try {
            JsonNode response = http.get().uri("https://api.coingecko.com/api/v3/global").header("x-cg-demo-api-key", coinGeckoDemoKey).retrieve().body(JsonNode.class);
            if (response == null) return;
            JsonNode data = response.path("data");
            Instant at = Instant.ofEpochSecond(data.path("updated_at").asLong());
            put(out, "TOTAL_MARKET_CAP", decimal(data.path("total_market_cap").path("usd")), "CoinGecko Demo global market data", at, null);
            put(out, "TOTAL_MARKET_VOLUME", decimal(data.path("total_volume").path("usd")), "CoinGecko Demo global market data", at, null);
            put(out, "BTC_DOMINANCE", decimal(data.path("market_cap_percentage").path("btc")), "CoinGecko Demo global market data", at, "percent");
            put(out, "MARKET_CAP_CHANGE_24H", decimal(data.path("market_cap_change_percentage_24h_usd")), "CoinGecko Demo global market data", at, "percent");
            put(out, "MARKET_VOLUME_CHANGE_24H", decimal(data.path("volume_change_percentage_24h_usd")), "CoinGecko Demo global market data", at, "percent");
        } catch (RuntimeException ignored) { }
    }

    private void fetchBinance(Map<String, MetricObservation> out) {
        try {
            JsonNode row = http.get().uri("https://fapi.binance.com/fapi/v1/premiumIndex?symbol=BTCUSDT").retrieve().body(JsonNode.class);
            if (row != null) put(out, "FUNDING_RATE", decimal(row.path("lastFundingRate")), "Binance USD-M BTCUSDT perpetual", Instant.ofEpochMilli(row.path("time").asLong()), "Derivative funding; not Korean spot flow");
        } catch (RuntimeException ignored) { }
        try {
            JsonNode row = http.get().uri("https://fapi.binance.com/fapi/v1/openInterest?symbol=BTCUSDT").retrieve().body(JsonNode.class);
            if (row != null) put(out, "OPEN_INTEREST", decimal(row.path("openInterest")), "Binance USD-M BTCUSDT perpetual", Instant.ofEpochMilli(row.path("time").asLong()), "Open interest level; change unavailable without comparable history");
        } catch (RuntimeException ignored) { }
    }

    private void fetchDefiLlama(Map<String, MetricObservation> out, String symbol, String chain) {
        try {
            JsonNode history = http.get().uri("https://api.llama.fi/v2/historicalChainTvl/{chain}", chain).retrieve().body(JsonNode.class);
            if (history != null && history.size() >= 2) {
                JsonNode previous = history.path(history.size() - 2);
                JsonNode latest = history.path(history.size() - 1);
                BigDecimal before = decimal(previous.path("tvl"));
                BigDecimal current = decimal(latest.path("tvl"));
                BigDecimal change = percentChange(before, current);
                put(out, "TVL_CHANGE_24H_" + symbol, change, "DefiLlama free historical chain TVL (" + chain + ")", Instant.ofEpochSecond(latest.path("date").asLong()), "Daily snapshots; chain TVL is a network-level proxy");
            }
        } catch (RuntimeException ignored) { }
        try {
            JsonNode dex = http.get().uri("https://api.llama.fi/overview/dexs/{chain}?excludeTotalDataChart=true&excludeTotalDataChartBreakdown=true", chain).retrieve().body(JsonNode.class);
            BigDecimal volume = dex == null ? null : decimal(dex.path("total24h"));
            if (volume != null) put(out, "DEX_VOLUME_24H_" + symbol, volume, "DefiLlama free DEX volume overview (" + chain + ")", Instant.now(), "24-hour USD volume");
        } catch (RuntimeException ignored) { }
        try {
            JsonNode history = http.get().uri("https://stablecoins.llama.fi/stablecoincharts/{chain}", chain).retrieve().body(JsonNode.class);
            if (history != null && history.size() >= 2) {
                BigDecimal before = decimal(history.path(history.size() - 2).path("totalCirculatingUSD").path("peggedUSD"));
                BigDecimal current = decimal(history.path(history.size() - 1).path("totalCirculatingUSD").path("peggedUSD"));
                put(out, "STABLECOIN_CHANGE_24H_" + symbol, percentChange(before, current), "DefiLlama free stablecoin history (" + chain + ")", Instant.ofEpochSecond(history.path(history.size() - 1).path("date").asLong()), "Chain stablecoin USD supply change");
            }
        } catch (RuntimeException ignored) { }
    }

    private Map<String, MetricObservation> unavailableDefaults() {
        Map<String, MetricObservation> values = new LinkedHashMap<>();
        for (String key : List.of("FEAR_GREED", "TOTAL_MARKET_CAP", "TOTAL_MARKET_VOLUME", "BTC_DOMINANCE", "MARKET_CAP_CHANGE_24H", "MARKET_VOLUME_CHANGE_24H", "FUNDING_RATE", "OPEN_INTEREST", "BTC_ETH_ETF_FLOW", "TVL_CHANGE_24H_ETH", "DEX_VOLUME_24H_ETH", "STABLECOIN_CHANGE_24H_ETH", "TVL_CHANGE_24H_SOL", "DEX_VOLUME_24H_SOL", "STABLECOIN_CHANGE_24H_SOL")) values.put(key, unavailable(key));
        return values;
    }
    private MetricObservation unavailable(String key) { return new MetricObservation(key, null, null, MetricStatus.UNAVAILABLE, null, null, "No free source observation available"); }
    private void put(Map<String, MetricObservation> values, String key, BigDecimal value, String source, Instant at, String note) {
        if (value == null || at == null || value.signum() < 0 && !key.contains("CHANGE") && !key.equals("FUNDING_RATE")) return;
        MetricStatus status = at.isBefore(Instant.now().minusSeconds(key.equals("FEAR_GREED") || key.contains("24H") ? 172800 : 3600)) ? MetricStatus.STALE : MetricStatus.AVAILABLE;
        values.put(key, new MetricObservation(key, value, null, status, source, at, note));
    }
    private BigDecimal decimal(JsonNode value) { try { return value.isNumber() ? value.decimalValue() : value.isTextual() ? new BigDecimal(value.asText()) : null; } catch (NumberFormatException ex) { return null; } }
    private BigDecimal percentChange(BigDecimal before, BigDecimal current) { return before == null || current == null || before.signum() == 0 ? null : current.subtract(before).multiply(BigDecimal.valueOf(100)).divide(before, 8, java.math.RoundingMode.HALF_UP); }
    private record Snapshot(Instant capturedAt, Map<String, MetricObservation> metrics) {}
}
