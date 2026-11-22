package com.example.server_components;

import com.example.server_components.model.User;
import com.example.server_components.repository.CategoryRepository;
import com.example.server_components.repository.ExpenseRecordRepository;
import com.example.server_components.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityAndRbacIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ExpenseRecordRepository recordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        recordRepository.deleteAll();
        categoryRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void publicEndpointsShouldWorkWithoutToken() throws Exception {
        mockMvc.perform(get("/healthz/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("alive"));

        mockMvc.perform(get("/healthz/ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"));
    }

    @Test
    void protectedEndpointWithoutTokenShouldReturn401() throws Exception {
        mockMvc.perform(get("/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthorized"));
    }

    @Test
    void registerShouldHashPasswordWithArgon2AndLoginShouldIssueJwt() throws Exception {
        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"artem\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("artem"))
                .andExpect(jsonPath("$.role").value("user"));

        User saved = userRepository.findByName("artem").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$argon2");

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"artem\",\"password\":\"wrongPass\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"artem\",\"password\":\"strongPass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.token_type").value("Bearer"));
    }

    @Test
    void rbacMatrixShouldEnforceUserAuditorAndAdminPermissions() throws Exception {
        User user1 = userRepository.save(new User("user1", passwordEncoder.encode("pass1"), "user"));
        User user2 = userRepository.save(new User("user2", passwordEncoder.encode("pass2"), "user"));
        userRepository.save(new User("auditor1", passwordEncoder.encode("pass3"), "auditor"));
        userRepository.save(new User("admin1", passwordEncoder.encode("pass4"), "admin"));

        String user1Token = loginAndGetToken("user1", "pass1");
        String user2Token = loginAndGetToken("user2", "pass2");
        String auditorToken = loginAndGetToken("auditor1", "pass3");
        String adminToken = loginAndGetToken("admin1", "pass4");

        // 1. User не має доступу до адмін-ендпоінтів (403) і чужих профілів (404)
        mockMvc.perform(get("/users").header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/category/global")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"GlobalFood\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/user/" + user2.getId()).header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isNotFound());

        // 2. Admin створює глобальну категорію (201)
        MvcResult globalCatRes = mockMvc.perform(post("/category/global")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"GlobalFood\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long globalCatId = objectMapper.readTree(globalCatRes.getResponse().getContentAsString()).get("id").asLong();

        // 3. User1 створює витрату, Auditor може переглядати, але не може створювати чи видаляти (403)
        MvcResult recordRes = mockMvc.perform(post("/record")
                        .header("Authorization", "Bearer " + user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"user_id\":" + user1.getId() + ",\"category_id\":" + globalCatId + ",\"amount\":120.50}"))
                .andExpect(status().isCreated())
                .andReturn();
        long recordId = objectMapper.readTree(recordRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/record/" + recordId).header("Authorization", "Bearer " + user2Token))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/record/" + recordId).header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/record/" + recordId).header("Authorization", "Bearer " + auditorToken))
                .andExpect(status().isForbidden());

        // 4. Admin може видалити запис (204)
        mockMvc.perform(delete("/record/" + recordId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("access_token").asText();
    }
}