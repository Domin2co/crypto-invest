CREATE TABLE paper_wallet (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    currency VARCHAR(32) NOT NULL,
    available_amount NUMERIC(38, 18) NOT NULL CHECK (available_amount >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_paper_wallet_user_currency UNIQUE (user_id, currency)
);

CREATE INDEX idx_paper_wallet_user_id ON paper_wallet (user_id);

COMMENT ON TABLE paper_wallet IS '사용자별 모의투자 가상 지갑 잔고';
COMMENT ON COLUMN paper_wallet.id IS '모의투자 지갑 잔고 식별자';
COMMENT ON COLUMN paper_wallet.user_id IS '사용자 식별자';
COMMENT ON COLUMN paper_wallet.currency IS '자산 통화 또는 심볼';
COMMENT ON COLUMN paper_wallet.available_amount IS '사용 가능한 가상 잔고';
COMMENT ON COLUMN paper_wallet.created_at IS '생성 일시';
COMMENT ON COLUMN paper_wallet.updated_at IS '수정 일시';
