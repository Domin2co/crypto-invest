CREATE TABLE api_rate_limit_bucket (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0)
);
CREATE INDEX idx_api_rate_limit_window ON api_rate_limit_bucket (window_started_at);
COMMENT ON TABLE api_rate_limit_bucket IS '인증 API 요청 제한을 위한 해시 키별 고정 시간 구간 카운터';
COMMENT ON COLUMN api_rate_limit_bucket.bucket_key IS '원본 개인정보를 포함하지 않는 서버 비밀키 기반 제한 지문';
COMMENT ON COLUMN api_rate_limit_bucket.window_started_at IS '요청 제한 구간 시작 시각';
COMMENT ON COLUMN api_rate_limit_bucket.request_count IS '해당 구간의 요청 수';
