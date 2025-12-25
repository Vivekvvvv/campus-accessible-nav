package com.demo.accessiblenav.apikey;

import com.demo.accessiblenav.route.GraphRoutingService;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import com.demo.accessiblenav.audit.OperationLogService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Locale;

@RestController
@RequestMapping("/api/open")
public class OpenRouteController {

    private final GraphRoutingService routingService;
    private final OperationLogService logService;

    public OpenRouteController(GraphRoutingService routingService,
                               OperationLogService logService) {
        this.routingService = routingService;
        this.logService = logService;
    }

    @PostMapping("/route")
    public RouteResponse route(@RequestBody RouteRequest request,
                               HttpServletRequest servletRequest) {
        Object scopesObj = servletRequest.getAttribute("apiKeyScopes");
        String scopes = scopesObj == null ? "" : String.valueOf(scopesObj);
        if (!hasScope(scopes, "route:read")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "scope route:read required");
        }
        RouteResponse response = routingService.route(request);
        logService.log("OPEN_API_ROUTE", "mode=" + request.getMode());
        return response;
    }

    private boolean hasScope(String scopesRaw, String expectedScope) {
        if (scopesRaw == null || scopesRaw.isBlank()) {
            return false;
        }
        String expected = expectedScope.toLowerCase(Locale.ROOT);
        return Arrays.stream(scopesRaw.split("[,\\s]+"))
                .map(s -> s == null ? "" : s.trim().toLowerCase(Locale.ROOT))
                .anyMatch(expected::equals);
    }
}
