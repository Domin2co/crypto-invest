package com.cryptoinvest.portfolio;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;

/** 거래소 응답 형식과 무관한 보유 자산. 주문 가능 수량만 공개한다. */
public record ExchangeBalance(Exchange exchange, String currency, BigDecimal quantity, BigDecimal averageBuyPrice) {}
