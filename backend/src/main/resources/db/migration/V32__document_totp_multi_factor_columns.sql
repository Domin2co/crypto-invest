COMMENT ON COLUMN user_totp_mfa.user_id IS '다중 인증 사용자 식별자';
COMMENT ON COLUMN user_totp_mfa.enabled IS '사용자의 TOTP 다중 인증 활성화 상태';
COMMENT ON COLUMN user_totp_mfa.created_at IS '인증 앱 등록 시작 시각';
COMMENT ON COLUMN user_totp_mfa.enabled_at IS '다중 인증 활성화 시각';
COMMENT ON COLUMN user_totp_recovery_code.user_id IS '복구 코드 소유 사용자 식별자';
COMMENT ON COLUMN user_totp_recovery_code.created_at IS '복구 코드 생성 시각';