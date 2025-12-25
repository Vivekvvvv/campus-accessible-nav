package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.auth.dto.AuthResponse;
import com.demo.accessiblenav.auth.dto.LoginRequest;
import com.demo.accessiblenav.auth.dto.RefreshTokenRequest;
import com.demo.accessiblenav.auth.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Validated
@Tag(name = "用户认证", description = "用户注册、登录和令牌刷新")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名和密码登录，返回访问令牌和刷新令牌")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "登录成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误")
    })
    public AuthResponse login(
            @Parameter(description = "登录请求", required = true)
            @RequestBody @Valid LoginRequest req) {
        return authService.login(req.getUsername(), req.getPassword());
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户，返回访问令牌和刷新令牌")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "注册成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "用户名已存在或参数无效")
    })
    public AuthResponse register(
            @Parameter(description = "注册请求", required = true)
            @RequestBody @Valid RegisterRequest req) {
        return authService.register(req.getUsername(), req.getPassword());
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新令牌", description = "使用刷新令牌获取新的访问令牌和刷新令牌")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "刷新成功",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "刷新令牌无效或已过期")
    })
    public AuthResponse refreshToken(
            @Parameter(description = "刷新令牌请求", required = true)
            @RequestBody @Valid RefreshTokenRequest req) {
        return authService.refreshToken(req.getRefreshToken());
    }
}
