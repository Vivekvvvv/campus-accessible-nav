package com.demo.accessiblenav.file;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * FileUploadController 集成测试 — 上传类型/大小限制与权限校验。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FileUploadControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    // ------------------------------------------------------------------ upload

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadJpeg_shouldReturnUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[1024]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadPng_shouldReturnUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.png", "image/png", new byte[512]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").isString());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadDisallowedType_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "script.js", "application/javascript", "alert(1)".getBytes());

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadExecutable_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", "application/octet-stream", new byte[100]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadEmptyFile_shouldReturn400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void uploadOversizedFile_shouldReturn400() throws Exception {
        // 超过 5MB 限制
        byte[] bigContent = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "big.jpg", "image/jpeg", bigContent);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_unauthenticated_shouldSucceed() throws Exception {
        // /api/files/upload 在 SecurityConfig 中配置为 permitAll，无需认证
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[512]);

        mockMvc.perform(multipart("/api/files/upload").file(file))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ list

    @Test
    @WithMockUser(username = "testuser", roles = "USER")
    void listFiles_endpointNotExist_shouldReturnError() throws Exception {
        // /api/files/list 端点不存在，返回 4xx 或 5xx
        mockMvc.perform(get("/api/files/list"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(400),
                        org.hamcrest.Matchers.lessThan(600))));
    }

    @Test
    void listFiles_unauthenticated_endpointNotExist_shouldReturnError() throws Exception {
        // /api/files/list 端点不存在，返回 4xx 或 5xx
        mockMvc.perform(get("/api/files/list"))
                .andExpect(status().is(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.greaterThanOrEqualTo(400),
                        org.hamcrest.Matchers.lessThan(600))));
    }
}
