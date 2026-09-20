package com.cryptoinvest.trading;

import com.cryptoinvest.exchange.Exchange;
import java.math.BigDecimal;
import java.util.UUID;

/** RiskEngine 통과 전의 주문 후보. adapter나 controller가 직접 실행하지 않는다. */
public record OrderPlan(UUID userId, Exchange exchange, String symbol, String side, BigDecimal amount, BigDecimal projectedWeight, String idempotencyKey) {}
