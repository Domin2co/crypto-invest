package com.cryptoinvest.trading;

/** 자동투자 mode를 주문 후보 필터로 변환한다. 새 주문 실행 경로를 만들지 않는다. */
public final class AutoInvestmentPolicy {
    private AutoInvestmentPolicy() {}
    public static boolean allowsSell(AutoInvestmentMode mode) { return mode == AutoInvestmentMode.REBALANCE_ALL; }
}
