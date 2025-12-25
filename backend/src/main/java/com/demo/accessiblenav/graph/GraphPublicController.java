package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphSnapshotResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/graph")
@Tag(name = "图数据", description = "校园导航图数据查询接口")
public class GraphPublicController {

    private final GraphImportService graphImportService;
    private final GraphBootstrapState bootstrapState;

    public GraphPublicController(GraphImportService graphImportService, GraphBootstrapState bootstrapState) {
        this.graphImportService = graphImportService;
        this.bootstrapState = bootstrapState;
    }

    @GetMapping("/snapshot")
    @Operation(
            summary = "获取图数据快照",
            description = "获取当前导航图的完整快照，包含所有节点、边和POI信息"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "获取成功",
                    content = @Content(schema = @Schema(implementation = GraphSnapshotResponse.class))),
            @ApiResponse(responseCode = "503", description = "图数据正在初始化中")
    })
    public GraphSnapshotResponse snapshot() {
        if (!bootstrapState.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "graph initializing");
        }
        return graphImportService.snapshot();
    }
}
