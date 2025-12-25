package com.demo.accessiblenav.admin;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "管理员", description = "管理员基础接口")
public class AdminController {

    private final AdminPermissionService permissionService;

    public AdminController(AdminPermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/ping")
    @Operation(summary = "健康检查", description = "检查管理员接口是否可用")
    @ApiResponse(responseCode = "200", description = "服务正常")
    public Map<String, String> ping() {
        return Collections.singletonMap("status", "ok");
    }

    @GetMapping("/profile")
    @Operation(summary = "获取当前用户信息", description = "获取当前登录管理员的用户名和角色")
    @ApiResponse(responseCode = "200", description = "获取成功")
    public Map<String, String> profile() {
        Map<String, String> data = new HashMap<>();
        data.put("username", permissionService.currentUsername());
        UserRole role = permissionService.currentRole();
        data.put("role", role == null ? "" : role.name());
        return data;
    }
}
