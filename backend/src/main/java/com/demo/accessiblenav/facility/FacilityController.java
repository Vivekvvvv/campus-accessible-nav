package com.demo.accessiblenav.facility;

import com.demo.accessiblenav.facility.dto.FacilityDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;

/**
 * 无障碍设施 API
 */
@RestController
@RequestMapping("/api/facilities")
@Tag(name = "无障碍设施", description = "无障碍设施信息查询和搜索")
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping("/nearby")
    @Operation(summary = "查找附近设施", description = "根据经纬度和搜索半径查找附近的无障碍设施")
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<FacilityDto> findNearby(
            @Parameter(description = "纬度", required = true, example = "23.274")
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @Parameter(description = "经度", required = true, example = "113.203")
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lng,
            @Parameter(description = "搜索半径（米）", example = "500")
            @RequestParam(defaultValue = "500") @Min(10) @Max(2000) int radiusMeters,
            @Parameter(description = "设施类型（可选）")
            @RequestParam(required = false) FacilityType type
    ) {
        return facilityService.findNearby(lat, lng, radiusMeters, type);
    }

    @GetMapping("/building/{buildingName}")
    @Operation(summary = "按建筑查找设施", description = "根据建筑名称查找该建筑内的所有无障碍设施")
    public List<FacilityDto> findByBuilding(
            @Parameter(description = "建筑名称", required = true)
            @PathVariable String buildingName) {
        return facilityService.findByBuilding(buildingName);
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "按类型查找设施", description = "根据设施类型查找所有匹配的设施")
    public List<FacilityDto> findByType(
            @Parameter(description = "设施类型", required = true)
            @PathVariable FacilityType type) {
        return facilityService.findByType(type);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取设施详情", description = "根据ID获取单个设施的详细信息")
    @ApiResponse(responseCode = "200", description = "查询成功")
    @ApiResponse(responseCode = "404", description = "设施不存在")
    public FacilityDto getById(
            @Parameter(description = "设施ID", required = true)
            @PathVariable Long id) {
        return facilityService.getById(id);
    }

    @GetMapping("/buildings")
    @Operation(summary = "获取建筑列表", description = "获取所有有无障碍设施的建筑名称列表")
    public List<String> getAllBuildingNames() {
        return facilityService.getAllBuildingNames();
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取统计信息", description = "获取无障碍设施的统计信息，包括各类型数量等")
    public Map<String, Object> getStatistics() {
        return facilityService.getStatistics();
    }

    @GetMapping("/types")
    @Operation(summary = "获取设施类型", description = "获取所有可用的设施类型及其描述")
    public List<Map<String, String>> getAllFacilityTypes() {
        return facilityService.getAllFacilityTypes();
    }
}
