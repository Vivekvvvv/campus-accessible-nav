package com.demo.accessiblenav.navigation.service;

import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.exception.ErrorCode;
import com.demo.accessiblenav.navigation.api.dto.CreateNavigationSessionRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationClientEventRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationHazardDto;
import com.demo.accessiblenav.navigation.api.dto.NavigationLocationRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationSessionResponse;
import com.demo.accessiblenav.navigation.api.dto.RerouteRequest;
import com.demo.accessiblenav.navigation.api.dto.WaypointDto;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEntity;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventEntity;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionStatus;
import com.demo.accessiblenav.obstacle.ObstacleEffectEntity;
import com.demo.accessiblenav.obstacle.ObstacleEffectRepository;
import com.demo.accessiblenav.route.GraphRoutingService;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import com.demo.accessiblenav.route.dto.TravelMode;
import com.demo.accessiblenav.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

@Service
public class NavigationSessionAppService {

    private final NavigationSessionRepository sessionRepository;
    private final NavigationSessionEventRepository eventRepository;
    private final GraphRoutingService routingService;
    private final ObjectMapper objectMapper;
    private final NavigationMetrics metrics;
    private final ObstacleEffectRepository effectRepository;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] TOKEN_ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    public NavigationSessionAppService(NavigationSessionRepository sessionRepository,
                                       NavigationSessionEventRepository eventRepository,
                                       GraphRoutingService routingService,
                                       ObjectMapper objectMapper,
                                       NavigationMetrics metrics,
                                       ObstacleEffectRepository effectRepository) {
        this.sessionRepository = sessionRepository;
        this.eventRepository = eventRepository;
        this.routingService = routingService;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
        this.effectRepository = effectRepository;
    }

    @Transactional
    public NavigationSessionResponse create(String userId, CreateNavigationSessionRequest req) {
        String mode = normalizeMode(req.getMode());

        String uid = blankToNull(userId);
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        Optional<NavigationSessionEntity> existing = sessionRepository
                .findFirstByUserIdAndStatusAndTenantIdOrderByCreatedAtDesc(uid, NavigationSessionStatus.ACTIVE, TenantContext.get());
        existing.ifPresent(this::endInternalSuperseded);

        Instant now = Instant.now();

        // Build waypoints list and determine first-leg destination
        List<WaypointDto> waypoints = req.getWaypoints();
        int totalLegs = 1;
        double firstLegEndLat = req.getEndLat();
        double firstLegEndLng = req.getEndLng();
        if (waypoints != null && !waypoints.isEmpty()) {
            totalLegs = waypoints.size() + 1;
            firstLegEndLat = waypoints.get(0).getLat();
            firstLegEndLng = waypoints.get(0).getLng();
        }

        RouteResponse route = route(req.getStartLat(), req.getStartLng(), firstLegEndLat, firstLegEndLng, mode);

        NavigationSessionEntity session = new NavigationSessionEntity();
        session.setId(UUID.randomUUID().toString());
        session.setUserId(uid);
        session.setStatus(NavigationSessionStatus.ACTIVE);
        session.setMode(mode);
        session.setStartLat(req.getStartLat());
        session.setStartLng(req.getStartLng());
        session.setDestinationLat(req.getEndLat());
        session.setDestinationLng(req.getEndLng());
        session.setDestinationName(blankToNull(req.getDestinationName()));
        session.setLastLat(req.getStartLat());
        session.setLastLng(req.getStartLng());
        session.setLastLocationAt(now);
        session.setDeviationCount(0);
        session.setRerouteCount(0);
        session.setCreatedAt(now);
        session.setStartedAt(now);
        session.setEndedAt(null);
        session.setRouteJson(writeJson(route));
        session.setResumeToken(generateResumeToken());
        session.setCurrentLeg(0);
        session.setTotalLegs(totalLegs);
        session.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());
        if (waypoints != null && !waypoints.isEmpty()) {
            session.setWaypointsJson(writeJson(waypoints));
        }

        sessionRepository.save(session);
        recordEvent(session, NavigationSessionEventType.START, "{\"mode\":\"" + mode + "\"}");

        metrics.incStarted(mode);
        return toResponse(session, route);
    }

    @Transactional(readOnly = true)
    public NavigationSessionResponse get(String userId, String sessionId) {
        NavigationSessionEntity session = requireOwned(userId, sessionId);
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional(readOnly = true)
    public NavigationSessionResponse getByResumeToken(String userId, String resumeToken) {
        String uid = blankToNull(userId);
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        String token = blankToNull(resumeToken);
        if (token == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }

        NavigationSessionEntity session = sessionRepository
                .findFirstByResumeTokenAndTenantIdOrderByCreatedAtDesc(token, TenantContext.get())
                .orElseThrow(() -> new BusinessException(ErrorCode.NAV_SESSION_NOT_FOUND));
        if (session.getUserId() == null || !uid.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional
    public NavigationSessionResponse pause(String userId, String sessionId) {
        NavigationSessionEntity session = requireActiveOrPausedOwned(userId, sessionId);
        if (session.getStatus() == NavigationSessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }
        session.setStatus(NavigationSessionStatus.PAUSED);
        sessionRepository.save(session);
        recordEvent(session, NavigationSessionEventType.PAUSE, null);
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional
    public NavigationSessionResponse resume(String userId, String sessionId) {
        NavigationSessionEntity session = requireActiveOrPausedOwned(userId, sessionId);
        if (session.getStatus() == NavigationSessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }
        session.setStatus(NavigationSessionStatus.ACTIVE);
        sessionRepository.save(session);
        recordEvent(session, NavigationSessionEventType.RESUME, null);
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional
    public NavigationSessionResponse end(String userId, String sessionId, String reason) {
        NavigationSessionEntity session = requireOwned(userId, sessionId);
        if (session.getStatus() != NavigationSessionStatus.ENDED) {
            session.setStatus(NavigationSessionStatus.ENDED);
            session.setEndedAt(Instant.now());
            sessionRepository.save(session);
            recordEvent(session, NavigationSessionEventType.END,
                    reason != null ? "{\"reason\":\"" + safeJson(reason) + "\"}" : null);

            metrics.incEnded(reason == null ? "ENDED" : reason);
            metrics.recordSessionDurationSeconds(Duration.between(session.getStartedAt(), session.getEndedAt()).toMillis() / 1000.0);
        }
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional
    public NavigationSessionResponse updateLocation(String userId, String sessionId, NavigationLocationRequest location) {
        NavigationSessionEntity session = requireOwned(userId, sessionId);
        if (session.getStatus() != NavigationSessionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }

        Instant observedAt = location.getObservedAt() != null ? location.getObservedAt() : Instant.now();
        session.setLastLat(location.getLat());
        session.setLastLng(location.getLng());
        session.setLastLocationAt(observedAt);
        sessionRepository.save(session);

        metrics.incLocationUpdates();
        // Keep event payload small; avoid logging high-frequency points by default.
        return toResponse(session, readRoute(session.getRouteJson()));
    }

    @Transactional
    public NavigationSessionResponse reroute(String userId, String sessionId, RerouteRequest req) {
        NavigationSessionEntity session = requireOwned(userId, sessionId);
        if (session.getStatus() != NavigationSessionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }

        String reason = blankToNull(req.getReason());
        if (reason != null && reason.equalsIgnoreCase("DEVIATION")) {
            session.setDeviationCount(session.getDeviationCount() + 1);
            recordEvent(session, NavigationSessionEventType.DEVIATION, "{\"lat\":" + req.getLat() + ",\"lng\":" + req.getLng() + "}");
        }

        RouteResponse route = route(req.getLat(), req.getLng(), session.getDestinationLat(), session.getDestinationLng(), session.getMode());
        if (session.getCurrentLeg() < session.getTotalLegs() - 1) {
            List<WaypointDto> waypoints = readWaypoints(session.getWaypointsJson());
            if (waypoints != null && !waypoints.isEmpty()) {
                int nextWaypointIdx = session.getCurrentLeg();
                if (nextWaypointIdx >= 0 && nextWaypointIdx < waypoints.size()) {
                    WaypointDto target = waypoints.get(nextWaypointIdx);
                    if (target != null) {
                        route = route(req.getLat(), req.getLng(), target.getLat(), target.getLng(), session.getMode());
                    }
                }
            }
        }

        session.setRerouteCount(session.getRerouteCount() + 1);
        session.setLastLat(req.getLat());
        session.setLastLng(req.getLng());
        session.setLastLocationAt(Instant.now());
        session.setRouteJson(writeJson(route));
        sessionRepository.save(session);

        recordEvent(session, NavigationSessionEventType.REROUTE,
                "{\"reason\":\"" + safeJson(reason == null ? "UNKNOWN" : reason) + "\"}");
        metrics.incReroute(session.getMode(), reason == null ? "UNKNOWN" : reason);

        return toResponse(session, route);
    }

    @Transactional(readOnly = true)
    public List<NavigationHazardDto> hazards(String userId, String sessionId, Double radiusM, Integer limit) {
        metrics.incHazardsQuery();

        NavigationSessionEntity session = requireOwned(userId, sessionId);
        RouteResponse route = readRoute(session.getRouteJson());
        if (route == null || route.getPath() == null || route.getPath().size() < 2) {
            return List.of();
        }

        double r = radiusM == null ? 30.0 : Math.max(1.0, Math.min(200.0, radiusM));
        int cap = limit == null ? 50 : Math.max(1, Math.min(200, limit));

        // Compute route bounding box (expanded by radius) to keep DB query small.
        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLng = Double.POSITIVE_INFINITY, maxLng = Double.NEGATIVE_INFINITY;
        double sumLat = 0;
        int n = 0;
        for (RouteResponse.LngLat p : route.getPath()) {
            double lat = p.getLat();
            double lng = p.getLng();
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) continue;
            minLat = Math.min(minLat, lat);
            maxLat = Math.max(maxLat, lat);
            minLng = Math.min(minLng, lng);
            maxLng = Math.max(maxLng, lng);
            sumLat += lat;
            n++;
        }
        if (n < 2 || !Double.isFinite(minLat) || !Double.isFinite(minLng)) {
            return List.of();
        }

        double meanLat = sumLat / n;
        double latBufDeg = r / 111320.0;
        double metersPerDegLng = 111320.0 * Math.max(0.1, Math.cos(Math.toRadians(meanLat)));
        double lngBufDeg = r / metersPerDegLng;

        BigDecimal qMinLat = BigDecimal.valueOf(minLat - latBufDeg);
        BigDecimal qMaxLat = BigDecimal.valueOf(maxLat + latBufDeg);
        BigDecimal qMinLng = BigDecimal.valueOf(minLng - lngBufDeg);
        BigDecimal qMaxLng = BigDecimal.valueOf(maxLng + lngBufDeg);

        Instant now = Instant.now();
        int queryCap = Math.min(500, cap * 5);
        List<ObstacleEffectEntity> effects = effectRepository.findActiveDisabledEffectsInBBoxByTenant(
                now,
                TenantContext.get(),
                qMinLat,
                qMaxLat,
                qMinLng,
                qMaxLng,
                PageRequest.of(0, queryCap)
        );

        List<NavigationHazardDto> out = new ArrayList<>();
        for (ObstacleEffectEntity ef : effects) {
            if (ef == null || ef.getEdge() == null) continue;
            var edge = ef.getEdge();
            var from = edge.getFromNode();
            var to = edge.getToNode();
            if (from == null || to == null) continue;

            double fromLat = from.getLat().doubleValue();
            double fromLng = from.getLng().doubleValue();
            double toLat = to.getLat().doubleValue();
            double toLng = to.getLng().doubleValue();

            double midLat = (fromLat + toLat) / 2.0;
            double midLng = (fromLng + toLng) / 2.0;

            Projection proj = projectToRouteMeters(midLng, midLat, route.getPath());
            if (proj == null) continue;
            if (proj.distanceToLineM > r) continue;

            out.add(new NavigationHazardDto(
                    ef.getId(),
                    edge.getId(),
                    ef.getReason(),
                    ef.getEndAt(),
                    fromLat,
                    fromLng,
                    toLat,
                    toLng,
                    proj.distanceToLineM,
                    proj.alongM
            ));
        }

        out.sort(Comparator.comparingDouble(NavigationHazardDto::getDistanceToRouteM));
        if (out.size() > cap) {
            out = out.subList(0, cap);
        }
        metrics.addHazardsMatched(out.size());
        return out;
    }

    @Transactional
    public NavigationSessionResponse advanceLeg(String userId, String sessionId) {
        NavigationSessionEntity session = requireActiveOrPausedOwned(userId, sessionId);
        int next = session.getCurrentLeg() + 1;
        if (next >= session.getTotalLegs()) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }

        List<WaypointDto> waypoints = readWaypoints(session.getWaypointsJson());
        if (waypoints == null || waypoints.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }

        // Mark current waypoint as reached
        if (next - 1 < waypoints.size()) {
            waypoints.get(next - 1).setReached(true);
            session.setWaypointsJson(writeJson(waypoints));
        }

        // Calculate route for next leg
        double fromLat, fromLng;
        if (session.getLastLat() != null && session.getLastLng() != null) {
            fromLat = session.getLastLat();
            fromLng = session.getLastLng();
        } else {
            // fallback to current waypoint location
            fromLat = waypoints.get(next - 1).getLat();
            fromLng = waypoints.get(next - 1).getLng();
        }

        double toLat, toLng;
        if (next < waypoints.size()) {
            toLat = waypoints.get(next).getLat();
            toLng = waypoints.get(next).getLng();
        } else {
            // Last leg: navigate to final destination
            toLat = session.getDestinationLat();
            toLng = session.getDestinationLng();
        }

        RouteResponse route = route(fromLat, fromLng, toLat, toLng, session.getMode());
        session.setCurrentLeg(next);
        session.setRouteJson(writeJson(route));
        sessionRepository.save(session);
        recordEvent(session, NavigationSessionEventType.WAYPOINT_REACHED,
                "{\"leg\":" + next + ",\"totalLegs\":" + session.getTotalLegs() + "}");
        return toResponse(session, route);
    }

    @Transactional
    public void clientEvent(String userId, String sessionId, NavigationClientEventRequest req) {
        NavigationSessionEntity session = requireActiveOrPausedOwned(userId, sessionId);

        String type = normalizeClientEventType(req == null ? null : req.getType());
        String payload = blankToNull(req == null ? null : req.getPayload());

        NavigationSessionEventEntity e = new NavigationSessionEventEntity();
        e.setSession(session);
        e.setEventType("CLIENT_" + type);
        e.setCreatedAt(Instant.now());
        if (payload != null) {
            e.setPayloadJson("{\"type\":\"" + safeJson(type) + "\",\"payload\":\"" + safeJson(payload) + "\"}");
        } else {
            e.setPayloadJson("{\"type\":\"" + safeJson(type) + "\"}");
        }
        eventRepository.save(e);

        metrics.incClientEvent(type);
    }

    private RouteResponse route(double startLat, double startLng, double endLat, double endLng, String mode) {
        RouteRequest routeRequest = new RouteRequest();
        routeRequest.setStartLat(startLat);
        routeRequest.setStartLng(startLng);
        routeRequest.setEndLat(endLat);
        routeRequest.setEndLng(endLng);
        routeRequest.setMode(TravelMode.valueOf(mode));
        return routingService.route(routeRequest);
    }

    private static final class Projection {
        final double distanceToLineM;
        final double alongM;

        Projection(double distanceToLineM, double alongM) {
            this.distanceToLineM = distanceToLineM;
            this.alongM = alongM;
        }
    }

    // Equirectangular projection-based point-to-polyline projection (campus-scale routes).
    private Projection projectToRouteMeters(double lng, double lat, List<RouteResponse.LngLat> path) {
        if (path == null || path.size() < 2) return null;
        if (!Double.isFinite(lng) || !Double.isFinite(lat)) return null;

        double refLatRad = Math.toRadians(lat);
        double px = lngToXMeters(lng, refLatRad);
        double py = latToYMeters(lat);

        double bestD = Double.POSITIVE_INFINITY;
        double bestAlong = 0;
        double cum = 0;

        for (int i = 0; i < path.size() - 1; i++) {
            RouteResponse.LngLat a = path.get(i);
            RouteResponse.LngLat b = path.get(i + 1);
            if (a == null || b == null) continue;

            double axLng = a.getLng();
            double axLat = a.getLat();
            double bxLng = b.getLng();
            double bxLat = b.getLat();
            if (!Double.isFinite(axLng) || !Double.isFinite(axLat) || !Double.isFinite(bxLng) || !Double.isFinite(bxLat)) continue;

            double ax = lngToXMeters(axLng, refLatRad);
            double ay = latToYMeters(axLat);
            double bx = lngToXMeters(bxLng, refLatRad);
            double by = latToYMeters(bxLat);

            double vx = bx - ax;
            double vy = by - ay;
            double segLen = Math.hypot(vx, vy);
            if (segLen <= 0) continue;

            double wx = px - ax;
            double wy = py - ay;
            double t = clamp((wx * vx + wy * vy) / (segLen * segLen), 0.0, 1.0);

            double projX = ax + t * vx;
            double projY = ay + t * vy;
            double d = Math.hypot(px - projX, py - projY);
            if (d < bestD) {
                bestD = d;
                bestAlong = cum + t * segLen;
            }

            cum += segLen;
        }

        return bestD == Double.POSITIVE_INFINITY ? null : new Projection(bestD, bestAlong);
    }

    private static double lngToXMeters(double lng, double refLatRad) {
        return (lng * 111320.0) * Math.cos(refLatRad);
    }

    private static double latToYMeters(double lat) {
        return lat * 110540.0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private NavigationSessionEntity requireOwned(String userId, String sessionId) {
        String uid = blankToNull(userId);
        if (uid == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        NavigationSessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NAV_SESSION_NOT_FOUND));
        if (session.getUserId() == null || !uid.equals(session.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (!TenantContext.get().equals(session.getTenantId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return session;
    }

    private NavigationSessionEntity requireActiveOrPausedOwned(String userId, String sessionId) {
        NavigationSessionEntity session = requireOwned(userId, sessionId);
        if (session.getStatus() == NavigationSessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.NAV_SESSION_NOT_ACTIVE);
        }
        return session;
    }

    private void endInternalSuperseded(NavigationSessionEntity session) {
        if (session.getStatus() == NavigationSessionStatus.ENDED) return;
        session.setStatus(NavigationSessionStatus.ENDED);
        session.setEndedAt(Instant.now());
        sessionRepository.save(session);
        recordEvent(session, NavigationSessionEventType.END, "{\"reason\":\"SUPERSEDED\"}");
        metrics.incEnded("SUPERSEDED");
    }

    private void recordEvent(NavigationSessionEntity session, String type, String payloadJson) {
        NavigationSessionEventEntity e = new NavigationSessionEventEntity();
        e.setSession(session);
        e.setEventType(type);
        e.setCreatedAt(Instant.now());
        e.setPayloadJson(payloadJson);
        eventRepository.save(e);
    }

    private NavigationSessionResponse toResponse(NavigationSessionEntity s, RouteResponse route) {
        NavigationSessionResponse r = new NavigationSessionResponse();
        r.setSessionId(s.getId());
        r.setUserId(s.getUserId());
        r.setStatus(s.getStatus());
        r.setMode(s.getMode());
        r.setStartLat(s.getStartLat());
        r.setStartLng(s.getStartLng());
        r.setDestinationLat(s.getDestinationLat());
        r.setDestinationLng(s.getDestinationLng());
        r.setDestinationName(s.getDestinationName());
        r.setLastLat(s.getLastLat());
        r.setLastLng(s.getLastLng());
        r.setLastLocationAt(s.getLastLocationAt());
        r.setDeviationCount(s.getDeviationCount());
        r.setRerouteCount(s.getRerouteCount());
        r.setCreatedAt(s.getCreatedAt());
        r.setStartedAt(s.getStartedAt());
        r.setEndedAt(s.getEndedAt());
        r.setResumeToken(s.getResumeToken());
        r.setRoute(route);
        r.setCurrentLeg(s.getCurrentLeg());
        r.setTotalLegs(s.getTotalLegs());
        List<WaypointDto> waypoints = readWaypoints(s.getWaypointsJson());
        if (waypoints != null) {
            r.setWaypoints(waypoints);
        }
        if (route != null) {
            Integer currentLevel = null;
            if (route.getPathWithLevel() != null && !route.getPathWithLevel().isEmpty()) {
                currentLevel = route.getPathWithLevel().get(0).getLevel();
            }
            r.setCurrentLevel(currentLevel);

            if (route.getLevelTransitions() != null && !route.getLevelTransitions().isEmpty()) {
                RouteResponse.LevelTransition nextTransition = route.getLevelTransitions().get(0);
                r.setNextLevel(nextTransition.getToLevel());
                r.setLevelTransitionVia(nextTransition.getVia());
            }
        }
        return r;
    }

    private static String generateResumeToken() {
        int len = 48;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            int idx = SECURE_RANDOM.nextInt(TOKEN_ALPHABET.length);
            sb.append(TOKEN_ALPHABET[idx]);
        }
        return sb.toString();
    }

    private String writeJson(Object v) {
        if (v == null) return null;
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            // Best-effort: do not fail navigation if debug snapshot can't be serialized.
            return null;
        }
    }

    private RouteResponse readRoute(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json, RouteResponse.class);
        } catch (Exception e) {
            return null;
        }
    }

    private List<WaypointDto> readWaypoints(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, WaypointDto.class));
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeMode(String modeRaw) {
        String m = modeRaw == null ? "" : modeRaw.trim();
        if (m.isEmpty()) {
            return "WALK";
        }
        String up = m.toUpperCase(Locale.ROOT);
        if (!up.equals("WALK") && !up.equals("WHEELCHAIR")) {
            throw new BusinessException(ErrorCode.NAV_INVALID_MODE);
        }
        return up;
    }

    private static String blankToNull(String v) {
        if (v == null) return null;
        String s = v.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeClientEventType(String raw) {
        String s = blankToNull(raw);
        if (s == null) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }
        String up = s.toUpperCase(Locale.ROOT);
        // Keep it strict to prevent metric cardinality and log injection.
        if (!up.matches("[A-Z0-9_]{1,32}")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER);
        }
        return up;
    }

    private static String safeJson(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
