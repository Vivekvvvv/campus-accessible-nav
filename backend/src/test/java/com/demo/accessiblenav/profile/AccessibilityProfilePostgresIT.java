package com.demo.accessiblenav.profile;

import com.demo.accessiblenav.it.PostgresITBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class AccessibilityProfilePostgresIT extends PostgresITBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void shouldPersistAndIsolateByAuthenticatedUser() throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("mobilityMode", "WHEELCHAIR");
        payload.put("avoidStairs", true);
        payload.put("avoidSlope", true);
        payload.put("avoidConstruction", true);
        payload.put("maxSlopePercent", 7.5);

        mockMvc.perform(put("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WHEELCHAIR"));

        mockMvc.perform(get("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.data.maxSlopePercent").value(7.5));

        mockMvc.perform(get("/api/profile/accessibility")
                        .with(SecurityMockMvcRequestPostProcessors.user("u2").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mobilityMode").value("WALK"));
    }
}
