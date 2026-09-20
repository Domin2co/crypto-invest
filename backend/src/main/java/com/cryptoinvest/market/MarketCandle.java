package com.cryptoinvest.market;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketCandle(
        Exchange exchange, String market, Instant openedAt, BigDecimal open, BigDecimal high,
        BigDecimal low, BigDecimal close, BigDecimal volume) {}
