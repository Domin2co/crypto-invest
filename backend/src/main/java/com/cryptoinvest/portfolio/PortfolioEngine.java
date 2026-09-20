package com.cryptoinvest.portfolio;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

/** 보유 자산의 평가액·비중·목표 비중 차이를 계산한다. 주문이나 거래소 I/O는 수행하지 않는다. */
public final class PortfolioEngine {
    private PortfolioEngine() {}
    public static BigDecimal evaluatedAmount(ExchangeBalance balance, BigDecimal price) { return balance.quantity().multiply(price); }
    public static BigDecimal weight(BigDecimal amount, List<BigDecimal> amounts) {
        BigDecimal total = amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.signum() == 0 ? BigDecimal.ZERO : amount.divide(total, MathContext.DECIMAL64);
    }
    public static BigDecimal rebalancingGap(BigDecimal currentWeight, BigDecimal targetWeight) { return targetWeight.subtract(currentWeight); }
}
