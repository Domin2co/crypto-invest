package com.cryptoinvest.exchange.publicapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import com.cryptoinvest.market.MarketPrice;
import com.cryptoinvest.market.MarketListing;
import java.util.List;

/** 거래소별 공개 시세 API를 공통 모델로 정규화하는 읽기 전용 포트. */
public interface ExchangePublicClient {
    Exchange exchange();
    MarketPrice getPrice(String market);
    List<MarketCandle> getDailyCandles(String market, int count);
    default List<MarketCandle> getCandles(String market, String interval, int count) {
        if (!"1d".equals(interval)) throw new IllegalArgumentException("Unsupported candle interval");
        return getDailyCandles(market, count);
    }
    default List<MarketListing> getMarkets() { return List.of(); }
}
