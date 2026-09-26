package com.cryptoinvest.recommendation;

import java.util.Set;

public final class XrpEvaluationStrategy implements CoinEvaluationStrategy {
    @Override public Set<String> metrics() { return Set.of("XRPL_ACTIVITY", "XRPL_DEX_VOLUME", "RWA_SIZE", "RLUSD_METRICS", "ETF_FLOW", "INSTITUTIONAL_ADOPTION"); }
}
