package com.cryptoinvest.exchange.credential;

/** 거래소 adapter가 요청 직전에만 사용하는 복호화된 자격증명. 로그·응답 모델로 사용하지 않는다. */
public record ExchangeCredentials(String accessKey, String secretKey) {}
