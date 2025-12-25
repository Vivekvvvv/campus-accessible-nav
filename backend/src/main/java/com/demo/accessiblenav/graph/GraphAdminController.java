package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.demo.accessiblenav.graph.dto.GraphImportResponse;
import com.demo.accessiblenav.graph.dto.GraphRepairRequest;
import com.demo.accessiblenav.graph.dto.GraphRepairResponse;
import com.demo.accessiblenav.graph.dto.GraphReplaceRequest;
import com.demo.accessiblenav.graph.dto.GraphSnapshotResponse;
import com.demo.accessiblenav.graph.dto.GraphSnapshotSummary;
import com.demo.accessiblenav.graph.dto.GraphValidationReport;
import com.demo.accessiblenav.graph.dto.GraphVersionDiff;
import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/graph")
@Validated
@Tag(name = "图数据管理", description = "导航图数据导入、验证、修复和版本管理（管理员接口）")
public class GraphAdminController {

    private final GraphImportService graphImportService;
    private final AdminPermissionService permissionService;
    private final GraphVersionDiffService diffService;

    public GraphAdminController(GraphImportService graphImportService,
                                AdminPermissionService permissionService,
                                GraphVersionDiffService diffService) {
        this.graphImportService = graphImportService;
        this.permissionService = permissionService;
        this.diffService = diffService;
    }

    @PostMapping("/import")
    @Operation(summary = "导入图数据", description = "增量导入节点、边和POI数据到导航图")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "导入成功"),
            @ApiResponse(responseCode = "400", description = "数据格式错误"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public GraphImportResponse importGraph(@RequestBody @Valid GraphImportRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);
        return graphImportService.importGraph(req);
    }

    @PostMapping("/replace")
    @Operation(summary = "替换图数据", description = "完全替换现有导航图数据")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "替换成功"),
            @ApiResponse(responseCode = "400", description = "数据格式错误"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public GraphImportResponse replaceGraph(@RequestBody @Valid GraphReplaceRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);
        return graphImportService.replaceGraph(req);
    }

    @GetMapping("/snapshot")
    @Operation(summary = "获取图数据快照", description = "获取当前导航图的完整数据快照")
    public GraphSnapshotResponse snapshot() {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        return graphImportService.snapshot();
    }

    @GetMapping("/validate")
    @Operation(summary = "验证图数据", description = "验证当前导航图数据的完整性和一致性")
    public GraphValidationReport validate() {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        return graphImportService.validateGraph();
    }

    @PostMapping("/preview")
    @Operation(summary = "预览导入数据", description = "预览导入数据的验证结果，不实际执行导入")
    public GraphValidationReport preview(@RequestBody @Valid GraphImportRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR);
        return graphImportService.validatePayload(req, true);
    }

    @PostMapping("/repair")
    @Operation(summary = "修复图数据", description = "自动修复导航图中的数据问题")
    public GraphRepairResponse repair(@RequestBody(required = false) GraphRepairRequest req) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);
        return graphImportService.repairGraph(req);
    }

    @GetMapping("/versions")
    @Operation(summary = "获取版本历史", description = "获取导航图的所有历史版本列表")
    public List<GraphSnapshotSummary> versions() {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        return graphImportService.listSnapshots();
    }

    @PostMapping("/rollback/{snapshotId}")
    @Operation(summary = "回滚到指定版本", description = "将导航图回滚到指定的历史版本")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "回滚成功"),
            @ApiResponse(responseCode = "404", description = "指定版本不存在"),
            @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public GraphImportResponse rollback(
            @Parameter(description = "快照ID", required = true)
            @PathVariable Long snapshotId) {
        permissionService.requireAny(UserRole.ADMIN);
        return graphImportService.rollbackSnapshot(snapshotId);
    }

    @GetMapping("/snapshots/{id1}/diff/{id2}")
    @Operation(summary = "对比两个版本", description = "比较两个图快照之间的差异")
    public GraphVersionDiff diffSnapshots(
            @PathVariable Long id1,
            @PathVariable Long id2) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER);
        return diffService.diff(id1, id2);
    }
}
