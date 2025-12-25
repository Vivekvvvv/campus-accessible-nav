package com.demo.accessiblenav.route;

import com.demo.accessiblenav.audit.OperationLogRepository;
import com.demo.accessiblenav.route.dto.RoutePassabilityPolicyUpdateRequest;
import com.demo.accessiblenav.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RouteWeightAdminControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RoutePassabilityPolicyRepository policyRepository;

    @Autowired
    OperationLogRepository operationLogRepository;

    @BeforeEach
    void reset() {
        policyRepository.deleteAll();
        operationLogRepository.deleteAll();
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void getPolicy_shouldReturnDefaults_whenNoTenantPolicyExists() throws Exception {
        mockMvc.perform(get("/api/admin/route/weights")
                        .header("X-Tenant-ID", "t1")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("t1"))
                .andExpect(jsonPath("$.passabilityPenaltyEnabled").value(true))
                .andExpect(jsonPath("$.passabilityMinClamp").value(0.01))
                .andExpect(jsonPath("$.passabilityWeightFactor").value(1.0));
    }

    @Test
    void updatePolicy_shouldPersistTenantScopedPolicy_andRecordAudit() throws Exception {
        RoutePassabilityPolicyUpdateRequest request = new RoutePassabilityPolicyUpdateRequest();
        request.setPassabilityPenaltyEnabled(false);
        request.setPassabilityMinClamp(0.08);
        request.setPassabilityWeightFactor(1.5);

        mockMvc.perform(put("/api/admin/route/weights")
                        .header("X-Tenant-ID", "tenant-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("editor").roles("EDITOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.passabilityPenaltyEnabled").value(false))
                .andExpect(jsonPath("$.passabilityMinClamp").value(0.08))
                .andExpect(jsonPath("$.passabilityWeightFactor").value(1.5))
                .andExpect(jsonPath("$.updatedBy").value("editor"));

        mockMvc.perform(get("/api/admin/route/weights")
                        .header("X-Tenant-ID", "tenant-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-a"))
                .andExpect(jsonPath("$.passabilityPenaltyEnabled").value(false))
                .andExpect(jsonPath("$.passabilityMinClamp").value(0.08))
                .andExpect(jsonPath("$.passabilityWeightFactor").value(1.5));

        mockMvc.perform(get("/api/admin/route/weights")
                        .header("X-Tenant-ID", "tenant-b")
                        .with(SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant-b"))
                .andExpect(jsonPath("$.passabilityPenaltyEnabled").value(true));
    }

    @Test
    void updatePolicy_shouldRejectViewerRole() throws Exception {
        RoutePassabilityPolicyUpdateRequest request = new RoutePassabilityPolicyUpdateRequest();
        request.setPassabilityWeightFactor(1.2);

        mockMvc.perform(put("/api/admin/route/weights")
                        .header("X-Tenant-ID", "tenant-a")
                        .with(SecurityMockMvcRequestPostProcessors.user("viewer").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
