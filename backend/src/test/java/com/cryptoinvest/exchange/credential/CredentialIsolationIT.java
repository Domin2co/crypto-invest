package com.cryptoinvest.exchange.credential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 인증한 사용자 ID로만 거래소 자격증명을 암호화해 저장하는 API 통합 테스트다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CredentialIsolationIT {
    @DynamicPropertySource
    static void securityProperties(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        registry.add("app.auth-token-secret", () -> key);
        registry.add("app.credential-encryption-key", () -> key);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void permitsOnlyTheConfiguredFrontendOriginForApiCorsRequests() throws Exception {
        mockMvc.perform(options("/api/exchange-accounts")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mockMvc.perform(options("/api/exchange-accounts")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void storesEncryptedCredentialsOnlyForTheAuthenticatedUser() throws Exception {
        String email = "api-" + UUID.randomUUID() + "@example.com";
        var registration = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "long-enough-password", "privacyAccepted", true, "marketingAccepted", false))))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString())
                .path("accessToken")
                .asText();

        mockMvc.perform(post("/api/exchange-accounts").contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("exchange", "UPBIT", "accessKey", "test-access", "secretKey", "test-secret"))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/exchange-accounts").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("exchange", "UPBIT", "accessKey", "test-access", "secretKey", "test-secret"))))
                .andExpect(status().isNoContent());

        Map<String, Object> stored = jdbcTemplate.queryForMap("SELECT encrypted_access_key, encrypted_secret_key FROM exchange_account WHERE user_id = (SELECT id FROM app_user WHERE email = ?)", email);
        assertThat(stored.get("encrypted_access_key")).isNotEqualTo("test-access");
        assertThat(stored.get("encrypted_secret_key")).isNotEqualTo("test-secret");
    }
}
