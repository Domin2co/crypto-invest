package com.cryptoinvest.trading;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** 수수료와 거래소 precision을 반영해 실행 전 주문 후보를 계산한다. */
public final class OrderPlanner {
    private OrderPlanner() {}
    public static BigDecimal quantity(BigDecimal amount, BigDecimal price, ExchangeRules rules) {
        if (amount.compareTo(rules.minOrderAmount()) < 0) throw new IllegalArgumentException("Minimum order amount");
        return amount.divide(BigDecimal.ONE.add(rules.feeRate()), 18, RoundingMode.DOWN).divide(price, rules.quantityScale(), RoundingMode.DOWN);
    }
    public static BigDecimal fee(BigDecimal amount, ExchangeRules rules) { return amount.multiply(rules.feeRate()); }
}
