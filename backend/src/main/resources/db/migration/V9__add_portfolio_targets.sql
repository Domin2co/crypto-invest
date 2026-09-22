CREATE TABLE portfolio_target (
    user_id UUID NOT NULL REFERENCES app_user(id),
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    currency VARCHAR(20) NOT NULL,
    target_weight NUMERIC(9, 8) NOT NULL CHECK (target_weight >= 0 AND target_weight <= 1),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, exchange, currency)
);

COMMENT ON TABLE portfolio_target IS '사용자별 포트폴리오 목표 비중';
COMMENT ON COLUMN portfolio_target.user_id IS '사용자 식별자';
COMMENT ON COLUMN portfolio_target.exchange IS '거래소 구분';
COMMENT ON COLUMN portfolio_target.currency IS '자산 통화 코드';
COMMENT ON COLUMN portfolio_target.target_weight IS '목표 비중';
COMMENT ON COLUMN portfolio_target.updated_at IS '수정 시각';
