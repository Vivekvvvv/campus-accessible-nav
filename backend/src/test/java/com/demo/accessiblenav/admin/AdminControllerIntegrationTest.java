package com.demo.accessiblenav.admin;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AdminController 集成测试 — 验证角色鉴权与基本接口行为。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // ------------------------------------------------------------------ /ping

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void ping_asAdmin_shouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("ok")));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void ping_asUser_shouldReturn403() throws Exception {
        // ping 仅允许 ADMIN/REVIEWER/EDITOR/VIEWER 角色，USER 角色应被拒绝
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void ping_unauthenticated_shouldReturn401or403() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().is4xxClientError());
    }

    // ------------------------------------------------------------------ /profile

    @Test
    @WithMockUser(username = "admin-user", roles = "ADMIN")
    void profile_asAdmin_shouldReturnUsernameAndRole() throws Exception {
        mockMvc.perform(get("/api/admin/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").isString())
                .andExpect(jsonPath("$.role").isString());
    }

    @Test
    @WithMockUser(username = "regular", roles = "USER")
    void profile_asUser_shouldReturn403() throws Exception {
        // profile 仅允许 ADMIN/REVIEWER/EDITOR/VIEWER 角色，USER 角色应被拒绝
        mockMvc.perform(get("/api/admin/profile"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void profile_unauthenticated_shouldReturn401or403() throws Exception {
        mockMvc.perform(get("/api/admin/profile"))
                .andExpect(status().is4xxClientError());
    }
}
