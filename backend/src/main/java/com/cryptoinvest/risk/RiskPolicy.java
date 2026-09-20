package com.cryptoinvest.risk;

import java.math.BigDecimal;

/** 환경/사용자 설정에서 주입될 거래 안전 한도. 값은 주문 코드에 hard-code하지 않는다. */
public record RiskPolicy(boolean killSwitch, BigDecimal minOrderAmount, BigDecimal maxOrderAmount, BigDecimal maxAssetWeight) {}
