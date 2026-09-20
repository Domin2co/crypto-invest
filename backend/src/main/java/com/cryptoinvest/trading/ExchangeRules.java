package com.cryptoinvest.trading;

import java.math.BigDecimal;

/** 거래소별 주문 제약. 실제 값은 adapter/설정에서 공급하며 주문 로직에 하드코딩하지 않는다. */
public record ExchangeRules(BigDecimal minOrderAmount, int quantityScale, BigDecimal feeRate) {}
