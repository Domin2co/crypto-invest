package com.cryptoinvest.market;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.time.Instant;

public record MarketPrice(Exchange exchange, String market, BigDecimal price, BigDecimal volume, Instant capturedAt) {}
