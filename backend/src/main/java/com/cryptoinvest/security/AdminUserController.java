package com.cryptoinvest.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserRepository users;

    public AdminUserController(AdminUserRepository users) { this.users = users; }

    @GetMapping
    public UserSearchResponse search(@RequestParam(defaultValue = "") String q, Authentication auth) {
        requireAdmin(auth);
        String query = q.strip();
        if (!query.isEmpty() && (query.length() < 2 || query.length() > 80)) throw new IllegalArgumentException("Search query must contain 2 to 80 characters");
        return new UserSearchResponse(query.isEmpty() ? List.of() : users.search(query), users.recentChanges());
    }

    @PatchMapping("/{userId}/role") @Transactional
    public void changeRole(@PathVariable UUID userId, @Valid @RequestBody RoleChangeRequest request, Authentication auth) {
        UUID actor = (UUID) auth.getPrincipal();
        requireAdmin(auth);
        String reason = request.reason().strip();
        if (reason.length() < 5) throw new IllegalArgumentException("A role change reason is required");
        users.lockRoleChanges();
        users.lockEnabledAdmins();
        if (!"ADMIN".equals(users.enabledRole(actor))) throw new AccessDeniedException("Administrator access required");
        String oldRole = users.enabledRole(userId);
        if (oldRole == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (oldRole.equals(request.role())) throw new IllegalStateException("Role is unchanged");
        if (actor.equals(userId) && "USER".equals(request.role())) throw new IllegalArgumentException("Administrators cannot revoke their own role");
        if ("ADMIN".equals(oldRole) && "USER".equals(request.role()) && users.enabledAdminCount() <= 1) throw new IllegalStateException("The last administrator cannot be removed");
        users.updateRole(userId, request.role());
        users.recordChange(actor, userId, oldRole, request.role(), reason);
    }

    private void requireAdmin(Authentication auth) {
        if (auth == null || !"ADMIN".equals(users.enabledRole((UUID) auth.getPrincipal()))) throw new AccessDeniedException("Administrator access required");
    }

    public record RoleChangeRequest(@NotBlank @Pattern(regexp = "USER|ADMIN") String role, @NotBlank @Size(max = 500) String reason) {}
    public record UserSearchResponse(List<AdminUserRepository.AdminUser> users, List<AdminUserRepository.RoleChange> recentChanges) {}
}
