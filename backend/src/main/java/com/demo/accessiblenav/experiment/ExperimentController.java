package com.demo.accessiblenav.experiment;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import com.demo.accessiblenav.experiment.dto.ExperimentAssignmentResponse;
import com.demo.accessiblenav.experiment.dto.ExperimentExposureRequest;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/experiments")
public class ExperimentController {

    private final ExperimentService experimentService;
    private final AdminPermissionService permissionService;

    public ExperimentController(ExperimentService experimentService,
                                AdminPermissionService permissionService) {
        this.experimentService = experimentService;
        this.permissionService = permissionService;
    }

    @PostMapping("/{name}/assign")
    public ExperimentAssignmentResponse assign(@PathVariable("name") String experimentName,
                                               @RequestBody Map<String, String> body) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER, UserRole.USER);
        String userId = body == null ? null : body.get("userId");
        return experimentService.assign(experimentName, userId);
    }

    @PostMapping("/{name}/exposure")
    public Map<String, String> exposure(@PathVariable("name") String experimentName,
                                        @RequestBody(required = false) ExperimentExposureRequest body) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.REVIEWER, UserRole.EDITOR, UserRole.VIEWER, UserRole.USER);
        String userId = body == null ? null : body.getUserId();
        String event = body == null ? null : body.getEvent();
        experimentService.recordExposure(experimentName, userId, event);
        return Map.of("status", "ok");
    }
}
