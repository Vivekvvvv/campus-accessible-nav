package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import com.demo.accessiblenav.graph.change.GraphChangeKind;
import com.demo.accessiblenav.graph.change.GraphChangePayloadType;
import com.demo.accessiblenav.graph.dto.GraphChangeRequestDto;
import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.demo.accessiblenav.graph.dto.GraphValidationReport;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/graph/bulk-import")
public class GraphBulkImportController {

    private final GraphBulkImportService bulkImportService;
    private final GraphImportService graphImportService;
    private final AdminPermissionService permissionService;

    public GraphBulkImportController(GraphBulkImportService bulkImportService,
                                     GraphImportService graphImportService,
                                     AdminPermissionService permissionService) {
        this.bulkImportService = bulkImportService;
        this.graphImportService = graphImportService;
        this.permissionService = permissionService;
    }

    @PostMapping("/preview")
    public GraphValidationReport preview(@RequestParam("files") MultipartFile[] files,
                                         @RequestParam(value = "expandUndirected", required = false) Boolean expandUndirected) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR);
        GraphImportRequest request = bulkImportService.buildImportRequest(files);
        boolean expand = expandUndirected == null || expandUndirected;
        return graphImportService.validatePayload(request, expand);
    }

    @PostMapping
    public GraphChangeRequestDto submit(@RequestParam("files") MultipartFile[] files,
                                        @RequestParam(value = "kind", required = false) GraphChangeKind kind,
                                        @RequestParam(value = "payloadType", required = false) GraphChangePayloadType payloadType,
                                        @RequestParam(value = "note", required = false) String note) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);
        GraphChangeKind resolvedKind = kind == null ? GraphChangeKind.IMPORT : kind;
        GraphChangePayloadType resolvedType = payloadType == null ? GraphChangePayloadType.IMPORT : payloadType;
        return bulkImportService.bulkImport(files, resolvedKind, resolvedType, note);
    }
}
