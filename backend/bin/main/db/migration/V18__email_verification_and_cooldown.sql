ALTER TABLE app_user ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_user ADD COLUMN email_changed_at TIMESTAMPTZ;
UPDATE app_user SET email_changed_at = created_at;
ALTER TABLE app_user ALTER COLUMN email_changed_at SET DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE app_user ALTER COLUMN email_changed_at SET NOT NULL;

CREATE TABLE account_email_verification (
    challenge_id UUID PRIMARY KEY,
    user_id UUID REFERENCES app_user(id) ON DELETE CASCADE,
    email VARCHAR(254) NOT NULL,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('SIGNUP', 'EMAIL_CHANGE')),
    code_hash VARCHAR(64) NOT NULL,
    attempts SMALLINT NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    verified_token_hash VARCHAR(64),
    token_expires_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ
);
CREATE INDEX ix_account_email_verification_email ON account_email_verification (LOWER(email), purpose, created_at DESC);
COMMENT ON TABLE account_email_verification IS '이메일 인증 challenge, 원문 코드 대신 서버 HMAC 해시 저장';
COMMENT ON COLUMN account_email_verification.code_hash IS '메일로 전송한 일회용 코드의 HMAC-SHA256 해시';
COMMENT ON COLUMN account_email_verification.verified_token_hash IS '인증 완료 후 발급한 일회용 가입/이메일 변경 토큰의 HMAC-SHA256 해시';
COMMENT ON COLUMN app_user.email_verified IS '현재 계정 이메일의 소유 확인 여부';
COMMENT ON COLUMN app_user.email_changed_at IS '가입 또는 마지막 이메일 변경 시각, 90일 변경 제한의 기준';
