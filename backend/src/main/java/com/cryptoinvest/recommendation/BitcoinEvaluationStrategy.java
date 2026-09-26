package com.cryptoinvest.recommendation;

import java.util.Set;

public final class BitcoinEvaluationStrategy implements CoinEvaluationStrategy {
    @Override public Set<String> metrics() { return Set.of("ETF_FLOW", "MVRV", "SOPR", "EXCHANGE_BALANCE", "LONG_TERM_HOLDER", "BTC_DOMINANCE"); }
}
