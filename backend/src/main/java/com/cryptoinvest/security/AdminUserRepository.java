package com.cryptoinvest.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminUserRepository {
    private final JdbcTemplate jdbc;

    public AdminUserRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<AdminUser> search(String query) {
        return jdbc.query("SELECT id,email,nickname,role,created_at FROM app_user WHERE enabled=TRUE AND (LOWER(email) LIKE LOWER(?) OR LOWER(COALESCE(nickname,'')) LIKE LOWER(?)) ORDER BY created_at DESC LIMIT 20",
                (rs, row) -> new AdminUser(UUID.fromString(rs.getString("id")), rs.getString("email"), rs.getString("nickname"), rs.getString("role"), rs.getTimestamp("created_at").toInstant()), "%" + query + "%", "%" + query + "%");
    }

    // ponytail: serialize all role changes globally; split locks by account only if admin volume warrants it.
    public void lockRoleChanges() { jdbc.execute("SELECT pg_advisory_xact_lock(751904128)"); }

    public void lockEnabledAdmins() {
        jdbc.query("SELECT id FROM app_user WHERE role='ADMIN' AND enabled=TRUE ORDER BY id FOR UPDATE", (rs, row) -> rs.getObject(1, UUID.class));
    }

    public String enabledRole(UUID userId) {
        return jdbc.query("SELECT role FROM app_user WHERE id=? AND enabled=TRUE", rs -> rs.next() ? rs.getString(1) : null, userId);
    }

    public int enabledAdminCount() {
        return jdbc.queryForObject("SELECT count(*) FROM app_user WHERE role='ADMIN' AND enabled=TRUE", Integer.class);
    }

    public void updateRole(UUID userId, String role) {
        if (jdbc.update("UPDATE app_user SET role=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND enabled=TRUE", role, userId) != 1) throw new IllegalStateException("Account is unavailable");
    }

    public void recordChange(UUID actor, UUID target, String oldRole, String newRole, String reason) {
        jdbc.update("INSERT INTO admin_role_change_audit(id,actor_user_id,target_user_id,previous_role,new_role,reason) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), actor, target, oldRole, newRole, reason);
    }

    public List<RoleChange> recentChanges() {
        return jdbc.query("SELECT a.actor_user_id,a.target_user_id,a.previous_role,a.new_role,a.reason,a.changed_at,CASE WHEN actor.enabled THEN COALESCE(NULLIF(actor.nickname,''),actor.email) ELSE '삭제된 계정' END actor_label,CASE WHEN target.enabled THEN COALESCE(NULLIF(target.nickname,''),target.email) ELSE '삭제된 계정' END target_label FROM admin_role_change_audit a LEFT JOIN app_user actor ON actor.id=a.actor_user_id LEFT JOIN app_user target ON target.id=a.target_user_id ORDER BY a.changed_at DESC,a.id DESC LIMIT 20",
                (rs, row) -> new RoleChange(UUID.fromString(rs.getString("actor_user_id")), UUID.fromString(rs.getString("target_user_id")), rs.getString("previous_role"), rs.getString("new_role"), rs.getString("reason"), rs.getTimestamp("changed_at").toInstant(), rs.getString("actor_label"), rs.getString("target_label")));
    }

    public record AdminUser(UUID id, String email, String nickname, String role, Instant createdAt) {}
    public record RoleChange(UUID actorUserId, UUID targetUserId, String previousRole, String newRole, String reason, Instant changedAt, String actorLabel, String targetLabel) {}
}
