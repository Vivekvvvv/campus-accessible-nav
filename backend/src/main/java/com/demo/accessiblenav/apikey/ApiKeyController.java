package com.demo.accessiblenav.apikey;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/api-keys")
@Tag(name = "API Key 管理", description = "管理开放 API 密钥（管理员接口）")
public class ApiKeyController {

    private final ApiKeyService apiKeyService;
    private final AdminPermissionService permissionService;

    public ApiKeyController(ApiKeyService apiKeyService, AdminPermissionService permissionService) {
        this.apiKeyService = apiKeyService;
        this.permissionService = permissionService;
    }

    @PostMapping
    @Operation(summary = "创建 API Key")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        permissionService.requireAny(UserRole.ADMIN);
        String ownerId = (String) body.getOrDefault("ownerId", "system");
        String scopes = (String) body.getOrDefault("scopes", "route:read");
        int rateLimit = body.containsKey("rateLimitPerMinute")
                ? ((Number) body.get("rateLimitPerMinute")).intValue() : 60;

        ApiKeyService.ApiKeyCreateResult result = apiKeyService.create(ownerId, scopes, rateLimit);
        return Map.of(
                "keyId", result.keyId(),
                "secret", result.secret(),
                "scopes", result.entity().getScopes(),
                "rateLimitPerMinute", result.entity().getRateLimitPerMinute()
        );
    }

    @GetMapping
    @Operation(summary = "列出所有 API Key")
    public List<Map<String, Object>> list(@RequestParam(required = false) String ownerId) {
        permissionService.requireAny(UserRole.ADMIN);
        List<ApiKeyEntity> keys;
        if (ownerId != null) {
            keys = apiKeyService.listByOwner(ownerId);
        } else {
            keys = apiKeyService.listByTenant(
                    com.demo.accessiblenav.tenant.TenantContext.get());
        }
        return keys.stream().map(k -> Map.<String, Object>of(
                "id", k.getId(),
                "keyId", k.getKeyId(),
                "ownerId", k.getOwnerId(),
                "scopes", k.getScopes(),
                "rateLimitPerMinute", k.getRateLimitPerMinute(),
                "active", k.isActive(),
                "createdAt", k.getCreatedAt().toString()
        )).toList();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "停用 API Key")
    public Map<String, String> deactivate(@PathVariable Long id) {
        permissionService.requireAny(UserRole.ADMIN);
        apiKeyService.deactivate(id);
        return Map.of("status", "deactivated");
    }
}
