package com.demo.accessiblenav.audit;

import com.demo.accessiblenav.audit.dto.OperationLogDto;
import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/logs")
@Tag(name = "操作日志", description = "系统操作日志查询和导出（管理员接口）")
public class OperationLogController {

    private final OperationLogService logService;
    private final AdminPermissionService permissionService;

    public OperationLogController(OperationLogService logService, AdminPermissionService permissionService) {
        this.logService = logService;
        this.permissionService = permissionService;
    }

    @GetMapping
    @Operation(summary = "查询操作日志", description = "根据条件查询系统操作日志")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<OperationLogDto> latest(
            @Parameter(description = "返回记录数量限制")
            @RequestParam(value = "limit", required = false) Integer limit,
            @Parameter(description = "操作者用户名筛选")
            @RequestParam(value = "actor", required = false) String actor,
            @Parameter(description = "角色筛选")
            @RequestParam(value = "role", required = false) String role,
            @Parameter(description = "操作类型筛选")
            @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "开始时间（ISO-8601格式）")
            @RequestParam(value = "startAt", required = false) String startAt,
            @Parameter(description = "结束时间（ISO-8601格式）")
            @RequestParam(value = "endAt", required = false) String endAt) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        Instant start = parseInstant(startAt);
        Instant end = parseInstant(endAt);
        return logService.search(actor, role, action, start, end, limit).stream()
                .map(l -> new OperationLogDto(l.getId(), l.getActor(), l.getActorRole(), l.getAction(), l.getDetail(), l.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @GetMapping("/export")
    @Operation(summary = "导出操作日志", description = "导出操作日志为CSV文件")
    @ApiResponse(responseCode = "200", description = "导出成功")
    public ResponseEntity<byte[]> export(
            @Parameter(description = "返回记录数量限制")
            @RequestParam(value = "limit", required = false) Integer limit,
            @Parameter(description = "操作者用户名筛选")
            @RequestParam(value = "actor", required = false) String actor,
            @Parameter(description = "角色筛选")
            @RequestParam(value = "role", required = false) String role,
            @Parameter(description = "操作类型筛选")
            @RequestParam(value = "action", required = false) String action,
            @Parameter(description = "开始时间（ISO-8601格式）")
            @RequestParam(value = "startAt", required = false) String startAt,
            @Parameter(description = "结束时间（ISO-8601格式）")
            @RequestParam(value = "endAt", required = false) String endAt) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        Instant start = parseInstant(startAt);
        Instant end = parseInstant(endAt);
        String csv = logService.exportCsv(logService.search(actor, role, action, start, end, limit));
        byte[] data = csv.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"operation-logs.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(data);
    }

    private Instant parseInstant(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
