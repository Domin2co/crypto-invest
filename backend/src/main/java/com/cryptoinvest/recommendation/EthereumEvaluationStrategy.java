package com.cryptoinvest.recommendation;

import java.util.Set;

public final class EthereumEvaluationStrategy implements CoinEvaluationStrategy {
    @Override public Set<String> metrics() { return Set.of("ETH_ETF_FLOW", "STAKING", "TVL", "STABLECOIN_SUPPLY", "ETH_BTC_STRENGTH", "L2_ACTIVITY"); }
}
