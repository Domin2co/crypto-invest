ALTER TABLE user_consent DROP CONSTRAINT user_consent_consent_type_check;
ALTER TABLE user_consent ADD CONSTRAINT user_consent_consent_type_check
    CHECK (consent_type IN ('PRIVACY', 'MARKETING', 'LIVE_TRADING', 'PAPER_LEADERBOARD'));

CREATE TABLE paper_league_entry (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    league_month DATE NOT NULL CHECK (EXTRACT(DAY FROM league_month) = 1),
    starting_value NUMERIC(38, 18) CHECK (starting_value IS NULL OR starting_value >= 0),
    final_value NUMERIC(38, 18) CHECK (final_value IS NULL OR final_value >= 0),
    return_percent NUMERIC(18, 8),
    trade_count INTEGER NOT NULL DEFAULT 0 CHECK (trade_count >= 0),
    place INTEGER CHECK (place IS NULL OR place >= 1),
    badge VARCHAR(16) CHECK (badge IS NULL OR badge IN ('GOLD', 'SILVER', 'BRONZE')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_paper_league_user_month UNIQUE (user_id, league_month),
    CONSTRAINT ck_paper_league_finish CHECK ((final_value IS NULL AND place IS NULL AND badge IS NULL) OR final_value IS NOT NULL)
);

CREATE INDEX idx_paper_league_month ON paper_league_entry (league_month, return_percent DESC);

COMMENT ON TABLE user_consent IS '사용자 개인정보·마케팅·실거래 및 PAPER 공개 랭킹 동의 이력';
COMMENT ON COLUMN user_consent.consent_type IS '개인정보·마케팅·실거래·PAPER 공개 랭킹 동의 유형';
COMMENT ON TABLE paper_league_entry IS '월별 PAPER 성과 대회 참가 및 결과';
COMMENT ON COLUMN paper_league_entry.id IS '월간 PAPER 대회 참가 식별자';
COMMENT ON COLUMN paper_league_entry.user_id IS '참가자 계정 식별자';
COMMENT ON COLUMN paper_league_entry.league_month IS '참가 대회 월의 첫날';
COMMENT ON COLUMN paper_league_entry.starting_value IS '대회 시작 시점의 PAPER 전체 평가액(KRW)';
COMMENT ON COLUMN paper_league_entry.final_value IS '대회 종료 시점의 PAPER 전체 평가액(KRW)';
COMMENT ON COLUMN paper_league_entry.return_percent IS '시작 평가액 대비 종료 수익률(퍼센트)';
COMMENT ON COLUMN paper_league_entry.trade_count IS '대회 기간 PAPER 주문 체결 수';
COMMENT ON COLUMN paper_league_entry.place IS '대회 종료 후 확정된 순위';
COMMENT ON COLUMN paper_league_entry.badge IS '대회 상위 3위 메달';
COMMENT ON COLUMN paper_league_entry.created_at IS '대회 참가 신청 시각';