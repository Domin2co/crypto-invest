ALTER TABLE app_user ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE app_user ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

COMMENT ON COLUMN app_user.password_hash IS 'BCrypt로 해시한 로그인 비밀번호';
COMMENT ON COLUMN app_user.enabled IS '사용자 계정 사용 가능 여부';
