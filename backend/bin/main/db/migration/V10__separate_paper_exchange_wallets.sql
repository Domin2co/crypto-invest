ALTER TABLE paper_wallet ADD COLUMN exchange VARCHAR(16) NOT NULL DEFAULT 'UPBIT';
ALTER TABLE paper_wallet DROP CONSTRAINT uq_paper_wallet_user_currency;
ALTER TABLE paper_wallet ADD CONSTRAINT uq_paper_wallet_user_exchange_currency UNIQUE (user_id, exchange, currency);

ALTER TABLE trade_order ADD COLUMN executed_price NUMERIC(38, 18);
UPDATE trade_order SET executed_price = executed_amount / NULLIF(executed_quantity, 0)
WHERE trading_mode = 'PAPER' AND executed_quantity IS NOT NULL AND executed_amount IS NOT NULL;

COMMENT ON COLUMN paper_wallet.exchange IS '모의투자 가상 잔고의 거래소';
COMMENT ON COLUMN trade_order.executed_price IS '모의 체결 시 적용된 거래소 가격';
