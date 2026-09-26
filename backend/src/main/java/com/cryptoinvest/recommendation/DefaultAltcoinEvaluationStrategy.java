package com.cryptoinvest.recommendation;

import java.util.Set;

public final class DefaultAltcoinEvaluationStrategy implements CoinEvaluationStrategy {
    @Override public Set<String> metrics() { return Set.of("TVL", "DEX_VOLUME", "ACTIVE_ADDRESSES", "NETWORK_FEES", "NETWORK_ACTIVITY"); }
}
