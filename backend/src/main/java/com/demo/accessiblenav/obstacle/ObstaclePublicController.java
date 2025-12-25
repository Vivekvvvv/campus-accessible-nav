package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.obstacle.dto.ObstacleReportCreateRequest;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/obstacles")
@Validated
@Tag(name = "障碍物上报", description = "用户上报路障、施工等障碍物信息")
public class ObstaclePublicController {

    private final ObstacleReportService reportService;

    public ObstaclePublicController(ObstacleReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/report")
    @Operation(
            summary = "上报障碍物",
            description = "用户上报路径上的障碍物信息，用于实时更新导航路线"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "上报成功",
                    content = @Content(schema = @Schema(implementation = ObstacleReportDto.class))),
            @ApiResponse(responseCode = "400", description = "请求参数无效")
    })
    public ObstacleReportDto create(
            @Parameter(description = "障碍物上报请求", required = true)
            @RequestBody @Valid ObstacleReportCreateRequest req) {
        return reportService.createReport(req);
    }

    @GetMapping("/reports/me")
    @Operation(summary = "获取我的上报", description = "获取当前登录用户的障碍上报记录（可按状态筛选）")
    public List<ObstacleReportDto> myReports(
            @Parameter(description = "状态筛选（PENDING/APPROVED/REJECTED）")
            @RequestParam(value = "status", required = false) String status) {
        return reportService.listMyReports(status);
    }
}
