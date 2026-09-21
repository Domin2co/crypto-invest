CREATE TABLE user_consent (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES app_user(id),
    consent_type VARCHAR(32) NOT NULL CHECK (consent_type IN ('PRIVACY', 'MARKETING')),
    policy_version VARCHAR(32) NOT NULL,
    granted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    withdrawn_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_user_consent_type UNIQUE (user_id, consent_type)
);

CREATE INDEX idx_user_consent_user_id ON user_consent (user_id);

COMMENT ON TABLE user_consent IS '사용자 개인정보 및 마케팅 수신 동의 이력';
COMMENT ON COLUMN user_consent.id IS '동의 식별자';
COMMENT ON COLUMN user_consent.user_id IS '사용자 식별자';
COMMENT ON COLUMN user_consent.consent_type IS '동의 유형';
COMMENT ON COLUMN user_consent.policy_version IS '동의한 정책 버전';
COMMENT ON COLUMN user_consent.granted_at IS '동의 시각';
COMMENT ON COLUMN user_consent.withdrawn_at IS '철회 시각';
