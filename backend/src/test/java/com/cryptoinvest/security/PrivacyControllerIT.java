package com.cryptoinvest.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** 개인정보 동의는 선택 철회 가능하고 계정 삭제는 API key와 직접 식별 정보를 제거하는지 검증한다. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrivacyControllerIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        registry.add("app.auth-token-secret", () -> key);
        registry.add("app.credential-encryption-key", () -> key);
    }
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void exportsOwnConsentAllowsMarketingWithdrawalAndAnonymizesDeletion() throws Exception {
        String email = "privacy-" + UUID.randomUUID() + "@example.com";
        var registration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "long-enough-password", "privacyAccepted", true, "marketingAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString()).path("accessToken").asText();

        mockMvc.perform(get("/api/privacy/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.consents.length()").value(2));
        mockMvc.perform(patch("/api/privacy/marketing-consent").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/privacy/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email)).isZero();
    }
}
