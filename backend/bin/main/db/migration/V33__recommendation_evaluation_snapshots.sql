CREATE TABLE recommendation_evaluation_snapshot (
    id UUID PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    market VARCHAR(32) NOT NULL,
    rule_version VARCHAR(40) NOT NULL,
    market_regime VARCHAR(16) NOT NULL CHECK (market_regime IN ('RISK_ON', 'NEUTRAL', 'RISK_OFF')),
    market_regime_score SMALLINT NOT NULL CHECK (market_regime_score BETWEEN -100 AND 100),
    asset_score SMALLINT NOT NULL CHECK (asset_score BETWEEN -100 AND 100),
    confidence_score SMALLINT NOT NULL CHECK (confidence_score BETWEEN 0 AND 100),
    rating VARCHAR(20) NOT NULL CHECK (rating IN ('STRONG_BUY', 'BUY', 'HOLD', 'REDUCE', 'STRONG_REDUCE')),
    indicator_values JSONB NOT NULL DEFAULT '{}'::jsonb,
    factor_contributions JSONB NOT NULL DEFAULT '[]'::jsonb,
    data_quality JSONB NOT NULL DEFAULT '{}'::jsonb,
    evaluated_at TIMESTAMPTZ NOT NULL,
    forward_7d_return_pct NUMERIC(12, 6),
    forward_30d_return_pct NUMERIC(12, 6),
    outcome_observed_at TIMESTAMPTZ,
    CHECK (outcome_observed_at IS NULL OR outcome_observed_at >= evaluated_at)
);
CREATE INDEX idx_recommendation_snapshot_market_time
    ON recommendation_evaluation_snapshot (exchange, market, evaluated_at DESC);
CREATE INDEX idx_recommendation_snapshot_rule_time
    ON recommendation_evaluation_snapshot (rule_version, evaluated_at DESC);
COMMENT ON TABLE recommendation_evaluation_snapshot IS '규칙 버전별 시점 추천 결과와 사후 성과 검증 스냅샷';
COMMENT ON COLUMN recommendation_evaluation_snapshot.id IS '추천 평가 스냅샷 식별자';
COMMENT ON COLUMN recommendation_evaluation_snapshot.exchange IS '평가 시세 거래소';
COMMENT ON COLUMN recommendation_evaluation_snapshot.market IS '거래소 종목 시장 코드';
COMMENT ON COLUMN recommendation_evaluation_snapshot.rule_version IS '평가 규칙 버전';
COMMENT ON COLUMN recommendation_evaluation_snapshot.market_regime IS '평가 시점 전체 시장 상태';
COMMENT ON COLUMN recommendation_evaluation_snapshot.market_regime_score IS '시장 상태 점수, -100에서 100';
COMMENT ON COLUMN recommendation_evaluation_snapshot.asset_score IS '개별 자산 점수, -100에서 100';
COMMENT ON COLUMN recommendation_evaluation_snapshot.confidence_score IS '데이터 품질 기반 신뢰도, 0에서 100';
COMMENT ON COLUMN recommendation_evaluation_snapshot.rating IS '개별 자산 추천 단계';
COMMENT ON COLUMN recommendation_evaluation_snapshot.indicator_values IS '시점별 지표 값과 출처 요약 JSON';
COMMENT ON COLUMN recommendation_evaluation_snapshot.factor_contributions IS '점수에 반영된 설명 가능 요인 JSON';
COMMENT ON COLUMN recommendation_evaluation_snapshot.data_quality IS '결측·최신성·이상치 상태 JSON';
COMMENT ON COLUMN recommendation_evaluation_snapshot.evaluated_at IS '추천 평가 기준 시각';
COMMENT ON COLUMN recommendation_evaluation_snapshot.forward_7d_return_pct IS '평가 뒤 7일 관측 수익률 퍼센트';
COMMENT ON COLUMN recommendation_evaluation_snapshot.forward_30d_return_pct IS '평가 뒤 30일 관측 수익률 퍼센트';
COMMENT ON COLUMN recommendation_evaluation_snapshot.outcome_observed_at IS '사후 성과 데이터를 확인한 시각';