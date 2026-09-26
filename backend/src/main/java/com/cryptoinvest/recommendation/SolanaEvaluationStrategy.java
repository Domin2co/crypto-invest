package com.cryptoinvest.recommendation;

import java.util.Set;

public final class SolanaEvaluationStrategy implements CoinEvaluationStrategy {
    @Override public Set<String> metrics() { return Set.of("TVL", "DEX_VOLUME", "ACTIVE_ADDRESSES", "STABLECOIN_SUPPLY", "NETWORK_FEES", "NETWORK_ACTIVITY"); }
}
