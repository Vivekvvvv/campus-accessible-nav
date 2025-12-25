package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.obstacle.dto.EdgeDisableRequest;
import com.demo.accessiblenav.obstacle.dto.EdgeDisableResponse;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportDto;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportReviewRequest;
import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/obstacles")
@Validated
@Tag(name = "障碍物管理", description = "障碍物上报审核和路径禁用管理（管理员接口）")
public class ObstacleAdminController {

    private final ObstacleAdminService adminService;
    private final ObstacleReportService reportService;
    private final AdminPermissionService permissionService;

    public ObstacleAdminController(ObstacleAdminService adminService,
                                   ObstacleReportService reportService,
                                   AdminPermissionService permissionService) {
        this.adminService = adminService;
        this.reportService = reportService;
        this.permissionService = permissionService;
    }

    @PostMapping("/disable-edge")
    @Operation(summary = "禁用路径", description = "禁用指定的路径边，使其不参与路线计算")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "禁用成功"),
            @ApiResponse(responseCode = "404", description = "路径不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public EdgeDisableResponse disableEdge(@RequestBody @Valid EdgeDisableRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        return adminService.disableEdge(req);
    }

    @PostMapping("/enable-edge")
    @Operation(summary = "启用路径", description = "重新启用被禁用的路径边")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "启用成功"),
            @ApiResponse(responseCode = "404", description = "路径不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public EdgeDisableResponse enableEdge(@RequestBody @Valid EdgeDisableRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        return adminService.enableEdge(req);
    }

    @GetMapping("/reports")
    @Operation(summary = "获取障碍物上报列表", description = "获取所有障碍物上报记录，可按状态筛选")
    public List<ObstacleReportDto> listReports(
            @Parameter(description = "状态筛选（PENDING/APPROVED/REJECTED）")
            @RequestParam(value = "status", required = false) String status) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        return reportService.listReports(status);
    }

    @PostMapping("/reports/{id}/approve")
    @Operation(summary = "批准上报", description = "批准障碍物上报，将禁用相关路径")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "批准成功"),
            @ApiResponse(responseCode = "404", description = "上报记录不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ObstacleReportDto approve(
            @Parameter(description = "上报记录ID", required = true)
            @PathVariable("id") Long id,
            @RequestBody(required = false) @Valid ObstacleReportReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        return reportService.approve(id, req);
    }

    @PostMapping("/reports/{id}/reject")
    @Operation(summary = "拒绝上报", description = "拒绝障碍物上报")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "拒绝成功"),
            @ApiResponse(responseCode = "404", description = "上报记录不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ObstacleReportDto reject(
            @Parameter(description = "上报记录ID", required = true)
            @PathVariable("id") Long id,
            @RequestBody(required = false) @Valid ObstacleReportReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        return reportService.reject(id, req);
    }

    @PostMapping("/reports/{id}/revoke")
    @Operation(summary = "撤销上报生效", description = "撤销已生效的障碍上报（将恢复相关路径边）")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "撤销成功"),
            @ApiResponse(responseCode = "404", description = "上报记录不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public ObstacleReportDto revoke(
            @Parameter(description = "上报记录ID", required = true)
            @PathVariable("id") Long id,
            @RequestBody(required = false) @Valid ObstacleReportReviewRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER);
        String note = req == null ? null : req.getReviewNote();
        return reportService.revoke(id, note);
    }
}
