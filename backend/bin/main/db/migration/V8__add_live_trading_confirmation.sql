ALTER TABLE user_consent DROP CONSTRAINT user_consent_consent_type_check;
ALTER TABLE user_consent ADD CONSTRAINT user_consent_consent_type_check
    CHECK (consent_type IN ('PRIVACY', 'MARKETING', 'LIVE_TRADING'));

COMMENT ON TABLE user_consent IS '사용자 개인정보·마케팅·실거래 재확인 동의 이력';
COMMENT ON COLUMN user_consent.consent_type IS '개인정보·마케팅·실거래 재확인 동의 유형';
