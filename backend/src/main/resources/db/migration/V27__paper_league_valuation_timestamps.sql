ALTER TABLE paper_league_entry ADD COLUMN starting_value_captured_at TIMESTAMPTZ;
ALTER TABLE paper_league_entry ADD COLUMN final_value_captured_at TIMESTAMPTZ;
COMMENT ON COLUMN paper_league_entry.starting_value_captured_at IS '월간 PAPER 시작 평가액을 시장 시세로 캡처한 시각';
COMMENT ON COLUMN paper_league_entry.final_value_captured_at IS '월간 PAPER 종료 평가액을 시장 시세로 캡처한 시각';
