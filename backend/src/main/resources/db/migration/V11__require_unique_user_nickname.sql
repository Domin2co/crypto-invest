ALTER TABLE app_user ADD COLUMN nickname VARCHAR(8);
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_nickname
    CHECK (nickname IS NULL OR (char_length(nickname) BETWEEN 2 AND 8 AND nickname ~ '^[A-Za-z0-9가-힣]+$'));
CREATE UNIQUE INDEX uq_app_user_nickname_lower ON app_user (LOWER(nickname)) WHERE nickname IS NOT NULL;

COMMENT ON COLUMN app_user.nickname IS '계정 및 공개 랭킹에 사용하는 고유 닉네임';