package com.demo.accessiblenav.emergency;

import com.demo.accessiblenav.emergency.dto.AddContactRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastRequest;
import com.demo.accessiblenav.emergency.dto.TriggerEmergencyRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Emergency 模块 Controller 集成测试。
 * 使用内嵌 H2 + SpringBootTest，不依赖外部 PostgreSQL。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EmergencyControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    EmergencyEventRepository eventRepository;

    @Autowired
    EmergencyContactRepository contactRepository;

    @AfterEach
    void cleanup() {
        contactRepository.deleteAll();
        eventRepository.deleteAll();
    }

    // ------------------------------------------------------------------ helpers

    private TriggerEmergencyRequest buildTriggerRequest() {
        TriggerEmergencyRequest req = new TriggerEmergencyRequest();
        req.setEventType(EmergencyType.FALL);
        req.setDescription("测试跌倒求助");
        req.setLat(31.23);
        req.setLng(121.47);
        req.setAccuracy(5.0);
        req.setSeverity("HIGH");
        return req;
    }

    private AddContactRequest buildContactRequest() {
        AddContactRequest req = new AddContactRequest();
        req.setContactName("紧急联系人");
        req.setPhoneNumber("13800138000");
        req.setRelationship(ContactRelationship.FAMILY);
        req.setIsPrimary(true);
        return req;
    }

    // ------------------------------------------------------------------ tests

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void triggerEmergency_shouldReturnCreatedEvent() throws Exception {
        mockMvc.perform(post("/api/v1/emergency/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildTriggerRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void triggerEmergency_missingType_shouldReturn400() throws Exception {
        TriggerEmergencyRequest req = new TriggerEmergencyRequest();
        req.setLat(31.23);
        req.setLng(121.47);
        // eventType is null -> @NotNull violation
        mockMvc.perform(post("/api/v1/emergency/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void triggerEmergency_missingLatLng_shouldReturn400() throws Exception {
        TriggerEmergencyRequest req = new TriggerEmergencyRequest();
        req.setEventType(EmergencyType.MEDICAL);
        // lat/lng null -> @NotNull violation
        mockMvc.perform(post("/api/v1/emergency/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void triggerEmergency_unauthenticated_shouldReturn401or403() throws Exception {
        mockMvc.perform(post("/api/v1/emergency/trigger")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildTriggerRequest())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void addContact_shouldReturnSavedContact() throws Exception {
        mockMvc.perform(post("/api/v1/emergency/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildContactRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactName", is("紧急联系人")));
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void addContact_missingName_shouldReturn400() throws Exception {
        AddContactRequest req = new AddContactRequest();
        req.setPhoneNumber("13800138000");
        // contactName blank -> @NotBlank violation
        mockMvc.perform(post("/api/v1/emergency/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void listContacts_shouldReturnEmptyInitially() throws Exception {
        mockMvc.perform(get("/api/v1/emergency/contacts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void deleteContact_nonExistent_shouldReturn404or400() throws Exception {
        mockMvc.perform(delete("/api/v1/emergency/contacts/99999"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void publishBroadcast_shouldReturnBroadcastDto() throws Exception {
        EmergencyBroadcastRequest req = new EmergencyBroadcastRequest();
        req.setTargetScope("ALL");
        req.setSeverity("NORMAL");
        req.setMessage("校园应急广播测试消息");

        mockMvc.perform(post("/api/v1/emergency/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message", is("校园应急广播测试消息")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"USER", "ADMIN"})
    void publishBroadcast_blankMessage_shouldReturn400() throws Exception {
        EmergencyBroadcastRequest req = new EmergencyBroadcastRequest();
        req.setTargetScope("ALL");
        req.setSeverity("NORMAL");
        req.setMessage("");
        mockMvc.perform(post("/api/v1/emergency/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void broadcastHistory_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/emergency/broadcast/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void myEvents_shouldReturnList() throws Exception {
        mockMvc.perform(get("/api/v1/emergency/my-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void suggestDispatch_nonExistentEvent_shouldReturn404or400() throws Exception {
        mockMvc.perform(get("/api/v1/emergency/99999/dispatch/suggest"))
                .andExpect(status().is4xxClientError());
    }
}
