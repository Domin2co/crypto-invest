package com.cryptoinvest.market;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MarketDiscussionInteractionsIT {
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
    void listDetailCommentsSingleVoteAndBlindAtTwentyOneDownvotes() throws Exception {
        UUID userId = UUID.randomUUID();
        String email = "discussion-interaction-" + userId + "@example.com";
        var registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(registered.getResponse().getContentAsString()).path("accessToken").asText();
        userId = jdbc.queryForObject("SELECT id FROM app_user WHERE email=?", UUID.class, email);
        jdbc.update("UPDATE app_user SET nickname=? WHERE id=?", "Board01", userId);
        String symbol = "BTC";
        byte[] imageBytes = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00, 0x00, 0x00};
        var postResponse = mockMvc.perform(post("/api/discussions/" + symbol).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("title", "Discussion title", "content", "Full body", "fontFamily", "serif", "fontSize", 18, "textAlign", "center", "imageType", "image/png", "imageData", imageBytes))))
                .andExpect(status().isCreated()).andReturn();
        UUID postId = UUID.fromString(mapper.readTree(postResponse.getResponse().getContentAsString()).path("id").asText());

        mockMvc.perform(get("/api/discussions/" + symbol))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].title").value("Discussion title"))
                .andExpect(jsonPath("$.content[0].number").value(1)).andExpect(jsonPath("$.content[0].upVotes").value(0))
                .andExpect(jsonPath("$.content[0].content").doesNotExist()).andExpect(jsonPath("$.content[0].fontFamily").doesNotExist());
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Full body")).andExpect(jsonPath("$.blind").value(false))
                .andExpect(jsonPath("$.hasImage").value(true));
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId + "/image")).andExpect(status().isOk());

        mockMvc.perform(post("/api/discussions/" + symbol + "/" + postId + "/vote").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"UP\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upVotes").value(1));
        mockMvc.perform(post("/api/discussions/" + symbol + "/" + postId + "/vote").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"direction\":\"DOWN\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.upVotes").value(0)).andExpect(jsonPath("$.downVotes").value(1));
        mockMvc.perform(post("/api/discussions/" + symbol + "/" + postId + "/comments").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"A plain text comment\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId + "/comments?page=0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].content").value("A plain text comment"));

        for (int i = 0; i < 20; i++) {
            UUID voter = UUID.randomUUID();
            jdbc.update("INSERT INTO app_user (id,email,password_hash,enabled,nickname) VALUES (?,?,?,TRUE,?)",
                    voter, "vote-" + voter + "@example.com", "unused", "V" + String.format("%07d", i));
            jdbc.update("INSERT INTO market_discussion_vote (post_id,user_id,vote) VALUES (?,?, -1)", postId, voter);
        }
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.downVotes").value(21))
                .andExpect(jsonPath("$.blind").value(true)).andExpect(jsonPath("$.content").doesNotExist());
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId + "/image")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId + "?reveal=true"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Full body"));
        mockMvc.perform(get("/api/discussions/" + symbol + "/" + postId + "/image?reveal=true")).andExpect(status().isOk());
    }

    @Test
    void commentPagingAndOwnerOnlyEditDeleteAreEnforced() throws Exception {
        String owner = register("discussion-owner-" + UUID.randomUUID() + "@example.com");
        String other = register("discussion-other-" + UUID.randomUUID() + "@example.com");
        UUID postId = createPost(owner, "Editable discussion");
        for (int i = 0; i < 11; i++) {
            mockMvc.perform(post("/api/discussions/BTC/" + postId + "/comments").header("Authorization", "Bearer " + owner)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("content", "Comment " + i))))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/discussions/BTC/" + postId + "/comments?page=0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(10)).andExpect(jsonPath("$.totalPages").value(2));
        var page = mockMvc.perform(get("/api/discussions/BTC/" + postId + "/comments?page=1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andReturn();
        UUID lastComment = UUID.fromString(mapper.readTree(page.getResponse().getContentAsString()).path("content").get(0).path("id").asText());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/discussions/BTC/" + postId + "/comments/" + lastComment)
                        .header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Not mine\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/discussions/BTC/" + postId + "/comments/" + lastComment)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Edited comment\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/discussions/BTC/" + postId)
                        .header("Authorization", "Bearer " + other).contentType(MediaType.APPLICATION_JSON)
                        .content(postRequest("Hijacked title")))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/discussions/BTC/" + postId)
                        .header("Authorization", "Bearer " + owner).contentType(MediaType.APPLICATION_JSON)
                        .content(postRequest("Updated title")))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/discussions/BTC/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Updated title"));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/discussions/BTC/" + postId)
                        .header("Authorization", "Bearer " + owner)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/discussions/BTC/" + postId)).andExpect(status().isNotFound());
    }

    @Test
    void onlyAdminsCanHideReportedPostsAndRestoreThem() throws Exception {
        String owner = register("discussion-post-" + UUID.randomUUID() + "@example.com");
        String reporter = register("discussion-reporter-" + UUID.randomUUID() + "@example.com");
        String admin = register("discussion-admin-" + UUID.randomUUID() + "@example.com");
        UUID adminId = jdbc.queryForObject("SELECT id FROM app_user WHERE email LIKE 'discussion-admin-%@example.com' ORDER BY created_at DESC LIMIT 1", UUID.class);
        jdbc.update("UPDATE app_user SET role='ADMIN' WHERE id=?", adminId);
        UUID postId = createPost(owner, "Reported discussion");
        mockMvc.perform(post("/api/discussions/BTC/" + postId + "/reports").header("Authorization", "Bearer " + reporter)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SPAM\",\"details\":\"Repeated links\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/discussion-reports").header("Authorization", "Bearer " + reporter)).andExpect(status().isForbidden());
        var reportPage = mockMvc.perform(get("/api/admin/discussion-reports").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("PENDING")).andReturn();
        UUID reportId = UUID.fromString(mapper.readTree(reportPage.getResponse().getContentAsString()).path("content").get(0).path("id").asText());
        String reportPath = "/api/admin/discussion-reports/" + reportId;
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(reportPath).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"HIDE\",\"reason\":\"Personal data exposure\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discussions/BTC/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.hidden").value(true)).andExpect(jsonPath("$.content").doesNotExist());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(reportPath).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"RESTORE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/discussions/BTC/" + postId)).andExpect(status().isOk()).andExpect(jsonPath("$.content").value("Full body"));
    }

    private String register(String email) throws Exception {
        var response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", "Good-pass1!", "privacyAccepted", true))))
                .andExpect(status().isOk()).andReturn();
        UUID id = jdbc.queryForObject("SELECT id FROM app_user WHERE email=?", UUID.class, email);
        jdbc.update("UPDATE app_user SET nickname=? WHERE id=?", "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 7), id);
        return mapper.readTree(response.getResponse().getContentAsString()).path("accessToken").asText();
    }

    private UUID createPost(String token, String title) throws Exception {
        var response = mockMvc.perform(post("/api/discussions/BTC").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(postRequest(title))).andExpect(status().isCreated()).andReturn();
        return UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("id").asText());
    }

    private String postRequest(String title) throws Exception {
        return mapper.writeValueAsString(Map.of("title", title, "content", "Full body", "fontFamily", "system", "fontSize", 16, "textAlign", "left"));
    }
}


