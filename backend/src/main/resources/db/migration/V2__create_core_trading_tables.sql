CREATE TABLE app_user (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE exchange_account (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    encrypted_access_key VARCHAR(1024) NOT NULL,
    encrypted_secret_key VARCHAR(2048) NOT NULL,
    key_version SMALLINT NOT NULL DEFAULT 1 CHECK (key_version > 0),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_exchange_account_user_exchange UNIQUE (user_id, exchange)
);

CREATE TABLE asset_snapshot (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    symbol VARCHAR(32) NOT NULL,
    quantity NUMERIC(38, 18) NOT NULL CHECK (quantity >= 0),
    average_buy_price NUMERIC(38, 18),
    current_price NUMERIC(38, 18) NOT NULL CHECK (current_price >= 0),
    evaluated_amount NUMERIC(38, 18) NOT NULL CHECK (evaluated_amount >= 0),
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_asset_snapshot_user_captured_at ON asset_snapshot (user_id, captured_at DESC);

CREATE TABLE market_candle (
    id UUID PRIMARY KEY,
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    market VARCHAR(32) NOT NULL,
    candle_interval VARCHAR(20) NOT NULL,
    opened_at TIMESTAMP WITH TIME ZONE NOT NULL,
    open_price NUMERIC(38, 18) NOT NULL CHECK (open_price >= 0),
    high_price NUMERIC(38, 18) NOT NULL CHECK (high_price >= 0),
    low_price NUMERIC(38, 18) NOT NULL CHECK (low_price >= 0),
    close_price NUMERIC(38, 18) NOT NULL CHECK (close_price >= 0),
    volume NUMERIC(38, 18) NOT NULL CHECK (volume >= 0),
    CONSTRAINT uq_market_candle UNIQUE (exchange, market, candle_interval, opened_at),
    CONSTRAINT ck_market_candle_price_range CHECK (low_price <= high_price)
);

CREATE INDEX idx_market_candle_market_opened_at ON market_candle (exchange, market, opened_at DESC);

CREATE TABLE recommendation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    symbol VARCHAR(32) NOT NULL,
    score NUMERIC(5, 2) NOT NULL CHECK (score >= 0 AND score <= 100),
    signal VARCHAR(32) NOT NULL,
    target_weight NUMERIC(9, 8) NOT NULL CHECK (target_weight >= 0 AND target_weight <= 1),
    reason TEXT NOT NULL,
    generated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_recommendation_user_generated_at ON recommendation (user_id, generated_at DESC);

CREATE TABLE order_plan (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(20) NOT NULL,
    requested_quantity NUMERIC(38, 18),
    requested_amount NUMERIC(38, 18),
    status VARCHAR(32) NOT NULL CHECK (status IN ('PLANNED', 'RISK_REJECTED', 'ACCEPTED')),
    risk_reason VARCHAR(500),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_order_plan_request CHECK (
        (requested_quantity IS NOT NULL AND requested_quantity > 0)
        OR (requested_amount IS NOT NULL AND requested_amount > 0)
    )
);

CREATE TABLE trade_order (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    order_plan_id UUID NOT NULL REFERENCES order_plan(id),
    exchange VARCHAR(20) NOT NULL CHECK (exchange IN ('UPBIT', 'BITHUMB')),
    trading_mode VARCHAR(16) NOT NULL CHECK (trading_mode IN ('PAPER', 'LIVE')),
    exchange_order_id VARCHAR(128),
    symbol VARCHAR(32) NOT NULL,
    side VARCHAR(4) NOT NULL CHECK (side IN ('BUY', 'SELL')),
    order_type VARCHAR(20) NOT NULL,
    requested_quantity NUMERIC(38, 18),
    requested_amount NUMERIC(38, 18),
    executed_quantity NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (executed_quantity >= 0),
    executed_amount NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (executed_amount >= 0),
    fee NUMERIC(38, 18) NOT NULL DEFAULT 0 CHECK (fee >= 0),
    status VARCHAR(32) NOT NULL CHECK (status IN ('SUBMITTED', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'FAILED', 'UNKNOWN')),
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_trade_order_request CHECK (
        (requested_quantity IS NOT NULL AND requested_quantity > 0)
        OR (requested_amount IS NOT NULL AND requested_amount > 0)
    )
);

CREATE INDEX idx_trade_order_user_created_at ON trade_order (user_id, created_at DESC);
CREATE INDEX idx_trade_order_exchange_symbol ON trade_order (exchange, symbol);

CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id),
    event_type VARCHAR(64) NOT NULL,
    exchange VARCHAR(20),
    symbol VARCHAR(32),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_user_created_at ON audit_log (user_id, created_at DESC);
