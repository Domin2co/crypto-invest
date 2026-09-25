package com.cryptoinvest.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.cryptoinvest.security.EmailVerificationService;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUserControllerIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        registry.add("app.auth-token-secret", () -> key);
        registry.add("app.credential-encryption-key", () -> key);
    }
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailVerificationService emailVerification;

    @Test
    void onlyAdminCanSearchAndEveryRoleChangeIsAudited() throws Exception {
        String admin = register("role-admin-" + UUID.randomUUID() + "@example.com", "ADMIN");
        String user = register("role-target-" + UUID.randomUUID() + "@example.com", "USER");
        mockMvc.perform(get("/api/admin/users?q=target").header("Authorization", "Bearer " + user)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users?q=target").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.users.length()").value(1)).andExpect(jsonPath("$.users[0].role").value("USER"));
        UUID target = jdbc.queryForObject("SELECT id FROM app_user WHERE email LIKE 'role-target-%@example.com' ORDER BY created_at DESC LIMIT 1", UUID.class);
        mockMvc.perform(patch("/api/admin/users/" + target + "/role").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"운영 담당 승인\"}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, target)).isEqualTo("ADMIN");
        mockMvc.perform(get("/api/admin/users?q=target").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recentChanges[0].actorUserId").exists()).andExpect(jsonPath("$.recentChanges[0].actorLabel").isNotEmpty()).andExpect(jsonPath("$.recentChanges[0].targetLabel").isNotEmpty()).andExpect(jsonPath("$.recentChanges[0].reason").value("운영 담당 승인"));
        mockMvc.perform(patch("/api/admin/users/" + target + "/role").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"역할 회수 요청\"}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, target)).isEqualTo("USER");
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM admin_role_change_audit WHERE target_user_id=?", Integer.class, target)).isEqualTo(2);
    }

    @Test
    void refusesSelfDemotionAndInvalidReason() throws Exception {
        String admin = register("role-last-admin-" + UUID.randomUUID() + "@example.com", "ADMIN");
        UUID adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE email LIKE 'role-last-admin-%@example.com' ORDER BY created_at DESC LIMIT 1", UUID.class);
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/role").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"운영자 변경\"}"))
                .andExpect(status().isBadRequest());
        String user = register("role-invalid-" + UUID.randomUUID() + "@example.com", "USER");
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email LIKE 'role-invalid-%@example.com' ORDER BY created_at DESC LIMIT 1", UUID.class);
        mockMvc.perform(patch("/api/admin/users/" + userId + "/role").header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\",\"reason\":\"ab\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/role").header("Authorization", "Bearer " + user).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"reason\":\"self revoke\"}"))
                .andExpect(status().isForbidden());
    }

    private String register(String email, String role) throws Exception {
        var response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        UUID id = jdbc.queryForObject("SELECT id FROM app_user WHERE email=?", UUID.class, email);
        jdbc.update("UPDATE app_user SET nickname=?,role=? WHERE id=?", "Role" + UUID.randomUUID().toString().replace("-", "").substring(0, 4), role, id);
        return mapper.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
