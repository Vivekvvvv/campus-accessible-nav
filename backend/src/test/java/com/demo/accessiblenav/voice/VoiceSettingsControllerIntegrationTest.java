package com.demo.accessiblenav.voice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * VoiceSettings Controller 集成测试 — CRUD 与边界值校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class VoiceSettingsControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    // ------------------------------------------------------------------ GET

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void getSettings_shouldReturnDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/voice-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    void getSettings_unauthenticated_shouldReturn401or403() throws Exception {
        mockMvc.perform(get("/api/v1/voice-settings"))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ PUT happy path

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_validPayload_shouldReturnUpdated() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setRate(1.5);
        dto.setPitch(1.0);
        dto.setVolume(0.8);
        dto.setEnabled(true);
        dto.setLang("zh-CN");

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate", is(1.5)))
                .andExpect(jsonPath("$.data.enabled", is(true)));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_partial_shouldMergeWithDefaults() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setEnabled(false);

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled", is(false)));
    }

    // ------------------------------------------------------------------ PUT validation

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_rateAboveMax_shouldReturn400() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setRate(99.0); // max is 10

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_volumeAboveMax_shouldReturn400() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setVolume(2.0); // max is 1

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_negativePitch_shouldReturn400() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setPitch(-1.0); // min is 0

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void updateSettings_preTurnMAboveMax_shouldReturn400() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setPreTurnM(999.0); // max is 500

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateSettings_unauthenticated_shouldReturn401or403() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setEnabled(true);

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ GET after PUT

    @Test
    @WithMockUser(username = "persistuser", roles = "USER")
    void updateThenGet_shouldReflectChanges() throws Exception {
        VoiceSettingsDto dto = new VoiceSettingsDto();
        dto.setRate(2.0);
        dto.setVolume(0.5);
        dto.setEnabled(true);

        mockMvc.perform(put("/api/v1/voice-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/voice-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate", is(2.0)))
                .andExpect(jsonPath("$.data.volume", is(0.5)));
    }
}
