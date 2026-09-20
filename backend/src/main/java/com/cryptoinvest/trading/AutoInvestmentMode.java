package com.cryptoinvest.trading;

/** 자동투자 시 기존 보유 자산을 매도 가능한지 결정하는 정책. 실행 권한은 별도 RiskEngine이 검증한다. */
public enum AutoInvestmentMode { REBALANCE_ALL, KEEP_EXISTING_ASSETS, CASH_ONLY }
