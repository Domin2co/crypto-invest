package com.cryptoinvest.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.cryptoinvest.exchange.Exchange;
import com.cryptoinvest.exchange.publicapi.UpbitPublicClient;
import com.cryptoinvest.market.MarketPrice;
import java.time.Instant;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import org.mockito.Answers;
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
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS) UpbitPublicClient upbitPublicClient;
    @MockitoBean EmailVerificationService emailVerificationService;

    @Test
    void exportsOwnConsentAllowsMarketingWithdrawalAndAnonymizesDeletion() throws Exception {
        when(upbitPublicClient.exchange()).thenReturn(Exchange.UPBIT);
        doReturn(new MarketPrice(Exchange.UPBIT, "KRW-BTC", new java.math.BigDecimal("100000"), java.math.BigDecimal.ONE, Instant.now())).when(upbitPublicClient).getPrice("KRW-BTC");
        String email = "privacy-" + UUID.randomUUID() + "@example.com";
        var registration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true, "marketingAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString()).path("accessToken").asText();
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
        String nickname = "N" + UUID.randomUUID().toString().substring(0, 7);
        mockMvc.perform(get("/api/paper/orders/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("NICKNAME_REQUIRED"));
        mockMvc.perform(get("/api/account/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.nicknameRequired").value(true));
        mockMvc.perform(get("/api/account/nickname/availability").param("nickname", "ㄱa").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(get("/api/account/nickname/availability").param("nickname", "A B").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(get("/api/account/nickname/availability").param("nickname", nickname).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.valid").value(true)).andExpect(jsonPath("$.available").value(true));
        mockMvc.perform(post("/api/account/nickname").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"ㄱㄱ\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/account/nickname").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("nickname", nickname))))
                .andExpect(status().isNoContent());
        String secondEmail = "nickname-" + UUID.randomUUID() + "@example.com";
        var secondRegistration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", secondEmail, "password", "Good-pass1!", "privacyAccepted", true, "marketingAccepted", false))))
                .andExpect(status().isOk()).andReturn();
        String secondToken = objectMapper.readTree(secondRegistration.getResponse().getContentAsString()).path("accessToken").asText();
        mockMvc.perform(get("/api/account/nickname/availability").param("nickname", nickname.toLowerCase(java.util.Locale.ROOT)).header("Authorization", "Bearer " + secondToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.available").value(false));
        mockMvc.perform(post("/api/account/nickname").header("Authorization", "Bearer " + secondToken).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of("nickname", nickname.toLowerCase(java.util.Locale.ROOT)))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("NICKNAME_TAKEN"));

        mockMvc.perform(get("/api/privacy/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.consents.length()").value(2));
        mockMvc.perform(post("/api/paper/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "exchange", "UPBIT", "symbol", "BTC", "side", "BUY", "amount", 10000,
                                "idempotencyKey", "paper-" + UUID.randomUUID()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("FILLED"))
                .andExpect(jsonPath("$.exchange").value("UPBIT")).andExpect(jsonPath("$.price").value(100000))
                .andExpect(jsonPath("$.quantity").value(0.1));
        mockMvc.perform(post("/api/paper/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "exchange", "UPBIT", "symbol", "BTC", "side", "SELL", "amount", 1,
                                "quantity", 0.1, "idempotencyKey", "paper-sell-" + UUID.randomUUID()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(10000))
                .andExpect(jsonPath("$.price").value(100000));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM trade_order WHERE user_id = ? AND trading_mode = 'PAPER'", Integer.class,
                userId)).isEqualTo(2);
        mockMvc.perform(get("/api/paper/orders/summary").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.wallets.length()").value(3))
                .andExpect(jsonPath("$.orders.length()").value(2));
        mockMvc.perform(post("/api/paper/orders").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(Map.of(
                                "exchange", "UPBIT", "symbol", "BTC", "side", "BUY", "amount", 1000000,
                                "idempotencyKey", "insufficient-" + UUID.randomUUID()))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_STATE"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM trade_order WHERE user_id = ? AND trading_mode = 'PAPER'", Integer.class,
                userId)).isEqualTo(2);
        mockMvc.perform(patch("/api/privacy/marketing-consent").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":false}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/live-trading/confirm").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":false}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/live-trading/confirm").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE event_type = 'LIVE_TRADING_CONFIRMED'", Integer.class)).isEqualTo(1);
        jdbcTemplate.update("INSERT INTO portfolio_target (user_id, exchange, currency, target_weight) VALUES (?, 'UPBIT', 'BTC', 0.35)", userId);
        mockMvc.perform(delete("/api/privacy/me").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM app_user WHERE email = ?", Integer.class, email)).isZero();
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM portfolio_target WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    void requiresSeparateLeaderboardConsentAndDeletesPublicParticipationOnWithdrawal() throws Exception {
        when(upbitPublicClient.exchange()).thenReturn(Exchange.UPBIT);
        String email = "league-" + UUID.randomUUID() + "@example.com";
        var registration = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true, "marketingAccepted", false))))
                .andExpect(status().isOk()).andReturn();
        String token = objectMapper.readTree(registration.getResponse().getContentAsString()).path("accessToken").asText();
        UUID userId = jdbcTemplate.queryForObject("SELECT id FROM app_user WHERE email = ?", UUID.class, email);
        String nickname = "L" + UUID.randomUUID().toString().substring(0, 6);
        mockMvc.perform(post("/api/account/nickname").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", nickname))))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/paper-league/entry").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.publicConsent").value(false));
        mockMvc.perform(post("/api/paper-league/entry").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        mockMvc.perform(patch("/api/privacy/paper-leaderboard-consent").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/paper-league/entry").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enrolled").value(true));
        mockMvc.perform(get("/api/paper-league"))
                .andExpect(status().isOk()).andExpect(jsonPath("$..email").doesNotExist());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM paper_league_entry WHERE user_id = ?", Integer.class, userId)).isEqualTo(1);
        mockMvc.perform(patch("/api/privacy/paper-leaderboard-consent").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"accepted\":false}"))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM paper_league_entry WHERE user_id = ?", Integer.class, userId)).isZero();
    }
}
