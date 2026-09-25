COMMENT ON COLUMN account_email_verification.challenge_id IS '인증 요청 고유 식별자';
COMMENT ON COLUMN account_email_verification.user_id IS '이메일 변경을 요청한 계정, 가입 인증은 NULL';
COMMENT ON COLUMN account_email_verification.email IS '인증 대상 이메일 주소';
COMMENT ON COLUMN account_email_verification.purpose IS '가입 또는 이메일 변경 인증 목적';
COMMENT ON COLUMN account_email_verification.attempts IS '인증 코드 오입력 횟수, 최대 5회';
COMMENT ON COLUMN account_email_verification.created_at IS '인증 요청 시각';
COMMENT ON COLUMN account_email_verification.expires_at IS '인증 코드 만료 시각';
COMMENT ON COLUMN account_email_verification.token_expires_at IS '인증 완료 토큰 만료 시각';
COMMENT ON COLUMN account_email_verification.consumed_at IS '확인 코드 또는 완료 토큰 소비 시각';
