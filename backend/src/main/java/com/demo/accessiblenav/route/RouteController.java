package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.GraphBootstrapState;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Validated
@Tag(name = "Routing", description = "Route computation APIs")
public class RouteController {

    private static final Logger log = LoggerFactory.getLogger(RouteController.class);

    private final GraphRoutingService routingService;
    private final RoutePreferencePolicyResolver routePreferencePolicyResolver;
    private final RoutePassabilityPolicyService routePassabilityPolicyService;
    private final GraphBootstrapState bootstrapState;

    public RouteController(GraphRoutingService routingService,
                           RoutePreferencePolicyResolver routePreferencePolicyResolver,
                           RoutePassabilityPolicyService routePassabilityPolicyService,
                           GraphBootstrapState bootstrapState) {
        this.routingService = routingService;
        this.routePreferencePolicyResolver = routePreferencePolicyResolver;
        this.routePassabilityPolicyService = routePassabilityPolicyService;
        this.bootstrapState = bootstrapState;
    }

    @PostMapping("/route")
    @Operation(
            summary = "Compute route",
            description = "Compute route with optional accessibility policy defaults from user profile"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Route computed",
                    content = @Content(schema = @Schema(implementation = RouteResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "503", description = "Graph still initializing")
    })
    public RouteResponse route(
            @Parameter(description = "Route request", required = true)
            @RequestBody @Valid RouteRequest req,
            Authentication auth) {
        if (!bootstrapState.isReady()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "graph initializing");
        }

        RoutePreferencePolicyResolver.ResolvedRoutingPolicy resolved = routePreferencePolicyResolver.resolve(req, auth);
        req.setStrategy(resolved.getStrategy());
        req.setSlopeWeight(resolved.getSlopeWeight());
        req.setStrategyWeights(resolved.getStrategyWeights());
        RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy =
                routePassabilityPolicyService.resolveForCurrentTenant();

        log.info("POST /api/route mode={} strategy={} slopeWeight={} start=({}, {}) end=({}, {})",
                req.getMode(),
                req.getStrategy(),
                req.getSlopeWeight(),
                req.getStartLat(), req.getStartLng(),
                req.getEndLat(), req.getEndLng());

        RouteResponse response = routingService.route(req);
        response.setRoutingPolicy(new RouteResponse.RoutingPolicy(
                resolved.isProfileApplied(),
                resolved.getStrategy() == null ? null : resolved.getStrategy().name(),
                resolved.getSlopeWeight(),
                resolved.getStrategySource(),
                resolved.getSlopeWeightSource(),
                resolved.getProfileMobilityMode(),
                resolved.getStrategyWeights(),
                passabilityPolicy.isPassabilityPenaltyEnabled(),
                passabilityPolicy.getPassabilityMinClamp(),
                passabilityPolicy.getPassabilityWeightFactor(),
                passabilityPolicy.getSource()
        ));
        return response;
    }
}
