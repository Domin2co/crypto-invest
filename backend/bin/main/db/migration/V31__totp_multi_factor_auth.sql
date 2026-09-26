CREATE TABLE user_totp_mfa (
    user_id UUID PRIMARY KEY REFERENCES app_user(id) ON DELETE CASCADE,
    encrypted_secret TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled_at TIMESTAMPTZ,
    last_used_step BIGINT
);
CREATE TABLE user_totp_recovery_code (
    user_id UUID NOT NULL REFERENCES user_totp_mfa(user_id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, code_hash)
);
COMMENT ON TABLE user_totp_mfa IS '사용자별 암호화된 TOTP 다중인증 비밀키와 재사용 방지 상태';
COMMENT ON COLUMN user_totp_mfa.encrypted_secret IS '사용자 ID를 추가인증 데이터로 사용해 AES-GCM 암호화한 TOTP 비밀키';
COMMENT ON COLUMN user_totp_mfa.last_used_step IS '이미 승인한 TOTP 시간 구간으로 재사용 방지';
COMMENT ON TABLE user_totp_recovery_code IS '사용자별 1회용 다중인증 복구 코드 해시';
COMMENT ON COLUMN user_totp_recovery_code.code_hash IS '복구 코드 원문 대신 서버 비밀키 기반 HMAC 지문';
