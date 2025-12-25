package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.obstacle.ObstacleEffectRepository;
import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.exception.ErrorCode;
import com.demo.accessiblenav.exception.RouteException;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import com.demo.accessiblenav.route.dto.RouteStrategy;
import com.demo.accessiblenav.route.dto.RouteStrategyWeights;
import com.demo.accessiblenav.route.dto.TravelMode;
import com.demo.accessiblenav.tenant.TenantContext;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;

@Service
public class GraphRoutingService {

    private static final Logger log = LoggerFactory.getLogger(GraphRoutingService.class);

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final ObstacleEffectRepository obstacleEffectRepository;
    private final GraphVersionService graphVersionService;
    private final RouteCache routeCache;
    private final Timer routeCalculationTimer;
    private final RouteMetrics routeMetrics;
    private final RoutePassabilityPolicyService routePassabilityPolicyService;

    @Value("${app.routing.max-snap-meters:200}")
    private double maxSnapMeters;

    @Value("${app.routing.use-postgis-knn:false}")
    private boolean usePostgisKnn;

    @Value("${app.campus.bbox:}")
    private String campusBboxRaw;

    private volatile CampusBounds campusBounds;
    private final Object graphLock = new Object();
    private volatile CachedGraph cachedGraph;

    public GraphRoutingService(NodeRepository nodeRepository,
                              EdgeRepository edgeRepository,
                              ObstacleEffectRepository obstacleEffectRepository,
                              GraphVersionService graphVersionService,
                              RouteCache routeCache,
                              Timer routeCalculationTimer,
                              RouteMetrics routeMetrics,
                              RoutePassabilityPolicyService routePassabilityPolicyService) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.obstacleEffectRepository = obstacleEffectRepository;
        this.graphVersionService = graphVersionService;
        this.routeCache = routeCache;
        this.routeCalculationTimer = routeCalculationTimer;
        this.routeMetrics = routeMetrics;
        this.routePassabilityPolicyService = routePassabilityPolicyService;
    }

    @Transactional(readOnly = true)
    public Long findNearestEdgeId(double lat, double lng) {
        long version = graphVersionService.current();
        CachedGraph graph = getCachedGraph(version, TenantContext.get());

        double bestDist = Double.MAX_VALUE;
        Long bestEdgeId = null;

        // 简单暴力全量扫描（毕设数据量级允许）
        for (EdgeSegment e : graph.edges) {
            NodePoint fromP = graph.nodes.get(e.fromId);
            NodePoint toP = graph.nodes.get(e.toId);
            if (fromP == null || toP == null) continue;

            Projection p = projectToSegmentMeters(lng, lat, fromP.lng, fromP.lat, toP.lng, toP.lat);
            if (p != null) {
                if (p.distanceM < bestDist) {
                    bestDist = p.distanceM;
                    bestEdgeId = e.edgeId;
                }
            }
        }

        if (bestEdgeId != null && maxSnapMeters > 0 && bestDist > maxSnapMeters) {
            return null;
        }
        return bestEdgeId;
    }

    @Transactional(readOnly = true)
    public RouteResponse route(RouteRequest req) {
        long version = graphVersionService.current();
        String tenantId = TenantContext.get();

        RouteStrategy strategy = req.getStrategy() != null ? req.getStrategy() : RouteStrategy.BALANCED;

        final boolean slopeWeightFromRequest = req.getSlopeWeight() != null;
        double slopeWeightValue = slopeWeightFromRequest ? req.getSlopeWeight() : defaultSlopeWeight(strategy, req.getMode());
        if (slopeWeightValue < 0) slopeWeightValue = 0;
        if (slopeWeightValue > 1) slopeWeightValue = 1;
        final double slopeWeight = slopeWeightValue;
        final RouteStrategyWeights strategyWeights = sanitizeStrategyWeights(req.getStrategyWeights(), strategy, req.getMode(), slopeWeight);
        final RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy =
                routePassabilityPolicyService.resolveForCurrentTenant();

        boolean campusOnly = Boolean.TRUE.equals(req.getCampusOnly());

        if (log.isDebugEnabled()) {
            log.debug("route compute mode={} strategy={} slopeWeight={} version={} start=({}, {}) end=({}, {})",
                req.getMode(),
                strategy,
                slopeWeight,
                version,
                req.getStartLat(), req.getStartLng(),
                req.getEndLat(), req.getEndLng());
        }

        // Time-limited obstacle effects can change the effective graph without a version bump.
        // Include "next expiry instant" in the cache key to avoid serving stale routes after expiry.
        long expiryToken = 0L;
        try {
            Instant nextExpiry = obstacleEffectRepository.findNextExpiryAfterByTenant(Instant.now(), tenantId);
            if (nextExpiry != null) {
                expiryToken = nextExpiry.getEpochSecond();
            }
        } catch (Exception ignored) {
            // Cache correctness should not fail routing; worst case we keep current behavior.
        }

        String cacheKey = buildCacheKey(req, slopeWeight, strategyWeights, strategy, passabilityPolicy, version, campusOnly, expiryToken, tenantId);
        RouteResponse cached = routeCache.get(cacheKey);
        if (cached != null) {
            routeMetrics.incRequest(true, true, req.getMode(), strategy, campusOnly);
            return cached;
        }

        int startLevel = req.getStartLevel() != null ? req.getStartLevel() : 1;
        int endLevel = req.getEndLevel() != null ? req.getEndLevel() : 1;

        final String[] stage = new String[] { "unknown" };
        RouteResponse resp;
        try {
            resp = routeCalculationTimer.record(() -> {
                stage[0] = "graph_load";
                GraphSnapshot graph = routeMetrics.recordStage(stage[0], () -> loadGraphWithAnchors(
                        req.getStartLat(),
                        req.getStartLng(),
                        startLevel,
                        req.getEndLat(),
                        req.getEndLng(),
                        endLevel,
                        campusOnly,
                        version,
                        tenantId
                ));

                stage[0] = "primary_route";
                RouteComputation primary = routeMetrics.recordStage(stage[0],
                        () -> computeRoute(graph, req.getMode(), slopeWeight, strategyWeights, strategy, passabilityPolicy));

                stage[0] = "build_path";
                List<RouteResponse.LevelPoint> pathWithLevel = routeMetrics.recordStage(stage[0],
                        () -> buildPathWithLevel(graph, primary.pathNodeIds));
                List<RouteResponse.LngLat> flatPath = flattenPath(pathWithLevel);
                List<RouteResponse.LevelTransition> levelTransitions = buildLevelTransitions(graph, primary.pathNodeIds);

                stage[0] = "instructions";
                List<RouteResponse.LevelTransition> lt = levelTransitions;
                List<RouteResponse.Instruction> instructions = routeMetrics.recordStage(stage[0],
                        () -> buildTurnByTurn(flatPath, lt));

                stage[0] = "explain";
                List<String> explain = routeMetrics.recordStage(stage[0],
                        () -> buildExplain(graph, primary.pathNodeIds, req.getMode(), slopeWeight, strategyWeights, passabilityPolicy, primary.riskCount, strategy));

                stage[0] = "compare_mode";
                RouteResponse.ModeDiff modeDiff = null;
                TravelMode compareMode = req.getMode() == TravelMode.WHEELCHAIR ? TravelMode.WALK : TravelMode.WHEELCHAIR;
                double compareSlopeWeight = slopeWeightFromRequest ? slopeWeight : defaultSlopeWeight(strategy, compareMode);
                RouteStrategyWeights compareWeights = sanitizeStrategyWeights(req.getStrategyWeights(), strategy, compareMode, compareSlopeWeight);
                try {
                    RouteComputation compare = routeMetrics.recordStage(stage[0],
                            () -> computeRoute(graph, compareMode, compareSlopeWeight, compareWeights, strategy, passabilityPolicy));
                    modeDiff = buildModeDiff(req.getMode(), primary, compare);
                } catch (Exception ex) {
                    modeDiff = buildModeDiff(req.getMode(), primary, null);
                }

                if (explain != null) {
                    appendSnapHint(explain, graph.startSnap, "起点");
                    appendSnapHint(explain, graph.endSnap, "终点");
                    if (modeDiff != null && modeDiff.getNotes() != null) {
                        explain.addAll(modeDiff.getNotes());
                    }
                }

                RouteResponse.Debug debug = new RouteResponse.Debug(
                        graph.startNodeId,
                        graph.endNodeId,
                        primary.visited,
                        primary.relaxations
                );

                stage[0] = "assemble";
                final RouteResponse.ModeDiff finalModeDiff = modeDiff;
                return routeMetrics.recordStage(stage[0], () -> new RouteResponse(
                        req.getMode().name(),
                        primary.distanceM,
                        primary.durationSec,
                        primary.riskCount,
                        instructions,
                        flatPath,
                        debug,
                        explain,
                        graph.startSnap,
                        graph.endSnap,
                        pathWithLevel,
                        levelTransitions,
                        finalModeDiff,
                        null
                ));
            });
        } catch (RuntimeException ex) {
            String reasonCode = routeMetrics.classifyFailureCode(ex);
            routeMetrics.incRequest(false, false, req.getMode(), strategy, campusOnly);
            routeMetrics.incFailure(reasonCode, stage[0], req.getMode(), strategy, campusOnly);
            throw ex;
        }

        routeMetrics.incRequest(false, true, req.getMode(), strategy, campusOnly);
        routeCache.put(cacheKey, resp);
        return resp;
    }

    private RouteComputation computeRoute(GraphSnapshot graph,
                                          TravelMode mode,
                                          double slopeWeight,
                                          RouteStrategyWeights strategyWeights,
                                          RouteStrategy strategy,
                                          RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy) {
        AStarResult result = aStar(graph, graph.startNodeId, graph.endNodeId, mode, slopeWeight, strategyWeights, strategy, passabilityPolicy);
        int riskCount = countRisksOnPath(graph, result.pathNodeIds, mode);
        long durationSec = estimateDurationSec(result.distanceM, mode, riskCount);
        RouteStats stats = analyzePath(graph, result.pathNodeIds);
        return new RouteComputation(result.pathNodeIds, result.distanceM, durationSec, riskCount, result.visited, result.relaxations, stats);
    }

    private List<RouteResponse.LevelPoint> buildPathWithLevel(GraphSnapshot graph, List<Long> nodeIds) {
        List<RouteResponse.LevelPoint> path = new ArrayList<>();
        if (nodeIds == null) {
            return path;
        }
        for (Long nodeId : nodeIds) {
            NodePoint p = graph.nodes.get(nodeId);
            if (p != null) {
                path.add(new RouteResponse.LevelPoint(p.lng, p.lat, p.level));
            }
        }
        return path;
    }

    private List<RouteResponse.LngLat> flattenPath(List<RouteResponse.LevelPoint> pathWithLevel) {
        List<RouteResponse.LngLat> flat = new ArrayList<>();
        if (pathWithLevel == null) {
            return flat;
        }
        RouteResponse.LevelPoint prev = null;
        for (RouteResponse.LevelPoint p : pathWithLevel) {
            if (p == null) {
                continue;
            }
            if (prev != null) {
                double dlng = Math.abs(p.getLng() - prev.getLng());
                double dlat = Math.abs(p.getLat() - prev.getLat());
                if (dlng < 1e-7 && dlat < 1e-7) {
                    prev = p;
                    continue;
                }
            }
            flat.add(new RouteResponse.LngLat(p.getLng(), p.getLat()));
            prev = p;
        }
        return flat;
    }

    private List<RouteResponse.LevelTransition> buildLevelTransitions(GraphSnapshot graph, List<Long> nodeIds) {
        List<RouteResponse.LevelTransition> transitions = new ArrayList<>();
        if (nodeIds == null || nodeIds.size() < 2) {
            return transitions;
        }
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            Long fromId = nodeIds.get(i);
            Long toId = nodeIds.get(i + 1);
            NodePoint from = graph.nodes.get(fromId);
            NodePoint to = graph.nodes.get(toId);
            if (from == null || to == null) {
                continue;
            }
            if (from.level != to.level) {
                EdgeData edge = findEdge(graph, fromId, toId);
                String via = "LEVEL";
                if (edge != null) {
                    if (edge.isElevator) {
                        via = "ELEVATOR";
                    } else if (edge.hasStairs) {
                        via = "STAIRS";
                    } else {
                        via = "RAMP";
                    }
                }
                transitions.add(new RouteResponse.LevelTransition(from.level, to.level, to.lng, to.lat, via));
            }
        }
        return transitions;
    }

    private RouteResponse.ModeDiff buildModeDiff(TravelMode mode, RouteComputation current, RouteComputation compare) {
        TravelMode compareMode = mode == TravelMode.WHEELCHAIR ? TravelMode.WALK : TravelMode.WHEELCHAIR;
        String compareLabel = compareMode == TravelMode.WALK ? "步行" : "轮椅";
        List<String> notes = new ArrayList<>();

        if (compare == null) {
            notes.add("对比" + compareLabel + "路线不可达。");
            return new RouteResponse.ModeDiff(compareMode.name(), 0, 0, notes);
        }

        double delta = current.distanceM - compare.distanceM;
        if (Math.abs(delta) >= 5) {
            String relation = delta > 0 ? "更长" : "更短";
            notes.add("对比" + compareLabel + "路线，当前路线" + relation + "约" + Math.round(Math.abs(delta)) + "米。");
        }

        RouteStats walkStats = mode == TravelMode.WALK ? current.stats : compare.stats;
        RouteStats wheelStats = mode == TravelMode.WHEELCHAIR ? current.stats : compare.stats;
        if (walkStats != null && wheelStats != null) {
            if (walkStats.stairsCount > 0 && wheelStats.stairsCount == 0) {
                notes.add("轮椅路线避开楼梯 " + walkStats.stairsCount + " 段。");
            }
            if (walkStats.inaccessibleCount > 0 && wheelStats.inaccessibleCount == 0) {
                notes.add("轮椅路线避开不可达路段 " + walkStats.inaccessibleCount + " 段。");
            }
            if (wheelStats.elevatorCount > walkStats.elevatorCount && wheelStats.elevatorCount > 0) {
                notes.add("轮椅路线更多使用电梯 " + wheelStats.elevatorCount + " 段。");
            }
            if (wheelStats.steepCount < walkStats.steepCount && walkStats.steepCount > 0) {
                notes.add("轮椅路线减少陡坡路段。");
            }
        }

        if (notes.isEmpty()) {
            notes.add("步行与轮椅路线差异不明显。");
        }
        return new RouteResponse.ModeDiff(compareMode.name(), compare.distanceM, compare.durationSec, notes);
    }

    private void appendSnapHint(List<String> explain, RouteResponse.SnapInfo snap, String label) {
        if (explain == null || snap == null) {
            return;
        }
        String type = "NODE".equalsIgnoreCase(snap.getType()) ? "节点" : "边";
        long dist = Math.round(snap.getDistanceM());
        String level = snap.getLevel() != null ? ("L" + snap.getLevel()) : "";
        explain.add(label + "吸附到" + type + level + "，偏移约" + dist + "米。");
    }

    private RouteStats analyzePath(GraphSnapshot graph, List<Long> nodeIds) {
        RouteStats stats = new RouteStats();
        if (nodeIds == null || nodeIds.size() < 2) {
            return stats;
        }
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            long from = nodeIds.get(i);
            long to = nodeIds.get(i + 1);
            EdgeData e = findEdge(graph, from, to);
            if (e == null) {
                continue;
            }
            stats.segments++;
            if (e.hasStairs) stats.stairsCount++;
            if (e.isElevator) stats.elevatorCount++;
            if (!e.accessibleDefault) stats.inaccessibleCount++;
            if (e.slopeLevel >= 3) stats.steepCount++;
            if (e.slopeLevel > stats.maxSlope) stats.maxSlope = e.slopeLevel;
        }
        return stats;
    }

    private String buildCacheKey(RouteRequest req,
                                 double slopeWeight,
                                 RouteStrategyWeights strategyWeights,
                                 RouteStrategy strategy,
                                 RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy,
                                 long version,
                                 boolean campusOnly,
                                 long expiryToken,
                                 String tenantId) {
        long slat = quantize(req.getStartLat());
        long slng = quantize(req.getStartLng());
        long elat = quantize(req.getEndLat());
        long elng = quantize(req.getEndLng());
        long sw = Math.round(slopeWeight * 1000);
        long stairsPenalty = Math.round(strategyWeights.getStairsPenalty() * 1000);
        long slopePenalty = Math.round(strategyWeights.getSlopePenalty() * 1000);
        long constructionPenalty = Math.round(strategyWeights.getConstructionPenalty() * 1000);
        long passabilityMinClamp = Math.round(passabilityPolicy.getPassabilityMinClamp() * 1000);
        long passabilityWeightFactor = Math.round(passabilityPolicy.getPassabilityWeightFactor() * 1000);
        String passabilityEnabled = passabilityPolicy.isPassabilityPenaltyEnabled() ? "1" : "0";
        String strat = strategy != null ? strategy.name() : "BALANCED";
        String campusFlag = campusOnly ? "1" : "0";
        return req.getMode().name() + "|" + strat + "|" + slat + "," + slng + "->" + elat + "," + elng
                + "|sw=" + sw
                + "|w.stairs=" + stairsPenalty
                + "|w.slope=" + slopePenalty
                + "|w.construction=" + constructionPenalty
                + "|w.passability.enabled=" + passabilityEnabled
                + "|w.passability.clamp=" + passabilityMinClamp
                + "|w.passability.factor=" + passabilityWeightFactor
                + "|campus=" + campusFlag + "|v=" + version + "|exp=" + expiryToken
                + "|tenant=" + (tenantId == null ? "default" : tenantId);
    }

    private long quantize(double v) {
        // 1e-5 度量级（约 1m），用于缓存 key；不影响真实计算
        return Math.round(v * 100000);
    }

    private double defaultSlopeWeight(RouteStrategy strategy, TravelMode mode) {
        if (strategy == RouteStrategy.SHORTEST) {
            return 0.0;
        }
        if (strategy == RouteStrategy.SAFEST) {
            return mode == TravelMode.WHEELCHAIR ? 0.8 : 0.6;
        }
        return 0.2;
    }

    private String strategyLabel(RouteStrategy strategy) {
        if (strategy == RouteStrategy.SHORTEST) return "最短";
        if (strategy == RouteStrategy.SAFEST) return "最安全";
        return "均衡";
    }


    private GraphSnapshot loadGraphWithAnchors(double startLat, double startLng, int startLevel, double endLat, double endLng, int endLevel, boolean campusOnly, long version, String tenantId) {
        CachedGraph baseGraph = getCachedGraph(version, tenantId);

        CampusBounds campusBounds = campusOnly ? requireCampusBounds() : null;
        if (campusOnly && campusBounds != null) {
            if (!campusBounds.contains(startLat, startLng)) {
                throw RouteException.startOutOfBounds();
            }
            if (!campusBounds.contains(endLat, endLng)) {
                throw RouteException.endOutOfBounds();
            }
        }

        Map<Long, NodePoint> nodeMap = new HashMap<>();
        for (Map.Entry<Long, NodePoint> entry : baseGraph.nodes.entrySet()) {
            double lat = entry.getValue().lat;
            double lng = entry.getValue().lng;
            int lvl = entry.getValue().level;
            if (campusBounds != null && !campusBounds.contains(lat, lng)) {
                continue;
            }
            nodeMap.put(entry.getKey(), new NodePoint(lat, lng, lvl));
        }

        Set<Long> disabledEdgeIds = new HashSet<>(obstacleEffectRepository.findActiveDisabledEdgeIdsByTenant(Instant.now(), tenantId));

        Map<Long, List<EdgeData>> adj = new HashMap<>();
        SnapCandidate bestStart = null;
        SnapCandidate bestEnd = null;
        for (EdgeSegment e : baseGraph.edges) {
            if (disabledEdgeIds.contains(e.edgeId)) {
                continue;
            }
            NodePoint fromP = nodeMap.get(e.fromId);
            NodePoint toP = nodeMap.get(e.toId);
            if (fromP == null || toP == null) {
                continue;
            }

            boolean startLevelMatch = (fromP.level == startLevel || toP.level == startLevel);
            if (startLevelMatch) {
                Projection pStart = projectToSegmentMeters(startLng, startLat, fromP.lng, fromP.lat, toP.lng, toP.lat);
                if (pStart != null) {
                    if (bestStart == null || pStart.distanceM < bestStart.distanceToSegmentM) {
                        bestStart = new SnapCandidate(e, e.fromId, e.toId, fromP, toP, pStart);
                    }
                }
            }

            boolean endLevelMatch = (fromP.level == endLevel || toP.level == endLevel);
            if (endLevelMatch) {
                Projection pEnd = projectToSegmentMeters(endLng, endLat, fromP.lng, fromP.lat, toP.lng, toP.lat);
                if (pEnd != null) {
                    if (bestEnd == null || pEnd.distanceM < bestEnd.distanceToSegmentM) {
                        bestEnd = new SnapCandidate(e, e.fromId, e.toId, fromP, toP, pEnd);
                    }
                }
            }

            EdgeData d = new EdgeData(
                    e.edgeId,
                    e.toId,
                    e.distanceM,
                    e.oneway,
                    e.hasStairs,
                    e.isElevator,
                    e.slopeLevel,
                    e.accessibleDefault,
                    e.baseCost,
                    e.passability
            );

            adj.computeIfAbsent(e.fromId, k -> new ArrayList<>()).add(d);
        }

        if (nodeMap.isEmpty() || adj.isEmpty()) {
            if (campusOnly) {
                throw new BusinessException(ErrorCode.GRAPH_NOT_LOADED, "校园内路网为空，请先导入校内路网数据");
            }
            throw new BusinessException(ErrorCode.GRAPH_NOT_LOADED, "graph is empty; please import graph data first");
        }

        if (maxSnapMeters > 0 && bestStart != null && bestStart.distanceToSegmentM > maxSnapMeters) {
            bestStart = null;
        }
        if (maxSnapMeters > 0 && bestEnd != null && bestEnd.distanceToSegmentM > maxSnapMeters) {
            bestEnd = null;
        }

        long startNodeId;
        if (bestStart != null) {
            startNodeId = -1L;
            nodeMap.put(startNodeId, new NodePoint(bestStart.proj.lat, bestStart.proj.lng, startLevel));
            addAnchorEdges(adj, startNodeId, bestStart, true);
        } else {
            GraphSnapshot graph = new GraphSnapshot(nodeMap, adj, 0, 0, null, null);
            startNodeId = findNearestNodeId(graph, startLat, startLng, startLevel, "起点", campusBounds, tenantId);
        }

        long endNodeId;
        if (bestEnd != null) {
            endNodeId = -2L;
            nodeMap.put(endNodeId, new NodePoint(bestEnd.proj.lat, bestEnd.proj.lng, endLevel));
            addAnchorEdges(adj, endNodeId, bestEnd, false);
        } else {
            GraphSnapshot graph = new GraphSnapshot(nodeMap, adj, 0, 0, null, null);
            endNodeId = findNearestNodeId(graph, endLat, endLng, endLevel, "终点", campusBounds, tenantId);
        }

        // 起终点落在同一条（物理）边：加一条“边内直达”，避免绕到端点再回来
        if (bestStart != null && bestEnd != null) {
            boolean sameEdgeId = Objects.equals(bestStart.edge.edgeId, bestEnd.edge.edgeId);
            boolean sameUndirectedSegment = !bestStart.edge.oneway
                    && !bestEnd.edge.oneway
                    && bestStart.fromId == bestEnd.toId
                    && bestStart.toId == bestEnd.fromId;

            if (sameEdgeId || sameUndirectedSegment) {
                double t1 = bestStart.proj.t;
                double t2 = bestEnd.proj.t;
                // 若 end 命中的是反向副本，把 t 转回 start 的方向
                if (sameUndirectedSegment) {
                    t2 = 1.0 - t2;
                }

                double distM;
                double cost;
                if (bestStart.edge.oneway) {
                    // 单行：只允许沿 from->to 正向“边内直达”
                    if (t2 + 1e-9 >= t1) {
                        double dt = Math.max(0, t2 - t1);
                        distM = bestStart.edge.distanceM * dt;
                        cost = bestStart.edge.baseCost * dt;
                    } else {
                        distM = -1;
                        cost = -1;
                    }
                } else {
                    double dt = Math.abs(t2 - t1);
                    distM = bestStart.edge.distanceM * dt;
                    cost = bestStart.edge.baseCost * dt;
                }

                if (distM >= 0) {
                    adj.computeIfAbsent(startNodeId, k -> new ArrayList<>()).add(new EdgeData(
                            bestStart.edge.edgeId,
                            endNodeId,
                            distM,
                            false,
                            bestStart.edge.hasStairs,
                            bestStart.edge.isElevator,
                            bestStart.edge.slopeLevel,
                            bestStart.edge.accessibleDefault,
                            cost
                    ));
                }
            }
        }

        RouteResponse.SnapInfo startSnap;
        if (bestStart != null) {
            startSnap = new RouteResponse.SnapInfo(
                    "EDGE",
                    bestStart.distanceToSegmentM,
                    bestStart.proj.lng,
                    bestStart.proj.lat,
                    startLevel,
                    bestStart.edge.edgeId,
                    null,
                    bestStart.fromId,
                    bestStart.toId
            );
        } else {
            NodePoint startPoint = nodeMap.get(startNodeId);
            double dist = startPoint != null
                    ? haversineMeters(startLng, startLat, startPoint.lng, startPoint.lat)
                    : 0.0;
            startSnap = new RouteResponse.SnapInfo(
                    "NODE",
                    dist,
                    startPoint != null ? startPoint.lng : startLng,
                    startPoint != null ? startPoint.lat : startLat,
                    startPoint != null ? startPoint.level : startLevel,
                    null,
                    startNodeId,
                    null,
                    null
            );
        }

        RouteResponse.SnapInfo endSnap;
        if (bestEnd != null) {
            endSnap = new RouteResponse.SnapInfo(
                    "EDGE",
                    bestEnd.distanceToSegmentM,
                    bestEnd.proj.lng,
                    bestEnd.proj.lat,
                    endLevel,
                    bestEnd.edge.edgeId,
                    null,
                    bestEnd.fromId,
                    bestEnd.toId
            );
        } else {
            NodePoint endPoint = nodeMap.get(endNodeId);
            double dist = endPoint != null
                    ? haversineMeters(endLng, endLat, endPoint.lng, endPoint.lat)
                    : 0.0;
            endSnap = new RouteResponse.SnapInfo(
                    "NODE",
                    dist,
                    endPoint != null ? endPoint.lng : endLng,
                    endPoint != null ? endPoint.lat : endLat,
                    endPoint != null ? endPoint.level : endLevel,
                    null,
                    endNodeId,
                    null,
                    null
            );
        }

        return new GraphSnapshot(nodeMap, adj, startNodeId, endNodeId, startSnap, endSnap);
    }

    private void addAnchorEdges(Map<Long, List<EdgeData>> adj, long anchorId, SnapCandidate c, boolean isStart) {
        // isStart=true: anchor -> endpoints; isStart=false: endpoints -> anchor
        double t = Math.max(0, Math.min(1, c.proj.t));
        double costToFrom = c.edge.baseCost * t;
        double costToTo = c.edge.baseCost * (1 - t);
        double distToFrom = c.edge.distanceM * t;
        double distToTo = c.edge.distanceM * (1 - t);

        boolean oneway = c.edge.oneway;
        boolean atFrom = t <= 1e-9;
        boolean atTo = t >= 1.0 - 1e-9;

        if (isStart) {
            // 起点临时节点：从 anchor 出发连接到图
            if (!oneway) {
                adj.computeIfAbsent(anchorId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, c.fromId, distToFrom, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToFrom));
                adj.computeIfAbsent(anchorId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, c.toId, distToTo, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToTo));
            } else {
                // 单行边：只允许沿 from->to 正向离开；但如果 anchor 恰好落在端点，允许零距离接入该端点
                if (atFrom) {
                    adj.computeIfAbsent(anchorId, k -> new ArrayList<>()).add(new EdgeData(
                            c.edge.edgeId, c.fromId, 0, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, 0));
                }
                adj.computeIfAbsent(anchorId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, c.toId, distToTo, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToTo));
            }
        } else {
            // 终点临时节点：从图到 anchor
            if (!oneway) {
                adj.computeIfAbsent(c.fromId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, anchorId, distToFrom, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToFrom));
                adj.computeIfAbsent(c.toId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, anchorId, distToTo, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToTo));
            } else {
                // 单行边：允许 from->anchor（正向）；若 anchor 恰好落在 to 端点，也允许 to->anchor 零距离接入
                adj.computeIfAbsent(c.fromId, k -> new ArrayList<>()).add(new EdgeData(
                        c.edge.edgeId, anchorId, distToFrom, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, costToFrom));
                if (atTo) {
                    adj.computeIfAbsent(c.toId, k -> new ArrayList<>()).add(new EdgeData(
                            c.edge.edgeId, anchorId, 0, false, c.edge.hasStairs, c.edge.isElevator, c.edge.slopeLevel, c.edge.accessibleDefault, 0));
                }
            }
        }
    }

    private Projection projectToSegmentMeters(double pLng, double pLat, double aLng, double aLat, double bLng, double bLat) {
        double refLat = (aLat + bLat + pLat) / 3.0;
        MetersPoint p = metersXY(pLng, pLat, refLat);
        MetersPoint a = metersXY(aLng, aLat, refLat);
        MetersPoint b = metersXY(bLng, bLat, refLat);

        double abx = b.x - a.x;
        double aby = b.y - a.y;
        double apx = p.x - a.x;
        double apy = p.y - a.y;

        double ab2 = abx * abx + aby * aby;
        if (ab2 <= 1e-9) return null;

        double t = (apx * abx + apy * aby) / ab2;
        if (t < 0) t = 0;
        if (t > 1) t = 1;

        double cx = a.x + t * abx;
        double cy = a.y + t * aby;

        double dx = p.x - cx;
        double dy = p.y - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        // 反算回经纬度（近似）
        double mPerDegLat = 111320.0;
        double mPerDegLng = 111320.0 * Math.cos(Math.toRadians(refLat));
        double projLng = cx / mPerDegLng;
        double projLat = cy / mPerDegLat;

        return new Projection(t, projLng, projLat, dist);
    }

    private MetersPoint metersXY(double lng, double lat, double refLat) {
        double mPerDegLat = 111320.0;
        double mPerDegLng = 111320.0 * Math.cos(Math.toRadians(refLat));
        return new MetersPoint(lng * mPerDegLng, lat * mPerDegLat);
    }

    private int countRisksOnPath(GraphSnapshot graph, List<Long> nodeIds, TravelMode mode) {
        if (nodeIds == null || nodeIds.size() < 2) return 0;

        int risks = 0;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            long from = nodeIds.get(i);
            long to = nodeIds.get(i + 1);
            EdgeData e = findEdge(graph, from, to);
            if (e == null) continue;

            boolean isRisk = false;
            // 步行：楼梯 / 较大坡度都算风险
            if (mode == TravelMode.WALK) {
                if (e.hasStairs) isRisk = true;
                if (e.slopeLevel >= 3) isRisk = true;
            }

            // 轮椅：已过滤楼梯/不可达；较大坡度算风险
            if (mode == TravelMode.WHEELCHAIR) {
                if (e.slopeLevel >= 3) isRisk = true;
            }

            if (isRisk) risks++;
        }
        return risks;
    }

    private EdgeData findEdge(GraphSnapshot graph, long fromNodeId, long toNodeId) {
        List<EdgeData> list = graph.adj.getOrDefault(fromNodeId, Collections.emptyList());
        for (EdgeData e : list) {
            if (e.toNodeId == toNodeId) return e;
        }
        return null;
    }

    private long estimateDurationSec(double distanceM, TravelMode mode, int riskCount) {
        // 简单估算：WALK ~ 1.4m/s（约 5km/h）；WHEELCHAIR ~ 1.0m/s
        double speed = (mode == TravelMode.WHEELCHAIR) ? 1.0 : 1.4;

        // 风险段（陡坡/楼梯）给一点点“慢速”惩罚，突出工程可解释性
        double riskFactor = 1.0 + Math.min(0.3, riskCount * 0.03);
        double sec = (distanceM / speed) * riskFactor;
        if (sec < 0) sec = 0;
        return (long) Math.round(sec);
    }

    private List<String> buildExplain(GraphSnapshot graph,
                                      List<Long> nodeIds,
                                      TravelMode mode,
                                      double slopeWeight,
                                      RouteStrategyWeights strategyWeights,
                                      RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy,
                                      int riskCount,
                                      RouteStrategy strategy) {
        List<String> explain = new ArrayList<>();
        int segments = 0;
        int stairs = 0;
        int steep = 0;
        int inaccessible = 0;
        int maxSlope = 0;

        if (nodeIds != null && nodeIds.size() >= 2) {
            for (int i = 0; i < nodeIds.size() - 1; i++) {
                long from = nodeIds.get(i);
                long to = nodeIds.get(i + 1);
                EdgeData e = findEdge(graph, from, to);
                if (e == null) {
                    continue;
                }
                segments++;
                if (e.hasStairs) stairs++;
                if (!e.accessibleDefault) inaccessible++;
                if (e.slopeLevel >= 3) steep++;
                if (e.slopeLevel > maxSlope) maxSlope = e.slopeLevel;
            }
        }

        explain.add("策略：" + strategyLabel(strategy));
        explain.add(String.format("坡度权重：%.2f", slopeWeight));
        explain.add(String.format("细粒度权重：stairs=%.2f slope=%.2f construction=%.2f",
                strategyWeights.getStairsPenalty(),
                strategyWeights.getSlopePenalty(),
                strategyWeights.getConstructionPenalty()));
        explain.add(String.format("通行概率惩罚：enabled=%s minClamp=%.2f factor=%.2f source=%s",
                passabilityPolicy.isPassabilityPenaltyEnabled(),
                passabilityPolicy.getPassabilityMinClamp(),
                passabilityPolicy.getPassabilityWeightFactor(),
                passabilityPolicy.getSource()));
        explain.add("路径线段数：" + segments);
        explain.add("风险段数：" + riskCount);
        if (mode == TravelMode.WHEELCHAIR) {
            explain.add("轮椅模式：过滤楼梯/不可达边");
        }
        if (strategy == RouteStrategy.SAFEST) {
            explain.add("安全惩罚：楼梯/不可达边");
        }
        explain.add("楼梯段：" + stairs);
        explain.add("坡度>=3段：" + steep);
        explain.add("最大坡度等级：" + maxSlope);
        explain.add("不可达段：" + inaccessible);
        return explain;
    }

    private List<RouteResponse.Instruction> buildTurnByTurn(List<RouteResponse.LngLat> path,
                                                              List<RouteResponse.LevelTransition> levelTransitions) {
        List<RouteResponse.Instruction> out = new ArrayList<>();
        if (path == null || path.isEmpty()) {
            return out;
        }

        // Index level transitions by approximate location for matching
        java.util.Map<String, RouteResponse.LevelTransition> transitionMap = new java.util.HashMap<>();
        if (levelTransitions != null) {
            for (RouteResponse.LevelTransition lt : levelTransitions) {
                String key = Math.round(lt.getLat() * 10000) + "," + Math.round(lt.getLng() * 10000);
                transitionMap.put(key, lt);
            }
        }

        out.add(new RouteResponse.Instruction("START", "从起点出发", 0));
        if (path.size() < 2) {
            out.add(new RouteResponse.Instruction("ARRIVE", "到达终点", 0));
            return out;
        }

        int nSeg = path.size() - 1;
        double[] segDist = new double[nSeg];
        double[] segBearing = new double[nSeg];
        for (int i = 0; i < nSeg; i++) {
            RouteResponse.LngLat a = path.get(i);
            RouteResponse.LngLat b = path.get(i + 1);
            segDist[i] = haversineMeters(a.getLng(), a.getLat(), b.getLng(), b.getLat());
            segBearing[i] = bearingDeg(a.getLng(), a.getLat(), b.getLng(), b.getLat());
        }

        double accum = 0;
        for (int i = 0; i < nSeg; i++) {
            // Check for level transition at segment end point
            RouteResponse.LngLat endPt = path.get(i + 1);
            String key = Math.round(endPt.getLat() * 10000) + "," + Math.round(endPt.getLng() * 10000);
            RouteResponse.LevelTransition lt = transitionMap.get(key);

            if (i > 0) {
                double delta = normalizeDeltaDeg(segBearing[i] - segBearing[i - 1]);
                if (Math.abs(delta) >= 35) {
                    if (accum >= 5) {
                        out.add(new RouteResponse.Instruction("GO", String.format("直行 %.0f 米", accum), accum));
                    }
                    if (delta > 0) {
                        out.add(new RouteResponse.Instruction("TURN_RIGHT", "右转", 0));
                    } else {
                        out.add(new RouteResponse.Instruction("TURN_LEFT", "左转", 0));
                    }
                    accum = 0;
                }
            }
            accum += segDist[i];

            // Insert level transition instruction
            if (lt != null) {
                if (accum >= 5) {
                    out.add(new RouteResponse.Instruction("GO", String.format("直行 %.0f 米", accum), accum));
                    accum = 0;
                }
                String via = lt.getVia();
                String viaText;
                if ("ELEVATOR".equals(via)) {
                    viaText = "乘电梯";
                } else if ("STAIRS".equals(via)) {
                    viaText = "走楼梯";
                } else {
                    viaText = "经坡道";
                }
                String direction = lt.getToLevel() > lt.getFromLevel() ? "上" : "下";
                String text = viaText + direction + "到" + lt.getToLevel() + "层";
                out.add(new RouteResponse.Instruction("LEVEL_CHANGE", text, 0));
            }
        }

        if (accum >= 5) {
            out.add(new RouteResponse.Instruction("GO", String.format("直行 %.0f 米", accum), accum));
        }
        out.add(new RouteResponse.Instruction("ARRIVE", "到达终点", 0));
        return out;
    }

    private double bearingDeg(double lng1, double lat1, double lng2, double lat2) {
        // 从点1指向点2的方位角（0=北，90=东）
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double dLambda = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) - Math.sin(phi1) * Math.cos(phi2) * Math.cos(dLambda);
        double theta = Math.atan2(y, x);
        double deg = Math.toDegrees(theta);
        deg = (deg + 360) % 360;
        return deg;
    }

    private double normalizeDeltaDeg(double deg) {
        // 归一化到 (-180, 180]
        double d = deg % 360;
        if (d <= -180) d += 360;
        if (d > 180) d -= 360;
        return d;
    }

    private CampusBounds requireCampusBounds() {
        CampusBounds bounds = resolveCampusBounds();
        if (bounds == null) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "校园边界未配置，请设置 app.campus.bbox");
        }
        return bounds;
    }

    private CampusBounds resolveCampusBounds() {
        CampusBounds cached = campusBounds;
        if (cached != null) {
            return cached;
        }
        String raw = campusBboxRaw;
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        CampusBounds parsed = CampusBounds.parse(raw);
        if (parsed == null) {
            log.warn("Invalid campus bbox config: {}", raw);
            return null;
        }
        campusBounds = parsed;
        return parsed;
    }

    private CachedGraph getCachedGraph(long version, String tenantId) {
        CachedGraph cached = cachedGraph;
        if (cached != null && cached.version == version && Objects.equals(cached.tenantId, tenantId)) {
            return cached;
        }
        synchronized (graphLock) {
            cached = cachedGraph;
            if (cached != null && cached.version == version && Objects.equals(cached.tenantId, tenantId)) {
                return cached;
            }
            CachedGraph next = loadGraphFromDb(version, tenantId);
            cachedGraph = next;
            return next;
        }
    }

    private CachedGraph loadGraphFromDb(long version, String tenantId) {
        List<NodeEntity> nodes = nodeRepository.findAllByTenantId(tenantId);
        List<EdgeEntity> edges = edgeRepository.findAllWithNodesByTenant(tenantId);

        if (nodes.isEmpty() || edges.isEmpty()) {
            throw new BusinessException(ErrorCode.GRAPH_NOT_LOADED, "graph is empty; please import graph data first");
        }

        Map<Long, NodePoint> nodeMap = new HashMap<>();
        for (NodeEntity n : nodes) {
            if (n == null || n.getId() == null || n.getLat() == null || n.getLng() == null) {
                continue;
            }
            nodeMap.put(n.getId(), new NodePoint(n.getLat().doubleValue(), n.getLng().doubleValue(), n.getLevel()));
        }

        List<EdgeSegment> segments = new ArrayList<>();
        for (EdgeEntity e : edges) {
            if (e == null || e.getFromNode() == null || e.getToNode() == null) {
                continue;
            }
            Long fromId = e.getFromNode().getId();
            Long toId = e.getToNode().getId();
            if (fromId == null || toId == null) {
                continue;
            }
            NodePoint fromP = nodeMap.get(fromId);
            NodePoint toP = nodeMap.get(toId);
            if (fromP == null || toP == null) {
                continue;
            }
            segments.add(new EdgeSegment(
                    e.getId(),
                    fromId,
                    toId,
                    e.getDistanceM(),
                    e.isOneway(),
                    e.isHasStairs(),
                    e.isElevator(),
                    e.getSlopeLevel(),
                    e.isAccessibleDefault(),
                    e.getBaseCost(),
                    e.getPassabilityProbability(),
                    fromP,
                    toP
            ));
        }

        return new CachedGraph(version, tenantId, nodeMap, segments);
    }

    private long findNearestNodeId(GraphSnapshot graph,
                                   double lat,
                                   double lng,
                                   int targetLevel,
                                   String label,
                                   CampusBounds campusBounds,
                                   String tenantId) {
        // 优先用 DB 范围查询减少候选；找不到再逐步扩大
        if (usePostgisKnn) {
            try {
                double radius = maxSnapMeters > 0 ? maxSnapMeters : 2000.0;
                Long id;
                if (campusBounds != null) {
                    id = nodeRepository.findNearestIdWithinMetersAndBboxByTenant(
                            lat,
                            lng,
                            tenantId,
                            targetLevel,
                            radius,
                            campusBounds.minLat,
                            campusBounds.maxLat,
                            campusBounds.minLng,
                            campusBounds.maxLng
                    );
                } else {
                    id = nodeRepository.findNearestIdWithinMetersByTenant(lat, lng, tenantId, targetLevel, radius);
                }

                if (id != null) {
                    return id;
                }
                if (maxSnapMeters > 0) {
                    throw RouteException.snapFailed(label + "不在路网范围内");
                }
            } catch (Exception ignored) {
                // Best-effort: if DB doesn't support PostGIS/H2, revert to the legacy approach.
            }
        }

        double[] radiuses = new double[]{0.0008, 0.0016, 0.0032, 0.0064, 0.0128};
        NodeEntity best = null;
        double bestDist = Double.POSITIVE_INFINITY;

        for (double r : radiuses) {
            BigDecimal minLat = BigDecimal.valueOf(lat - r);
            BigDecimal maxLat = BigDecimal.valueOf(lat + r);
            BigDecimal minLng = BigDecimal.valueOf(lng - r);
            BigDecimal maxLng = BigDecimal.valueOf(lng + r);

            List<NodeEntity> candidates = nodeRepository.findNearbyByTenant(
                    tenantId,
                    minLat,
                    maxLat,
                    minLng,
                    maxLng,
                    PageRequest.of(0, 200)
            );
            for (NodeEntity n : candidates) {
                if (n.getLat() == null || n.getLng() == null) {
                    continue;
                }
                if (n.getLevel() != null && n.getLevel() != targetLevel) {
                    continue;
                }
                double candidateLat = n.getLat().doubleValue();
                double candidateLng = n.getLng().doubleValue();
                if (campusBounds != null && !campusBounds.contains(candidateLat, candidateLng)) {
                    continue;
                }
                double d = haversineMeters(lng, lat, candidateLng, candidateLat);
                if (d < bestDist) {
                    bestDist = d;
                    best = n;
                }
            }
            if (best != null) {
                break;
            }
        }

        if (best != null) {
            if (maxSnapMeters > 0 && bestDist > maxSnapMeters) {
                throw RouteException.snapFailed(label + "不在路网范围内");
            }
            return best.getId();
        }

        // 兜底：没有任何候选就用内存图全量扫描（路网很大时不建议，但毕设范围OK）
        long bestId = -1;
        for (Map.Entry<Long, NodePoint> e : graph.nodes.entrySet()) {
            NodePoint p = e.getValue();
            if (p.level != targetLevel) {
                continue;
            }
            double d = haversineMeters(lng, lat, p.lng, p.lat);
            if (d < bestDist) {
                bestDist = d;
                bestId = e.getKey();
            }
        }

        if (bestId < 0) {
            // 如果指定层级找不到，尝试找任意层级（还是严格一点好？）
            // 这里我们严格一点，找不到就报错，或者 fall back
            throw RouteException.snapFailed(label + "附近找不到层级 " + targetLevel + " 的节点");
        }
        if (maxSnapMeters > 0 && bestDist > maxSnapMeters) {
            throw RouteException.snapFailed(label + "不在路网范围内");
        }
        return bestId;
    }

    private AStarResult aStar(GraphSnapshot graph,
                              long startId,
                              long goalId,
                              TravelMode mode,
                              double slopeWeight,
                              RouteStrategyWeights strategyWeights,
                              RouteStrategy strategy,
                              RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy passabilityPolicy) {
        PriorityQueue<NodeRecord> open = new PriorityQueue<>(Comparator.comparingDouble(a -> a.f));
        Map<Long, Double> bestG = new HashMap<>();
        Map<Long, Long> prev = new HashMap<>();

        bestG.put(startId, 0.0);
        open.add(new NodeRecord(startId, 0.0, heuristic(graph, startId, goalId)));

        int visited = 0;
        int relaxations = 0;

        Set<Long> closed = new HashSet<>();

        while (!open.isEmpty()) {
            NodeRecord cur = open.poll();
            if (closed.contains(cur.nodeId)) {
                continue;
            }
            closed.add(cur.nodeId);
            visited++;

            if (cur.nodeId == goalId) {
                break;
            }

            List<EdgeData> neighbors = graph.adj.getOrDefault(cur.nodeId, Collections.emptyList());
            for (EdgeData e : neighbors) {
                if (mode == TravelMode.WHEELCHAIR) {
                    if (e.hasStairs) continue;
                    if (!e.accessibleDefault && !e.isElevator) continue;
                }

                double w = e.baseCost;

                if (e.isElevator) {
                    if (mode == TravelMode.WALK) {
                        w += 30.0; // Penalty for waiting
                    }
                    // Wheelchair: standard cost (preferred over detour)
                }

                if (strategy == RouteStrategy.SAFEST) {
                    if (e.hasStairs) {
                        w = w * 1.4;
                    }
                    if (!e.accessibleDefault) {
                        w = w * 1.2;
                    }
                }

                if (e.hasStairs) {
                    w = w * (1.0 + strategyWeights.getStairsPenalty());
                }
                if (!e.accessibleDefault) {
                    w = w * (1.0 + strategyWeights.getConstructionPenalty());
                }
                // 坡度惩罚：slopeLevel 0-5
                w = w * (1.0 + (Math.max(0, Math.min(5, e.slopeLevel)) * strategyWeights.getSlopePenalty()));

                w = applyPassabilityPenalty(w, e.passability, passabilityPolicy);

                double g2 = cur.g + w;
                Double old = bestG.get(e.toNodeId);
                if (old == null || g2 < old) {
                    bestG.put(e.toNodeId, g2);
                    prev.put(e.toNodeId, cur.nodeId);
                    double f2 = g2 + heuristic(graph, e.toNodeId, goalId);
                    open.add(new NodeRecord(e.toNodeId, g2, f2));
                    relaxations++;
                }
            }
        }

        if (!bestG.containsKey(goalId)) {
            // Keep an explicit error code for API + metrics.
            throw RouteException.notFound();
        }

        List<Long> path = new ArrayList<>();
        long cur = goalId;
        path.add(cur);
        while (cur != startId) {
            Long p = prev.get(cur);
            if (p == null) {
                break;
            }
            cur = p;
            path.add(cur);
        }
        Collections.reverse(path);

        double distanceM = pathDistanceMeters(graph, path);
        return new AStarResult(path, distanceM, visited, relaxations);
    }

    private RouteStrategyWeights sanitizeStrategyWeights(RouteStrategyWeights weights,
                                                         RouteStrategy strategy,
                                                         TravelMode mode,
                                                         double slopeWeight) {
        if (weights == null) {
            return defaultStrategyWeights(strategy, mode, slopeWeight);
        }
        return new RouteStrategyWeights(
                clamp(weights.getStairsPenalty(), 0.0, 3.0, defaultStrategyWeights(strategy, mode, slopeWeight).getStairsPenalty()),
                clamp(weights.getSlopePenalty(), 0.0, 1.0, clamp(slopeWeight, 0.0, 1.0, 0.2)),
                clamp(weights.getConstructionPenalty(), 0.0, 3.0, defaultStrategyWeights(strategy, mode, slopeWeight).getConstructionPenalty())
        );
    }

    private double applyPassabilityPenalty(double baseWeight,
                                           double passability,
                                           RoutePassabilityPolicyService.ResolvedRoutePassabilityPolicy policy) {
        if (policy == null || !policy.isPassabilityPenaltyEnabled()) {
            return baseWeight;
        }
        double safePassability = Math.max(policy.getPassabilityMinClamp(), passability);
        // factor=0 => no penalty, factor=1 => previous behavior, factor in (0,2] smooth adjust.
        double penaltyMultiplier = 1.0 + policy.getPassabilityWeightFactor() * ((1.0 / safePassability) - 1.0);
        return baseWeight * penaltyMultiplier;
    }

    private RouteStrategyWeights defaultStrategyWeights(RouteStrategy strategy, TravelMode mode, double slopeWeight) {
        double safeSlopeWeight = clamp(slopeWeight, 0.0, 1.0, 0.2);
        if (strategy == RouteStrategy.SHORTEST) {
            return new RouteStrategyWeights(0.0, 0.0, 0.0);
        }
        if (strategy == RouteStrategy.SAFEST) {
            return new RouteStrategyWeights(mode == TravelMode.WHEELCHAIR ? 1.6 : 1.1, safeSlopeWeight, 1.0);
        }
        return new RouteStrategyWeights(mode == TravelMode.WHEELCHAIR ? 0.8 : 0.3, safeSlopeWeight, 0.3);
    }

    private double clamp(Double value, double min, double max, double fallback) {
        double v = value == null ? fallback : value;
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }

    private double pathDistanceMeters(GraphSnapshot graph, List<Long> nodeIds) {
        double sum = 0;
        for (int i = 0; i < nodeIds.size() - 1; i++) {
            NodePoint a = graph.nodes.get(nodeIds.get(i));
            NodePoint b = graph.nodes.get(nodeIds.get(i + 1));
            if (a == null || b == null) continue;
            sum += haversineMeters(a.lng, a.lat, b.lng, b.lat);
        }
        return sum;
    }

    private double heuristic(GraphSnapshot graph, long a, long b) {
        NodePoint p1 = graph.nodes.get(a);
        NodePoint p2 = graph.nodes.get(b);
        if (p1 == null || p2 == null) return 0;
        double h = haversineMeters(p1.lng, p1.lat, p2.lng, p2.lat);
        double v = Math.abs(p1.level - p2.level) * 30.0;
        return h + v;
    }

    private double haversineMeters(double lng1, double lat1, double lng2, double lat2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private static class CampusBounds {
        final double minLat;
        final double minLng;
        final double maxLat;
        final double maxLng;

        CampusBounds(double minLat, double minLng, double maxLat, double maxLng) {
            this.minLat = minLat;
            this.minLng = minLng;
            this.maxLat = maxLat;
            this.maxLng = maxLng;
        }

        boolean contains(double lat, double lng) {
            return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
        }

        static CampusBounds parse(String raw) {
            if (raw == null) {
                return null;
            }
            String cleaned = raw.trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            String[] parts = cleaned.split(",");
            if (parts.length != 4) {
                return null;
            }
            try {
                double lat1 = Double.parseDouble(parts[0].trim());
                double lng1 = Double.parseDouble(parts[1].trim());
                double lat2 = Double.parseDouble(parts[2].trim());
                double lng2 = Double.parseDouble(parts[3].trim());
                double minLat = Math.min(lat1, lat2);
                double maxLat = Math.max(lat1, lat2);
                double minLng = Math.min(lng1, lng2);
                double maxLng = Math.max(lng1, lng2);
                return new CampusBounds(minLat, minLng, maxLat, maxLng);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
    }

    private static class GraphSnapshot {
        final Map<Long, NodePoint> nodes;
        final Map<Long, List<EdgeData>> adj;
        final long startNodeId;
        final long endNodeId;
        final RouteResponse.SnapInfo startSnap;
        final RouteResponse.SnapInfo endSnap;

        GraphSnapshot(Map<Long, NodePoint> nodes,
                      Map<Long, List<EdgeData>> adj,
                      long startNodeId,
                      long endNodeId,
                      RouteResponse.SnapInfo startSnap,
                      RouteResponse.SnapInfo endSnap) {
            this.nodes = nodes;
            this.adj = adj;
            this.startNodeId = startNodeId;
            this.endNodeId = endNodeId;
            this.startSnap = startSnap;
            this.endSnap = endSnap;
        }
    }

    private static class CachedGraph {
        final long version;
        final String tenantId;
        final Map<Long, NodePoint> nodes;
        final List<EdgeSegment> edges;

        CachedGraph(long version, String tenantId, Map<Long, NodePoint> nodes, List<EdgeSegment> edges) {
            this.version = version;
            this.tenantId = tenantId;
            this.nodes = nodes;
            this.edges = edges;
        }
    }

    @SuppressWarnings("unused")
    private static class EdgeSegment {
        final long edgeId;
        final long fromId;
        final long toId;
        final double distanceM;
        final boolean oneway;
        final boolean hasStairs;
        final boolean isElevator;
        final int slopeLevel;
        final boolean accessibleDefault;
        final double baseCost;
        final double passability;
        final NodePoint fromPoint;
        final NodePoint toPoint;

        EdgeSegment(long edgeId,
                    long fromId,
                    long toId,
                    double distanceM,
                    boolean oneway,
                    boolean hasStairs,
                    boolean isElevator,
                    int slopeLevel,
                    boolean accessibleDefault,
                    double baseCost,
                    double passability,
                    NodePoint fromPoint,
                    NodePoint toPoint) {
            this.edgeId = edgeId;
            this.fromId = fromId;
            this.toId = toId;
            this.distanceM = distanceM;
            this.oneway = oneway;
            this.hasStairs = hasStairs;
            this.isElevator = isElevator;
            this.slopeLevel = slopeLevel;
            this.accessibleDefault = accessibleDefault;
            this.baseCost = baseCost;
            this.passability = passability;
            this.fromPoint = fromPoint;
            this.toPoint = toPoint;
        }
    }

    private static class Projection {
        final double t;
        final double lng;
        final double lat;
        final double distanceM;

        Projection(double t, double lng, double lat, double distanceM) {
            this.t = t;
            this.lng = lng;
            this.lat = lat;
            this.distanceM = distanceM;
        }
    }

    private static class MetersPoint {
        final double x;
        final double y;

        MetersPoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    @SuppressWarnings("unused")
    private static class SnapCandidate {
        final EdgeSegment edge;
        final long fromId;
        final long toId;
        final NodePoint fromPoint;
        final NodePoint toPoint;
        final Projection proj;
        final double distanceToSegmentM;

        SnapCandidate(EdgeSegment edge, long fromId, long toId, NodePoint fromPoint, NodePoint toPoint, Projection proj) {
            this.edge = edge;
            this.fromId = fromId;
            this.toId = toId;
            this.fromPoint = fromPoint;
            this.toPoint = toPoint;
            this.proj = proj;
            this.distanceToSegmentM = proj.distanceM;
        }
    }

    private static class NodePoint {
        final double lat;
        final double lng;
        final int level;

        NodePoint(double lat, double lng, int level) {
            this.lat = lat;
            this.lng = lng;
            this.level = level;
        }
    }

    @SuppressWarnings("unused")
    private static class EdgeData {
        final long edgeId;
        final long toNodeId;
        final double distanceM;
        final boolean oneway;
        final boolean hasStairs;
        final boolean isElevator;
        final int slopeLevel;
        final boolean accessibleDefault;
        final double baseCost;
        final double passability;

        EdgeData(long edgeId,
                 long toNodeId,
                 double distanceM,
                 boolean oneway,
                 boolean hasStairs,
                 boolean isElevator,
                 int slopeLevel,
                 boolean accessibleDefault,
                 double baseCost,
                 double passability) {
            this.edgeId = edgeId;
            this.toNodeId = toNodeId;
            this.distanceM = distanceM;
            this.oneway = oneway;
            this.hasStairs = hasStairs;
            this.isElevator = isElevator;
            this.slopeLevel = slopeLevel;
            this.accessibleDefault = accessibleDefault;
            this.baseCost = baseCost;
            this.passability = passability;
        }

        EdgeData(long edgeId,
                 long toNodeId,
                 double distanceM,
                 boolean oneway,
                 boolean hasStairs,
                 boolean isElevator,
                 int slopeLevel,
                 boolean accessibleDefault,
                 double baseCost) {
            this(edgeId, toNodeId, distanceM, oneway, hasStairs, isElevator, slopeLevel, accessibleDefault, baseCost, 1.0);
        }
    }

    @SuppressWarnings("unused")
    private static class RouteStats {
        int segments;
        int stairsCount;
        int elevatorCount;
        int inaccessibleCount;
        int steepCount;
        int maxSlope;
    }

    private static class RouteComputation {
        final List<Long> pathNodeIds;
        final double distanceM;
        final long durationSec;
        final int riskCount;
        final int visited;
        final int relaxations;
        final RouteStats stats;

        RouteComputation(List<Long> pathNodeIds,
                         double distanceM,
                         long durationSec,
                         int riskCount,
                         int visited,
                         int relaxations,
                         RouteStats stats) {
            this.pathNodeIds = pathNodeIds;
            this.distanceM = distanceM;
            this.durationSec = durationSec;
            this.riskCount = riskCount;
            this.visited = visited;
            this.relaxations = relaxations;
            this.stats = stats;
        }
    }

    private static class NodeRecord {
        final long nodeId;
        final double g;
        final double f;

        NodeRecord(long nodeId, double g, double f) {
            this.nodeId = nodeId;
            this.g = g;
            this.f = f;
        }
    }

    private static class AStarResult {
        final List<Long> pathNodeIds;
        final double distanceM;
        final int visited;
        final int relaxations;

        AStarResult(List<Long> pathNodeIds, double distanceM, int visited, int relaxations) {
            this.pathNodeIds = pathNodeIds;
            this.distanceM = distanceM;
            this.visited = visited;
            this.relaxations = relaxations;
        }
    }
}
