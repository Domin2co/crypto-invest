package com.cryptoinvest.exchange.publicapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import com.cryptoinvest.market.MarketPrice;
import com.cryptoinvest.market.MarketListing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Upbit 공개 시세·시장 목록을 내부 market 모델로 변환한다. */
@Component
public class UpbitPublicClient extends AbstractPublicClient implements ExchangePublicClient {
    private static final String BASE_URL = "https://api.upbit.com";
    public UpbitPublicClient(ObjectMapper objectMapper) { super(objectMapper); }
    public Exchange exchange() { return Exchange.UPBIT; }
    public MarketPrice getPrice(String market) {
        validate(market, 1); JsonNode node = get(BASE_URL + "/v1/ticker?markets=" + market).get(0);
        return new MarketPrice(exchange(), market, node.path("trade_price").decimalValue(), node.path("acc_trade_volume_24h").decimalValue(), Instant.ofEpochMilli(node.path("timestamp").longValue()));
    }
    public List<MarketCandle> getCandles(String market, String interval, int count) {
        validate(market, count);
        String path = switch (interval) {
            case "1m", "3m", "5m", "10m", "15m", "30m", "60m", "240m" -> "/v1/candles/minutes/" + interval.substring(0, interval.length() - 1);
            case "1d" -> "/v1/candles/days";
            case "1w" -> "/v1/candles/weeks";
            case "1M" -> "/v1/candles/months";
            default -> throw new IllegalArgumentException("Unsupported candle interval");
        };
        JsonNode nodes = get(BASE_URL + path + "?market=" + market + "&count=" + count);
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false).map(n -> new MarketCandle(exchange(), market,
                Instant.parse(n.path("candle_date_time_utc").textValue() + "Z"), n.path("opening_price").decimalValue(), n.path("high_price").decimalValue(), n.path("low_price").decimalValue(), n.path("trade_price").decimalValue(), n.path("candle_acc_trade_volume").decimalValue())).toList();
    }
    public List<MarketListing> getMarkets() {
        JsonNode nodes = get(BASE_URL + "/v1/market/all?is_details=false");
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false)
                .filter(n -> n.path("market").asText().startsWith("KRW-"))
                .map(n -> new MarketListing(n.path("market").asText(), n.path("korean_name").asText(), n.path("english_name").asText()))
                .sorted(java.util.Comparator.comparing(MarketListing::market)).toList();
    }
    public List<MarketCandle> getDailyCandles(String market, int count) {
        validate(market, count); JsonNode nodes = get(BASE_URL + "/v1/candles/days?market=" + market + "&count=" + count);
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false).map(n -> new MarketCandle(exchange(), market,
                Instant.parse(n.path("candle_date_time_utc").textValue() + "Z"), n.path("opening_price").decimalValue(), n.path("high_price").decimalValue(), n.path("low_price").decimalValue(), n.path("trade_price").decimalValue(), n.path("candle_acc_trade_volume").decimalValue())).toList();
    }
}
