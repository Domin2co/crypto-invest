package com.cryptoinvest.market;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cryptoinvest.security.EmailVerificationService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketDiscussionControllerIT {
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        registry.add("app.auth-token-secret", () -> key);
        registry.add("app.credential-encryption-key", () -> key);
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailVerificationService emailVerificationService;

    @Test
    void publishesPostsPerSymbolAndDeletesThemWithTheAccount() throws Exception {
        String email = "discussion-" + UUID.randomUUID() + "@example.com";
        var registration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString()).path("accessToken").asText();
        UUID userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
        String nickname = "T" + UUID.randomUUID().toString().substring(0, 7);
        String symbol = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase();

        mockMvc.perform(post("/api/discussions/" + symbol).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"before nickname\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/discussions/" + symbol).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"before nickname\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));
        mockMvc.perform(post("/api/account/nickname").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("nickname", nickname))))
                .andExpect(status().isNoContent());

        String body = "<script>alert(1)</script> market note";
        mockMvc.perform(post("/api/discussions/" + symbol).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("title", "Test title", "content", body, "fontFamily", "system", "fontSize", 16, "textAlign", "left"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.symbol").value(symbol))
                .andExpect(jsonPath("$.nickname").value(nickname)).andExpect(jsonPath("$.content").value(body))
                .andExpect(jsonPath("$.userId").doesNotExist());
        mockMvc.perform(post("/api/discussions/" + symbol + "E").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"Another title\",\"content\":\"different coin\",\"fontFamily\":\"system\",\"fontSize\":16,\"textAlign\":\"left\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/discussions/" + symbol))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").doesNotExist()).andExpect(jsonPath("$.content[0].nickname").value(nickname));
        mockMvc.perform(get("/api/discussions/" + symbol.toLowerCase())).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/discussions/" + symbol).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/discussions/" + symbol).header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("content", "x".repeat(1001)))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(delete("/api/privacy/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/discussions/" + symbol)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject("SELECT count(*) FROM market_discussion_post WHERE user_id = ?", Integer.class, userId)).isZero();
    }
}