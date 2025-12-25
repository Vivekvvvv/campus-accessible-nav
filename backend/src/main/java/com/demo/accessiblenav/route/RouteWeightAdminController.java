package com.demo.accessiblenav.route;

import com.demo.accessiblenav.route.dto.RoutePassabilityPolicyDto;
import com.demo.accessiblenav.route.dto.RoutePassabilityPolicyUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/route/weights")
@Validated
@Tag(name = "Route Weight Admin", description = "Dynamic route weight policy management")
public class RouteWeightAdminController {

    private final RoutePassabilityPolicyService policyService;

    public RouteWeightAdminController(RoutePassabilityPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    @Operation(summary = "Get route passability dynamic weight policy")
    public RoutePassabilityPolicyDto getPolicy() {
        return policyService.getForCurrentTenant();
    }

    @PutMapping
    @Operation(summary = "Update route passability dynamic weight policy")
    public RoutePassabilityPolicyDto updatePolicy(@RequestBody @Valid RoutePassabilityPolicyUpdateRequest request) {
        return policyService.updateForCurrentTenant(request);
    }
}
