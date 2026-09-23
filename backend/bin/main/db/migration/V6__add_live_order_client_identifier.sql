ALTER TABLE trade_order ADD COLUMN client_order_id VARCHAR(36);
ALTER TABLE trade_order ADD CONSTRAINT uq_trade_order_client_order_id UNIQUE (client_order_id);

COMMENT ON COLUMN trade_order.client_order_id IS '거래소 조회용 클라이언트 주문 식별자';
