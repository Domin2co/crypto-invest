package com.cryptoinvest.market;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.ExchangePublicClient;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 공개 시세 조회 API. 이 controller는 자격증명과 주문 기능을 전혀 다루지 않는다. */
@RestController
@RequestMapping("/api/markets")
public class MarketController {
    private final Map<Exchange, ExchangePublicClient> clients;

    public MarketController(List<ExchangePublicClient> clients) {
        this.clients = clients.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ExchangePublicClient::exchange, Function.identity()));
    }

    @GetMapping("/{exchange}/ticker")
    public MarketPrice ticker(@PathVariable Exchange exchange, @RequestParam String market) {
        return client(exchange).getPrice(market);
    }

    @GetMapping("/{exchange}/candles/days")
    public List<MarketCandle> dailyCandles(@PathVariable Exchange exchange, @RequestParam String market,
            @RequestParam(defaultValue = "1") int count) {
        return client(exchange).getDailyCandles(market, count);
    }

    private ExchangePublicClient client(Exchange exchange) {
        ExchangePublicClient client = clients.get(exchange);
        if (client == null) throw new IllegalArgumentException("Unsupported exchange");
        return client;
    }
}
