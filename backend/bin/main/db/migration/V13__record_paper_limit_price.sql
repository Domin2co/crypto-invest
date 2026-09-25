ALTER TABLE order_plan ADD COLUMN limit_price NUMERIC(38, 18);
COMMENT ON COLUMN order_plan.limit_price IS '사용자가 지정한 모의 호가 주문 가격(KRW)';

ALTER TABLE trade_order ADD COLUMN limit_price NUMERIC(38, 18);
COMMENT ON COLUMN trade_order.limit_price IS '모의 호가 주문의 지정 가격(KRW), 시장 주문은 NULL';
