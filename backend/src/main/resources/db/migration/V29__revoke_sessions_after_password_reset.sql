ALTER TABLE app_user ADD COLUMN auth_token_version INTEGER NOT NULL DEFAULT 0;
COMMENT ON COLUMN app_user.auth_token_version IS '사용자 접속 토큰 버전; 비밀번호 재설정 시 증가됨';
