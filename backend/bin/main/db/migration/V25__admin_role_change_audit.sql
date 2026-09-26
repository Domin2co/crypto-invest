CREATE TABLE admin_role_change_audit (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL,
    target_user_id UUID NOT NULL,
    previous_role VARCHAR(16) NOT NULL CHECK (previous_role IN ('USER', 'ADMIN')),
    new_role VARCHAR(16) NOT NULL CHECK (new_role IN ('USER', 'ADMIN')),
    reason VARCHAR(500) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_admin_role_change_different CHECK (previous_role <> new_role)
);
CREATE INDEX ix_admin_role_change_audit_changed_at ON admin_role_change_audit (changed_at DESC);
COMMENT ON TABLE admin_role_change_audit IS '관리자 권한 변경 담당자, 대상, 변경 전후 역할, 사유 및 시각 감사 기록';
COMMENT ON COLUMN admin_role_change_audit.id IS '권한 변경 기록 식별자';
COMMENT ON COLUMN admin_role_change_audit.actor_user_id IS '역할 변경을 실행한 관리자 사용자 식별자';
COMMENT ON COLUMN admin_role_change_audit.target_user_id IS '역할 변경 대상 사용자 식별자';
COMMENT ON COLUMN admin_role_change_audit.previous_role IS '변경 전 사용자 역할';
COMMENT ON COLUMN admin_role_change_audit.new_role IS '변경 후 사용자 역할';
COMMENT ON COLUMN admin_role_change_audit.reason IS '관리자 역할 변경 사유';
COMMENT ON COLUMN admin_role_change_audit.changed_at IS '관리자 역할 변경 시각';
