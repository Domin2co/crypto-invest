package com.cryptoinvest.recommendation;

import java.math.BigDecimal;
import java.util.List;

/** 규칙 기반 추천 결과. 이 값은 주문 요청이 아니며 TradingService를 직접 호출하지 않는다. */
public record Recommendation(String symbol, int score, String signal, BigDecimal targetWeight, List<String> reasons) {}
