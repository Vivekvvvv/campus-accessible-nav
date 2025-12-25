package com.demo.accessiblenav.auth;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.auth.dto.UpdateRoleRequest;
import com.demo.accessiblenav.auth.dto.UserSummaryDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@Validated
@Tag(name = "用户管理", description = "用户账户管理（管理员接口）")
public class AdminUserController {

    private final UserAccountService userAccountService;
    private final OperationLogService logService;
    private final AdminPermissionService permissionService;

    public AdminUserController(UserAccountService userAccountService,
                               OperationLogService logService,
                               AdminPermissionService permissionService) {
        this.userAccountService = userAccountService;
        this.logService = logService;
        this.permissionService = permissionService;
    }

    @GetMapping
    @Operation(summary = "获取用户列表", description = "获取所有注册用户的列表")
    @ApiResponse(responseCode = "200", description = "获取成功")
    public List<UserSummaryDto> list() {
        permissionService.requireAny(UserRole.ADMIN);
        return userAccountService.findAll().stream()
                .map(u -> new UserSummaryDto(u.getId(), u.getUsername(), u.getRole(), u.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @PutMapping("/{id}/role")
    @Operation(summary = "更新用户角色", description = "修改指定用户的角色权限")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "更新成功"),
            @ApiResponse(responseCode = "404", description = "用户不存在"),
            @ApiResponse(responseCode = "400", description = "不能移除自己的管理员权限")
    })
    public UserSummaryDto updateRole(
            @Parameter(description = "用户ID", required = true)
            @PathVariable("id") Long id,
            @RequestBody @Valid UpdateRoleRequest req) {
        permissionService.requireAny(UserRole.ADMIN);
        String current = permissionService.currentUsername();
        UserAccount target = userAccountService.findById(id);
        if (target == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "user not found");
        }
        if (target.getUsername().equals(current) && req.getRole() != UserRole.ADMIN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "cannot remove own admin role");
        }
        UserAccount updated = userAccountService.updateRole(id, req.getRole());
        logService.log("USER_ROLE_UPDATED", "user=" + updated.getUsername() + ", role=" + updated.getRole());
        return new UserSummaryDto(updated.getId(), updated.getUsername(), updated.getRole(), updated.getCreatedAt());
    }
}
