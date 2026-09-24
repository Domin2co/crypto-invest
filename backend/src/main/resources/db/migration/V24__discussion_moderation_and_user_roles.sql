ALTER TABLE app_user
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE market_discussion_post
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN moderation_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN moderation_reason VARCHAR(500),
    ADD COLUMN moderated_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    ADD COLUMN moderated_at TIMESTAMPTZ;

ALTER TABLE market_discussion_comment
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

CREATE TABLE market_discussion_report (
    id UUID PRIMARY KEY,
    post_id UUID NOT NULL REFERENCES market_discussion_post(id) ON DELETE CASCADE,
    reporter_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    reason VARCHAR(24) NOT NULL CHECK (reason IN ('SPAM', 'ABUSE', 'PERSONAL_INFO', 'OTHER')),
    details VARCHAR(500),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'HIDDEN', 'DISMISSED', 'RESTORED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMPTZ,
    resolved_by UUID REFERENCES app_user(id) ON DELETE SET NULL,
    CONSTRAINT uq_market_discussion_report_user_post UNIQUE (post_id, reporter_user_id)
);

CREATE INDEX ix_market_discussion_report_status_created ON market_discussion_report (status, created_at DESC, id DESC);

COMMENT ON COLUMN app_user.role IS '서버 권한: USER 또는 수동 승인된 ADMIN';
COMMENT ON COLUMN market_discussion_post.updated_at IS '게시글 마지막 수정 시각';
COMMENT ON COLUMN market_discussion_post.moderation_hidden IS '관리자 검토에 따라 공개 목록과 본문을 숨기는 상태';
COMMENT ON COLUMN market_discussion_post.moderation_reason IS '관리자 숨김 사유';
COMMENT ON COLUMN market_discussion_post.moderated_by IS '마지막 숨김 상태를 변경한 관리자';
COMMENT ON COLUMN market_discussion_post.moderated_at IS '마지막 숨김 상태 변경 시각';
COMMENT ON COLUMN market_discussion_comment.updated_at IS '댓글 마지막 수정 시각';
COMMENT ON TABLE market_discussion_report IS '종목 토론 게시글 신고와 관리자 처리 이력';
COMMENT ON COLUMN market_discussion_report.id IS '신고 식별자';
COMMENT ON COLUMN market_discussion_report.post_id IS '신고 대상 게시글';
COMMENT ON COLUMN market_discussion_report.reporter_user_id IS '신고한 사용자';
COMMENT ON COLUMN market_discussion_report.reason IS '신고 사유 분류';
COMMENT ON COLUMN market_discussion_report.details IS '신고 상세 설명, 최대 500자';
COMMENT ON COLUMN market_discussion_report.status IS 'PENDING, HIDDEN, DISMISSED, RESTORED 처리 상태';
COMMENT ON COLUMN market_discussion_report.created_at IS '신고 생성 시각';
COMMENT ON COLUMN market_discussion_report.resolved_at IS '관리자 처리 시각';
COMMENT ON COLUMN market_discussion_report.resolved_by IS '처리한 관리자';
