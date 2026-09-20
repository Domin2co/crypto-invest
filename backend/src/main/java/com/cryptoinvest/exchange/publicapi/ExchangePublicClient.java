package com.cryptoinvest.exchange.publicapi;

import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.market.MarketCandle;
import com.cryptoinvest.market.MarketPrice;
import java.util.List;

/** 거래소별 공개 시세 API를 공통 모델로 정규화하는 읽기 전용 포트. */
public interface ExchangePublicClient {
    Exchange exchange();
    MarketPrice getPrice(String market);
    List<MarketCandle> getDailyCandles(String market, int count);
}
