package com.demo.accessiblenav.navigation.api;

import com.demo.accessiblenav.navigation.api.dto.CreateNavigationSessionRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationClientEventRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationHazardDto;
import com.demo.accessiblenav.navigation.api.dto.NavigationLocationRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationSessionResponse;
import com.demo.accessiblenav.navigation.api.dto.RerouteRequest;
import com.demo.accessiblenav.navigation.service.NavigationSessionAppService;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

@RestController
@RequestMapping("/api/navigation")
public class NavigationSessionController {

    private final NavigationSessionAppService service;

    public NavigationSessionController(NavigationSessionAppService service) {
        this.service = service;
    }

    /**
     * Create a navigation session and return the initial route.
     */
    @PostMapping("/session")
    public NavigationSessionResponse create(@RequestBody CreateNavigationSessionRequest req, Authentication auth) {
        return service.create(requireUserId(auth), req);
    }

    @GetMapping("/session/{id}")
    public NavigationSessionResponse get(@PathVariable("id") String sessionId, Authentication auth) {
        return service.get(requireUserId(auth), sessionId);
    }

    @GetMapping("/session/resume-token/{token}")
    public NavigationSessionResponse getByResumeToken(@PathVariable("token") String resumeToken, Authentication auth) {
        return service.getByResumeToken(requireUserId(auth), resumeToken);
    }

    @PostMapping("/session/{id}/pause")
    public NavigationSessionResponse pause(@PathVariable("id") String sessionId, Authentication auth) {
        return service.pause(requireUserId(auth), sessionId);
    }

    @PostMapping("/session/{id}/resume")
    public NavigationSessionResponse resume(@PathVariable("id") String sessionId, Authentication auth) {
        return service.resume(requireUserId(auth), sessionId);
    }

    @PostMapping("/session/{id}/end")
    public NavigationSessionResponse end(@PathVariable("id") String sessionId,
                                         @RequestParam(name = "reason", required = false) String reason,
                                         Authentication auth) {
        return service.end(requireUserId(auth), sessionId, reason);
    }

    @PostMapping("/session/{id}/location")
    public NavigationSessionResponse location(@PathVariable("id") String sessionId,
                                              @RequestBody NavigationLocationRequest req,
                                              Authentication auth) {
        return service.updateLocation(requireUserId(auth), sessionId, req);
    }

    @PostMapping("/session/{id}/reroute")
    public NavigationSessionResponse reroute(@PathVariable("id") String sessionId,
                                             @RequestBody RerouteRequest req,
                                             Authentication auth) {
        return service.reroute(requireUserId(auth), sessionId, req);
    }

    @PostMapping("/session/{id}/advance-leg")
    public NavigationSessionResponse advanceLeg(@PathVariable("id") String sessionId,
                                                Authentication auth) {
        return service.advanceLeg(requireUserId(auth), sessionId);
    }

    @GetMapping("/session/{id}/hazards")
    public List<NavigationHazardDto> hazards(@PathVariable("id") String sessionId,
                                             @RequestParam(name = "radiusM", required = false) Double radiusM,
                                             @RequestParam(name = "limit", required = false) Integer limit,
                                             Authentication auth) {
        return service.hazards(requireUserId(auth), sessionId, radiusM, limit);
    }

    @PostMapping("/session/{id}/client-event")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clientEvent(@PathVariable("id") String sessionId,
                            @RequestBody NavigationClientEventRequest req,
                            Authentication auth) {
        service.clientEvent(requireUserId(auth), sessionId, req);
    }

    private static String requireUserId(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().trim().isEmpty()) {
            // SecurityConfig should prevent this, but keep it defensive.
            throw new IllegalStateException("missing authenticated user");
        }
        return auth.getName();
    }
}
