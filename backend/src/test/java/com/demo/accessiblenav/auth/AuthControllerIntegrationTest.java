package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.auth.dto.LoginRequest;
import com.demo.accessiblenav.auth.dto.RefreshTokenRequest;
import com.demo.accessiblenav.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AuthController 集成测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("用户注册应该成功")
    void registerShouldSucceed() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser" + System.currentTimeMillis());
        request.setPassword("StrongPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value(request.getUsername()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.accessTokenExpiresIn").isNumber())
                .andExpect(jsonPath("$.refreshTokenExpiresIn").isNumber());
    }

    @Test
    @DisplayName("用户名过短应该返回验证错误")
    void shortUsernameShouldFail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("ab");  // 少于3个字符
        request.setPassword("StrongPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("密码过短应该返回验证错误")
    void shortPasswordShouldFail() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("validuser");
        request.setPassword("12345");  // 少于6个字符

        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("登录成功应该返回令牌对")
    void loginShouldReturnTokenPair() throws Exception {
        // 先注册用户
        String username = "loginuser" + System.currentTimeMillis();
        String password = "StrongPass123";

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword(password);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(registerRequest))))
                .andExpect(status().isOk());

        // 然后登录
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword(password);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("错误密码应该返回认证错误")
    void wrongPasswordShouldFail() throws Exception {
        // 先注册用户
        String username = "wrongpassuser" + System.currentTimeMillis();

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword("StrongPass123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(registerRequest))))
                .andExpect(status().isOk());

        // 使用错误密码登录
        LoginRequest loginRequest = new LoginRequest();
        loginRequest.setUsername(username);
        loginRequest.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(loginRequest))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_003"));
    }

    @Test
    @DisplayName("刷新令牌应该返回新的令牌对")
    void refreshTokenShouldReturnNewTokenPair() throws Exception {
        // 先注册用户获取令牌
        String username = "refreshuser" + System.currentTimeMillis();

        RegisterRequest registerRequest = new RegisterRequest();
        registerRequest.setUsername(username);
        registerRequest.setPassword("StrongPass123");

        MvcResult registerResult = mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(registerRequest))))
                .andExpect(status().isOk())
                .andReturn();

        // 提取刷新令牌
        String responseJson = registerResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseJson).get("refreshToken").asText();

        // 使用刷新令牌获取新令牌
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(refreshRequest))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").exists())
                .andExpect(jsonPath("$.username").value(username));
    }

    @Test
    @DisplayName("无效刷新令牌应该返回错误")
    void invalidRefreshTokenShouldFail() throws Exception {
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest("invalid.refresh.token");

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(refreshRequest))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("重复注册相同用户名应该失败")
    void duplicateUsernameShouldFail() throws Exception {
        String username = "duplicateuser" + System.currentTimeMillis();

        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setPassword("StrongPass123");

        // 第一次注册应该成功
        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isOk());

        // 第二次注册应该失败
        mockMvc.perform(post("/api/auth/register")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AUTH_002"));
    }
}
