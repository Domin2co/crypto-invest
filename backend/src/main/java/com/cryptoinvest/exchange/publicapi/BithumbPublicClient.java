package com.cryptoinvest.exchange.publicapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import com.cryptoinvest.market.MarketPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Bithumb 공개 ticker·일봉 응답을 내부 market 모델로 변환한다. */
@Component
public class BithumbPublicClient extends AbstractPublicClient implements ExchangePublicClient {
    private static final String BASE_URL = "https://api.bithumb.com";
    public BithumbPublicClient(ObjectMapper objectMapper) { super(objectMapper); }
    public Exchange exchange() { return Exchange.BITHUMB; }
    public MarketPrice getPrice(String market) {
        validate(market, 1); JsonNode node = get(BASE_URL + "/v1/ticker?markets=" + market).get(0);
        return new MarketPrice(exchange(), market, node.path("trade_price").decimalValue(), node.path("acc_trade_volume_24h").decimalValue(), Instant.ofEpochMilli(node.path("timestamp").longValue()));
    }
    public List<MarketCandle> getDailyCandles(String market, int count) {
        validate(market, count); JsonNode nodes = get(BASE_URL + "/v1/candles/days?market=" + market + "&count=" + count);
        return java.util.stream.StreamSupport.stream(nodes.spliterator(), false).map(n -> new MarketCandle(exchange(), market,
                Instant.parse(n.path("candle_date_time_utc").textValue() + "Z"), n.path("opening_price").decimalValue(), n.path("high_price").decimalValue(), n.path("low_price").decimalValue(), n.path("trade_price").decimalValue(), n.path("candle_acc_trade_volume").decimalValue())).toList();
    }
}
