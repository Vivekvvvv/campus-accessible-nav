package com.demo.accessiblenav.profile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AccessibilityProfileControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AccessibilityProfileRepository repository;

    @BeforeEach
    void reset() {
        repository.deleteAll();
    }

    @Test
    void getWithoutAuth_shouldBeRejected() throws Exception {
        mockMvc.perform(get("/api/profile/accessibility"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void get_shouldCreateDefaultProfileForCurrentUser() throws Exception {
        mockMvc.perform(get("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mobilityMode").value("WALK"))
                .andExpect(jsonPath("$.data.avoidStairs").value(false))
                .andExpect(jsonPath("$.data.avoidSlope").value(false))
                .andExpect(jsonPath("$.data.avoidConstruction").value(true))
                .andExpect(jsonPath("$.data.maxSlopePercent").value(12.0));
    }

    @Test
    void put_thenGet_shouldPersistPerUser() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobilityMode", "WHEELCHAIR");
        payload.put("avoidStairs", true);
        payload.put("avoidSlope", true);
        payload.put("avoidConstruction", true);
        payload.put("maxSlopePercent", 8.5);

        mockMvc.perform(put("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.avoidStairs").value(true))
                .andExpect(jsonPath("$.data.avoidSlope").value(true))
                .andExpect(jsonPath("$.data.avoidConstruction").value(true))
                .andExpect(jsonPath("$.data.maxSlopePercent").value(8.5));

        mockMvc.perform(get("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WHEELCHAIR"));

        mockMvc.perform(get("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u2").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WALK"));
    }
}
