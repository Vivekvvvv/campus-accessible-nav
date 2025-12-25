package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphImportRequest;
import com.demo.accessiblenav.graph.dto.GraphImportResponse;
import com.demo.accessiblenav.graph.dto.GraphIssuePoint;
import com.demo.accessiblenav.graph.dto.GraphRepairRequest;
import com.demo.accessiblenav.graph.dto.GraphRepairResponse;
import com.demo.accessiblenav.graph.dto.GraphRepairSummary;
import com.demo.accessiblenav.graph.dto.GraphReplaceRequest;
import com.demo.accessiblenav.graph.dto.GraphSnapshotResponse;
import com.demo.accessiblenav.graph.dto.GraphSnapshotSummary;
import com.demo.accessiblenav.graph.dto.GraphValidationReport;
import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.obstacle.ObstacleEffectRepository;
import com.demo.accessiblenav.obstacle.ObstacleEffectEntity;
import com.demo.accessiblenav.obstacle.ObstacleReportRepository;
import com.demo.accessiblenav.route.GraphVersionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class GraphImportService {

    private final NodeRepository nodeRepository;
    private final EdgeRepository edgeRepository;
    private final ObstacleEffectRepository obstacleEffectRepository;
    private final ObstacleReportRepository obstacleReportRepository;
    private final GraphSnapshotRepository snapshotRepository;
    private final GraphVersionService graphVersionService;
    private final OperationLogService logService;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, Counter> importCounters = new ConcurrentHashMap<>();

    private static final int ISSUE_POINT_LIMIT = 200;
    private static final double DEFAULT_REPAIR_MAX_CONNECT_METERS = 60.0;

    public GraphImportService(NodeRepository nodeRepository,
                              EdgeRepository edgeRepository,
                              ObstacleEffectRepository obstacleEffectRepository,
                              ObstacleReportRepository obstacleReportRepository,
                              GraphSnapshotRepository snapshotRepository,
                              GraphVersionService graphVersionService,
                              OperationLogService logService,
                              ObjectMapper objectMapper,
                              MeterRegistry meterRegistry) {
        this.nodeRepository = nodeRepository;
        this.edgeRepository = edgeRepository;
        this.obstacleEffectRepository = obstacleEffectRepository;
        this.obstacleReportRepository = obstacleReportRepository;
        this.snapshotRepository = snapshotRepository;
        this.graphVersionService = graphVersionService;
        this.logService = logService;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public GraphImportResponse importGraph(GraphImportRequest req) {
        return recordImport("admin", () -> importGraphInternal(req, true));
    }

    private GraphImportResponse importGraphInternal(GraphImportRequest req, boolean bumpVersion) {
        Map<String, NodeEntity> keyToNode = new HashMap<>();

        int upserted = 0;
        for (GraphImportRequest.NodeUpsert n : req.getNodes()) {
            BigDecimal lat = new BigDecimal(n.getLat()).setScale(7, RoundingMode.HALF_UP);
            BigDecimal lng = new BigDecimal(n.getLng()).setScale(7, RoundingMode.HALF_UP);

            NodeEntity node = nodeRepository.findByLatAndLng(lat, lng).orElse(null);
            if (node == null) {
                node = new NodeEntity();
                node.setLat(lat);
                node.setLng(lng);
                node.setNodeType(n.getNodeType());
                node.setExtraJson(n.getExtraJson());
                node.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());
                node = nodeRepository.save(node);
                upserted++;
            } else {
                // 只做轻量更新：类型/extra 允许覆盖
                node.setNodeType(n.getNodeType());
                node.setExtraJson(n.getExtraJson());
                node = nodeRepository.save(node);
            }

            keyToNode.put(n.getKey(), node);
        }

        int edgesUpserted = 0;
        for (GraphImportRequest.EdgeCreate e : req.getEdges()) {
            NodeEntity from = keyToNode.get(e.getFromKey());
            NodeEntity to = keyToNode.get(e.getToKey());
            if (from == null || to == null) {
                throw new IllegalArgumentException("edge references unknown node key");
            }

            boolean oneway = e.getOneway() != null && e.getOneway();
            boolean hasStairs = e.getHasStairs() != null && e.getHasStairs();
            int slopeLevel = e.getSlopeLevel() != null ? e.getSlopeLevel() : 0;
            boolean accessibleDefault = e.getAccessibleDefault() == null || e.getAccessibleDefault();
            double baseCost = e.getBaseCost() != null ? e.getBaseCost() : e.getDistanceM();

            edgesUpserted += upsertEdge(from, to, e.getDistanceM(), oneway, hasStairs, slopeLevel, accessibleDefault, baseCost);

            if (!oneway) {
                edgesUpserted += upsertEdge(to, from, e.getDistanceM(), false, hasStairs, slopeLevel, accessibleDefault, baseCost);
            }
        }

        GraphValidationReport report = buildValidationReport(nodeRepository.findAll(), edgeRepository.findAllWithNodes());
        GraphImportResponse resp = new GraphImportResponse(upserted, edgesUpserted, report);
        if (bumpVersion) {
            graphVersionService.bump();
            logService.log("GRAPH_IMPORT", "nodes=" + upserted + ", edges=" + edgesUpserted);
            recordSnapshot("IMPORT", report);
        }
        return resp;
    }

    @Transactional
    public GraphImportResponse replaceGraph(GraphReplaceRequest req) {
        return recordImport("admin", () -> replaceGraphInternal(req, "REPLACE", null));
    }

    @Transactional(readOnly = true)
    public GraphValidationReport validateGraph() {
        return buildValidationReport(nodeRepository.findAll(), edgeRepository.findAllWithNodes());
    }

    @Transactional(readOnly = true)
    public GraphValidationReport validatePayload(GraphImportRequest req, boolean expandUndirected) {
        return buildValidationReportFromPayload(req, expandUndirected);
    }

    @Transactional
    public GraphRepairResponse repairGraph(GraphRepairRequest req) {
        boolean dryRun = req != null && Boolean.TRUE.equals(req.getDryRun());
        double maxConnectMeters = req != null && req.getMaxConnectMeters() != null
                ? req.getMaxConnectMeters()
                : DEFAULT_REPAIR_MAX_CONNECT_METERS;
        if (maxConnectMeters < 0) {
            maxConnectMeters = 0;
        }

        boolean connectIsolated = req == null || req.getConnectIsolated() == null || req.getConnectIsolated();
        boolean connectDisconnected = req == null || req.getConnectDisconnected() == null || req.getConnectDisconnected();
        boolean removeDangling = req == null || req.getRemoveDanglingEdges() == null || req.getRemoveDanglingEdges();
        boolean removeDuplicate = req == null || req.getRemoveDuplicateEdges() == null || req.getRemoveDuplicateEdges();

        List<NodeEntity> nodes = nodeRepository.findAll();
        List<EdgeEntity> edges = edgeRepository.findAllWithNodes();
        GraphValidationReport before = buildValidationReport(nodes, edges);

        if (nodes.isEmpty()) {
            GraphRepairSummary summary = new GraphRepairSummary(dryRun, maxConnectMeters, 0, 0, 0, 0, 0, 0, 0);
            return new GraphRepairResponse(before, before, summary);
        }

        Map<Long, NodeEntity> nodeById = new HashMap<>();
        for (NodeEntity node : nodes) {
            if (node != null && node.getId() != null) {
                nodeById.put(node.getId(), node);
            }
        }

        List<EdgeEntity> danglingEdges = new ArrayList<>();
        Map<String, List<EdgeEntity>> edgesByKey = new HashMap<>();
        for (EdgeEntity edge : edges) {
            if (edge == null) {
                continue;
            }
            Long fromId = edge.getFromNode() != null ? edge.getFromNode().getId() : null;
            Long toId = edge.getToNode() != null ? edge.getToNode().getId() : null;
            if (fromId == null || toId == null || !nodeById.containsKey(fromId) || !nodeById.containsKey(toId)) {
                danglingEdges.add(edge);
                continue;
            }
            String key = edgeKey(fromId, toId);
            edgesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(edge);
        }

        List<EdgeEntity> duplicateEdges = new ArrayList<>();
        for (List<EdgeEntity> group : edgesByKey.values()) {
            if (group.size() <= 1) {
                continue;
            }
            EdgeEntity keep = selectEdgeToKeep(group);
            Long keepId = keep != null ? keep.getId() : null;
            for (EdgeEntity edge : group) {
                if (edge == null) {
                    continue;
                }
                if (keepId != null && keepId.equals(edge.getId())) {
                    continue;
                }
                duplicateEdges.add(edge);
            }
        }

        int danglingRemoved = removeDangling ? danglingEdges.size() : 0;
        int duplicateRemoved = removeDuplicate ? duplicateEdges.size() : 0;

        Set<Long> removedEdgeIds = new HashSet<>();
        if (removeDangling) {
            for (EdgeEntity edge : danglingEdges) {
                if (edge != null && edge.getId() != null) {
                    removedEdgeIds.add(edge.getId());
                }
            }
        }
        if (removeDuplicate) {
            for (EdgeEntity edge : duplicateEdges) {
                if (edge != null && edge.getId() != null) {
                    removedEdgeIds.add(edge.getId());
                }
            }
        }

        List<EdgeEntity> workingEdges = new ArrayList<>();
        for (EdgeEntity edge : edges) {
            if (edge == null) {
                continue;
            }
            Long id = edge.getId();
            if (id != null && removedEdgeIds.contains(id)) {
                continue;
            }
            workingEdges.add(edge);
        }

        Map<Long, Set<Long>> neighbors = new HashMap<>();
        for (Long id : nodeById.keySet()) {
            neighbors.put(id, new HashSet<>());
        }
        Set<String> existingEdgeKeys = new HashSet<>();
        for (EdgeEntity edge : workingEdges) {
            Long fromId = edge.getFromNode() != null ? edge.getFromNode().getId() : null;
            Long toId = edge.getToNode() != null ? edge.getToNode().getId() : null;
            if (fromId == null || toId == null) {
                continue;
            }
            existingEdgeKeys.add(edgeKey(fromId, toId));
            neighbors.computeIfAbsent(fromId, k -> new HashSet<>()).add(toId);
            neighbors.computeIfAbsent(toId, k -> new HashSet<>()).add(fromId);
        }

        List<EdgeEntity> newEdges = new ArrayList<>();
        int isolatedConnected = 0;
        int componentsConnected = 0;
        int edgesCreated = 0;

        if (maxConnectMeters > 0 && !neighbors.isEmpty()) {
            ComponentsResult components = computeComponents(neighbors);
            Set<Long> largest = components.largestComponent;
            if (largest == null) {
                largest = new HashSet<>();
            }

            if (connectIsolated && !largest.isEmpty()) {
                List<Long> isolatedNodes = new ArrayList<>();
                for (Map.Entry<Long, Set<Long>> entry : neighbors.entrySet()) {
                    if (entry.getValue() == null || entry.getValue().isEmpty()) {
                        isolatedNodes.add(entry.getKey());
                    }
                }

                for (Long isolatedId : isolatedNodes) {
                    NodePair pair = findNearestInSet(isolatedId, largest, nodeById, maxConnectMeters);
                    if (pair == null) {
                        continue;
                    }
                    int created = addConnection(pair, nodeById, existingEdgeKeys, neighbors, newEdges);
                    if (created > 0) {
                        edgesCreated += created;
                        isolatedConnected++;
                        largest.add(pair.fromId);
                    }
                }
            }

            if (connectDisconnected) {
                ComponentsResult updated = computeComponents(neighbors);
                Set<Long> largestComponent = updated.largestComponent;
                if (largestComponent == null) {
                    largestComponent = new HashSet<>();
                }

                for (Set<Long> component : updated.components) {
                    if (component == null || component.isEmpty() || component == largestComponent || largestComponent.isEmpty()) {
                        continue;
                    }
                    if (largestComponent.containsAll(component)) {
                        continue;
                    }
                    NodePair pair = findClosestPair(component, largestComponent, nodeById, maxConnectMeters);
                    if (pair == null) {
                        continue;
                    }
                    int created = addConnection(pair, nodeById, existingEdgeKeys, neighbors, newEdges);
                    if (created > 0) {
                        edgesCreated += created;
                        componentsConnected++;
                        largestComponent.addAll(component);
                    }
                }
            }
        }

        ComponentsResult finalComponents = computeComponents(neighbors);
        int isolatedRemaining = 0;
        for (Set<Long> adj : neighbors.values()) {
            if (adj == null || adj.isEmpty()) {
                isolatedRemaining++;
            }
        }
        int disconnectedRemaining = Math.max(0, finalComponents.components.size() - 1);

        GraphRepairSummary summary = new GraphRepairSummary(
                dryRun,
                maxConnectMeters,
                danglingRemoved,
                duplicateRemoved,
                isolatedConnected,
                componentsConnected,
                edgesCreated,
                isolatedRemaining,
                disconnectedRemaining
        );

        if (dryRun) {
            Map<Long, ValidationNode> validationNodes = new HashMap<>();
            for (NodeEntity node : nodeById.values()) {
                if (node.getId() == null || node.getLat() == null || node.getLng() == null) {
                    continue;
                }
                validationNodes.put(node.getId(), new ValidationNode(
                        node.getId(),
                        node.getLat().doubleValue(),
                        node.getLng().doubleValue()
                ));
            }
            List<ValidationEdge> validationEdges = new ArrayList<>();
            for (EdgeEntity edge : workingEdges) {
                Long fromId = edge.getFromNode() != null ? edge.getFromNode().getId() : null;
                Long toId = edge.getToNode() != null ? edge.getToNode().getId() : null;
                validationEdges.add(new ValidationEdge(edge.getId(), fromId, toId));
            }
            for (EdgeEntity edge : newEdges) {
                Long fromId = edge.getFromNode() != null ? edge.getFromNode().getId() : null;
                Long toId = edge.getToNode() != null ? edge.getToNode().getId() : null;
                validationEdges.add(new ValidationEdge(null, fromId, toId));
            }
            GraphValidationReport after = buildValidationReportFromGraph(validationNodes, validationEdges);
            return new GraphRepairResponse(before, after, summary);
        }

        if (removeDangling && !danglingEdges.isEmpty()) {
            deleteObstacleRefs(danglingEdges);
            edgeRepository.deleteAllInBatch(danglingEdges);
        }
        if (removeDuplicate && !duplicateEdges.isEmpty()) {
            deleteObstacleRefs(duplicateEdges);
            edgeRepository.deleteAllInBatch(duplicateEdges);
        }
        if (!newEdges.isEmpty()) {
            edgeRepository.saveAll(newEdges);
        }

        graphVersionService.bump();
        logService.log("GRAPH_REPAIR", "danglingRemoved=" + danglingRemoved
                + ", duplicateRemoved=" + duplicateRemoved
                + ", edgesCreated=" + edgesCreated);
        GraphValidationReport after = buildValidationReport(nodeRepository.findAll(), edgeRepository.findAllWithNodes());
        recordSnapshot("REPAIR", after);
        return new GraphRepairResponse(before, after, summary);
    }

    @Transactional(readOnly = true)
    public List<GraphSnapshotSummary> listSnapshots() {
        return snapshotRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Transactional
    public GraphImportResponse rollbackSnapshot(long snapshotId) {
        GraphSnapshotEntity entity = snapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new IllegalArgumentException("snapshot not found"));
        GraphSnapshotResponse snapshot;
        try {
            snapshot = objectMapper.readValue(entity.getSnapshotJson(), GraphSnapshotResponse.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("snapshot parse failed", e);
        }
        GraphReplaceRequest req = toReplaceRequest(snapshot);
        String detail = "snapshotId=" + snapshotId + ", sourceVersion=" + entity.getVersion();
        return recordImport("admin", () -> replaceGraphInternal(req, "ROLLBACK", detail));
    }

    @Transactional
    public GraphImportResponse importGraphForBootstrap(GraphImportRequest req, boolean replace) {
        if (replace) {
            GraphReplaceRequest replaceReq = new GraphReplaceRequest();
            replaceReq.setNodes(req == null ? null : req.getNodes());
            replaceReq.setEdges(req == null ? null : req.getEdges());
            return recordImport("bootstrap", () -> replaceGraphInternal(replaceReq, "REPLACE", null));
        }
        return recordImport("bootstrap", () -> importGraphInternal(req, true));
    }

    private GraphImportResponse recordImport(String mode, java.util.function.Supplier<GraphImportResponse> supplier) {
        Timer.Sample sample = Timer.start(meterRegistry);
        boolean success = false;
        try {
            GraphImportResponse response = supplier.get();
            success = true;
            return response;
        } finally {
            String resultTag = success ? "success" : "failure";
            sample.stop(Timer.builder("graph.import.duration")
                    .description("Graph import execution duration")
                    .tag("mode", safeMode(mode))
                    .tag("result", resultTag)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            importCounters.computeIfAbsent(counterKey(mode, success), k ->
                    Counter.builder("graph.import.count")
                            .description("Graph import execution count")
                            .tag("mode", safeMode(mode))
                            .tag("result", resultTag)
                            .register(meterRegistry)
            ).increment();
        }
    }

    private String counterKey(String mode, boolean success) {
        return safeMode(mode) + "|" + (success ? "success" : "failure");
    }

    private String safeMode(String mode) {
        if (mode == null) {
            return "unknown";
        }
        String normalized = mode.trim().toLowerCase();
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private GraphImportResponse replaceGraphInternal(GraphReplaceRequest req, String snapshotLabel, String extraDetail) {
        obstacleEffectRepository.deleteAllInBatch();
        obstacleReportRepository.deleteAllInBatch();
        edgeRepository.deleteAllInBatch();
        nodeRepository.deleteAllInBatch();

        GraphImportResponse resp;
        if (req == null || req.getNodes() == null || req.getEdges() == null || req.getNodes().isEmpty() || req.getEdges().isEmpty()) {
            GraphValidationReport report = buildValidationReport(nodeRepository.findAll(), edgeRepository.findAllWithNodes());
            resp = new GraphImportResponse(0, 0, report);
        } else {
            GraphImportRequest importReq = new GraphImportRequest();
            importReq.setNodes(req.getNodes());
            importReq.setEdges(req.getEdges());
            resp = importGraphInternal(importReq, false);
        }

        graphVersionService.bump();
        String detail = "nodes=" + resp.getNodesUpserted() + ", edges=" + resp.getEdgesCreated();
        if (extraDetail != null && !extraDetail.isEmpty()) {
            detail = detail + ", " + extraDetail;
        }
        String action = "REPLACE".equalsIgnoreCase(snapshotLabel) ? "GRAPH_REPLACE" : "GRAPH_ROLLBACK";
        logService.log(action, detail);
        recordSnapshot(snapshotLabel, resp.getReport());
        return resp;
    }

    private GraphReplaceRequest toReplaceRequest(GraphSnapshotResponse snapshot) {
        GraphReplaceRequest req = new GraphReplaceRequest();
        if (snapshot == null || snapshot.getNodes() == null || snapshot.getEdges() == null) {
            req.setNodes(Collections.emptyList());
            req.setEdges(Collections.emptyList());
            return req;
        }

        Map<Long, String> keyById = new HashMap<>();
        List<GraphImportRequest.NodeUpsert> nodes = new ArrayList<>();
        int idx = 0;
        for (GraphSnapshotResponse.NodeDto n : snapshot.getNodes()) {
            String key = "node-" + (n.getId() != null ? n.getId() : ("tmp" + idx++));
            GraphImportRequest.NodeUpsert upsert = new GraphImportRequest.NodeUpsert();
            upsert.setKey(key);
            upsert.setLat(n.getLat());
            upsert.setLng(n.getLng());
            upsert.setNodeType(n.getNodeType() != null ? n.getNodeType() : "default");
            upsert.setExtraJson(n.getExtraJson());
            nodes.add(upsert);
            if (n.getId() != null) {
                keyById.put(n.getId(), key);
            }
        }

        List<GraphImportRequest.EdgeCreate> edges = new ArrayList<>();
        for (GraphSnapshotResponse.EdgeDto e : snapshot.getEdges()) {
            String fromKey = keyById.get(e.getFromNodeId());
            String toKey = keyById.get(e.getToNodeId());
            if (fromKey == null || toKey == null) {
                continue;
            }
            GraphImportRequest.EdgeCreate edge = new GraphImportRequest.EdgeCreate();
            edge.setFromKey(fromKey);
            edge.setToKey(toKey);
            edge.setDistanceM(e.getDistanceM());
            edge.setOneway(e.isOneway());
            edge.setHasStairs(e.isHasStairs());
            edge.setSlopeLevel(e.getSlopeLevel());
            edge.setAccessibleDefault(e.isAccessibleDefault());
            edge.setBaseCost(e.getBaseCost());
            edges.add(edge);
        }
        req.setNodes(nodes);
        req.setEdges(edges);
        return req;
    }

    private void recordSnapshot(String label, GraphValidationReport report) {
        try {
            GraphSnapshotResponse snapshot = snapshot();
            GraphSnapshotEntity entity = new GraphSnapshotEntity();
            entity.setVersion(graphVersionService.current());
            entity.setLabel(label);
            entity.setNodeCount(report != null ? report.getNodeCount() : (snapshot.getNodes() != null ? snapshot.getNodes().size() : 0));
            entity.setEdgeCount(report != null ? report.getEdgeCount() : (snapshot.getEdges() != null ? snapshot.getEdges().size() : 0));
            entity.setQualityScore(report != null ? report.getQualityScore() : 0.0);
            entity.setCreatedAt(Instant.now());
            entity.setSnapshotJson(objectMapper.writeValueAsString(snapshot));
            snapshotRepository.save(entity);
        } catch (Exception e) {
            logService.log("GRAPH_SNAPSHOT_FAIL", label + ":" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private GraphSnapshotSummary toSummary(GraphSnapshotEntity entity) {
        return new GraphSnapshotSummary(
                entity.getId(),
                entity.getVersion(),
                entity.getLabel(),
                entity.getNodeCount(),
                entity.getEdgeCount(),
                entity.getQualityScore(),
                entity.getCreatedAt()
        );
    }

    private int upsertEdge(NodeEntity from,
                           NodeEntity to,
                           double distanceM,
                           boolean oneway,
                           boolean hasStairs,
                           int slopeLevel,
                           boolean accessibleDefault,
                           double baseCost) {
        EdgeEntity edge = edgeRepository.findByFromNode_IdAndToNode_Id(from.getId(), to.getId()).orElse(null);
        if (edge == null) {
            edge = new EdgeEntity();
            edge.setFromNode(from);
            edge.setToNode(to);
            edge.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());
        }

        edge.setDistanceM(distanceM);
        edge.setOneway(oneway);
        edge.setHasStairs(hasStairs);
        edge.setSlopeLevel(slopeLevel);
        edge.setAccessibleDefault(accessibleDefault);
        edge.setBaseCost(baseCost);

        edgeRepository.save(edge);
        return 1;
    }

    @Transactional(readOnly = true)
    public GraphSnapshotResponse snapshot() {
        List<NodeEntity> nodes = nodeRepository.findAll();
        List<EdgeEntity> edges = edgeRepository.findAllWithNodes();

        Instant now = Instant.now();
        Map<Long, ObstacleEffectEntity> disabledEffectByEdgeId = new HashMap<>();
        for (ObstacleEffectEntity ef : obstacleEffectRepository.findActiveDisabledEffects(now)) {
            if (ef.getEdge() != null && ef.getEdge().getId() != null) {
                disabledEffectByEdgeId.put(ef.getEdge().getId(), ef);
            }
        }
        Set<Long> disabledEdgeIds = disabledEffectByEdgeId.keySet();

        List<GraphSnapshotResponse.NodeDto> nodeDtos = new ArrayList<>();
        for (NodeEntity n : nodes) {
            if (n == null || n.getId() == null || n.getLat() == null || n.getLng() == null) {
                continue;
            }
            nodeDtos.add(new GraphSnapshotResponse.NodeDto(
                    n.getId(),
                    n.getLat().toPlainString(),
                    n.getLng().toPlainString(),
                    n.getNodeType(),
                    n.getExtraJson()
            ));
        }

        // 去掉非单行边的“反向重复”以便前端回显成一条线
        Set<String> undirectedSeen = new HashSet<>();
        List<GraphSnapshotResponse.EdgeDto> edgeDtos = new ArrayList<>();
        for (EdgeEntity e : edges) {
            if (e == null || e.getFromNode() == null || e.getToNode() == null) {
                continue;
            }
            if (e.getFromNode().getLat() == null || e.getFromNode().getLng() == null
                    || e.getToNode().getLat() == null || e.getToNode().getLng() == null) {
                continue;
            }
            Long fromId = e.getFromNode().getId();
            Long toId = e.getToNode().getId();
            if (fromId == null || toId == null) {
                continue;
            }

            if (!e.isOneway()) {
                long a = Math.min(fromId, toId);
                long b = Math.max(fromId, toId);
                String key = a + "-" + b;
                if (undirectedSeen.contains(key)) {
                    continue;
                }
                undirectedSeen.add(key);
            }

            edgeDtos.add(new GraphSnapshotResponse.EdgeDto(
                    e.getId(),
                    fromId,
                    toId,
                    e.getFromNode().getLat().toPlainString(),
                    e.getFromNode().getLng().toPlainString(),
                    e.getToNode().getLat().toPlainString(),
                    e.getToNode().getLng().toPlainString(),
                    e.getDistanceM(),
                    e.isOneway(),
                    e.isHasStairs(),
                    e.getSlopeLevel(),
                    e.isAccessibleDefault(),
                    e.getBaseCost(),
                    disabledEdgeIds.contains(e.getId()),
                    disabledEffectByEdgeId.get(e.getId()) != null ? disabledEffectByEdgeId.get(e.getId()).getReason() : null,
                    disabledEffectByEdgeId.get(e.getId()) != null ? disabledEffectByEdgeId.get(e.getId()).getEndAt() : null
            ));
        }

        return new GraphSnapshotResponse(nodeDtos, edgeDtos);
    }

    private GraphValidationReport buildValidationReport(List<NodeEntity> nodes, List<EdgeEntity> edges) {
        Map<Long, ValidationNode> nodeById = new HashMap<>();
        if (nodes != null) {
            for (NodeEntity n : nodes) {
                if (n == null || n.getId() == null) {
                    continue;
                }
                double lat = n.getLat() == null ? 0.0 : n.getLat().doubleValue();
                double lng = n.getLng() == null ? 0.0 : n.getLng().doubleValue();
                nodeById.put(n.getId(), new ValidationNode(n.getId(), lat, lng));
            }
        }

        List<ValidationEdge> edgeList = new ArrayList<>();
        if (edges != null) {
            for (EdgeEntity e : edges) {
                if (e == null) {
                    continue;
                }
                Long fromId = e.getFromNode() != null ? e.getFromNode().getId() : null;
                Long toId = e.getToNode() != null ? e.getToNode().getId() : null;
                edgeList.add(new ValidationEdge(e.getId(), fromId, toId));
            }
        }
        return buildValidationReportFromGraph(nodeById, edgeList);
    }

    private GraphValidationReport buildValidationReportFromPayload(GraphImportRequest req, boolean expandUndirected) {
        Map<Long, ValidationNode> nodeById = new HashMap<>();
        Map<String, Long> idByKey = new HashMap<>();
        long seq = 1;
        if (req != null && req.getNodes() != null) {
            for (GraphImportRequest.NodeUpsert n : req.getNodes()) {
                if (n == null) {
                    continue;
                }
                double lat;
                double lng;
                try {
                    lat = Double.parseDouble(n.getLat());
                    lng = Double.parseDouble(n.getLng());
                } catch (Exception e) {
                    continue;
                }
                long id = seq++;
                idByKey.put(n.getKey(), id);
                nodeById.put(id, new ValidationNode(id, lat, lng));
            }
        }

        List<ValidationEdge> edgeList = new ArrayList<>();
        if (req != null && req.getEdges() != null) {
            for (GraphImportRequest.EdgeCreate e : req.getEdges()) {
                if (e == null) {
                    continue;
                }
                Long fromId = idByKey.get(e.getFromKey());
                Long toId = idByKey.get(e.getToKey());
                edgeList.add(new ValidationEdge(null, fromId, toId));
                boolean oneway = e.getOneway() != null && e.getOneway();
                if (expandUndirected && !oneway && fromId != null && toId != null) {
                    edgeList.add(new ValidationEdge(null, toId, fromId));
                }
            }
        }
        return buildValidationReportFromGraph(nodeById, edgeList);
    }

    private GraphValidationReport buildValidationReportFromGraph(Map<Long, ValidationNode> nodeById,
                                                                 List<ValidationEdge> edges) {
        int nodeCount = nodeById != null ? nodeById.size() : 0;
        int edgeCount = edges != null ? edges.size() : 0;

        Map<Long, Set<Long>> neighbors = new HashMap<>();
        if (nodeById != null) {
            for (Long id : nodeById.keySet()) {
                neighbors.put(id, new HashSet<>());
            }
        }

        final int sampleLimit = 10;
        int danglingEdgeCount = 0;
        List<Long> danglingEdgeSample = new ArrayList<>();
        Map<String, List<ValidationEdge>> edgesByKey = new HashMap<>();
        Set<String> undirected = new HashSet<>();
        if (edges != null) {
            for (ValidationEdge e : edges) {
                if (e == null) {
                    continue;
                }
                Long fromId = e.fromId;
                Long toId = e.toId;
                if (fromId == null || toId == null
                        || nodeById == null
                        || !nodeById.containsKey(fromId)
                        || !nodeById.containsKey(toId)) {
                    danglingEdgeCount++;
                    if (e.edgeId != null && danglingEdgeSample.size() < sampleLimit) {
                        danglingEdgeSample.add(e.edgeId);
                    }
                    continue;
                }

                neighbors.computeIfAbsent(fromId, k -> new HashSet<>()).add(toId);
                neighbors.computeIfAbsent(toId, k -> new HashSet<>()).add(fromId);

                long a = Math.min(fromId, toId);
                long b = Math.max(fromId, toId);
                undirected.add(a + "-" + b);

                String key = fromId + "-" + toId;
                edgesByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(e);
            }
        }

        int duplicateEdgeCount = 0;
        List<Long> duplicateEdgeSample = new ArrayList<>();
        for (List<ValidationEdge> list : edgesByKey.values()) {
            if (list.size() <= 1) {
                continue;
            }
            duplicateEdgeCount += list.size() - 1;
            for (int i = 1; i < list.size() && duplicateEdgeSample.size() < sampleLimit; i++) {
                Long edgeId = list.get(i).edgeId;
                if (edgeId != null) {
                    duplicateEdgeSample.add(edgeId);
                }
            }
        }

        int undirectedEdgeCount = undirected.size();
        int isolatedNodes = 0;
        int deadEndNodes = 0;
        List<Long> isolatedSample = new ArrayList<>();
        Set<Long> isolatedSet = new HashSet<>();
        Set<Long> deadEndSet = new HashSet<>();

        if (nodeById != null) {
            for (Long id : nodeById.keySet()) {
                int degree = neighbors.getOrDefault(id, new HashSet<>()).size();
                if (degree == 0) {
                    isolatedNodes++;
                    isolatedSet.add(id);
                    if (isolatedSample.size() < sampleLimit) {
                        isolatedSample.add(id);
                    }
                } else if (degree == 1) {
                    deadEndNodes++;
                    deadEndSet.add(id);
                }
            }
        }

        int componentCount = 0;
        int largestComponentNodes = 0;
        Set<Long> largestSet = new HashSet<>();
        Set<Long> visited = new HashSet<>();

        if (nodeById != null) {
            for (Long startId : nodeById.keySet()) {
                if (visited.contains(startId)) {
                    continue;
                }

                componentCount++;
                List<Long> componentNodes = new ArrayList<>();
                Deque<Long> queue = new ArrayDeque<>();
                queue.add(startId);
                visited.add(startId);

                while (!queue.isEmpty()) {
                    Long cur = queue.poll();
                    componentNodes.add(cur);
                    for (Long next : neighbors.getOrDefault(cur, new HashSet<>())) {
                        if (visited.add(next)) {
                            queue.add(next);
                        }
                    }
                }

                if (componentNodes.size() > largestComponentNodes) {
                    largestComponentNodes = componentNodes.size();
                    largestSet = new HashSet<>(componentNodes);
                }
            }
        }

        int disconnectedNodes = 0;
        List<Long> disconnectedSample = new ArrayList<>();
        Set<Long> disconnectedSet = new HashSet<>();
        if (nodeById != null && !nodeById.isEmpty()) {
            for (Long id : nodeById.keySet()) {
                if (!largestSet.contains(id)) {
                    disconnectedNodes++;
                    disconnectedSet.add(id);
                    if (disconnectedSample.size() < sampleLimit) {
                        disconnectedSample.add(id);
                    }
                }
            }
        }

        List<GraphIssuePoint> issuePoints = new ArrayList<>();
        Set<Long> addedIssueIds = new HashSet<>();
        if (nodeById != null) {
            for (Long id : isolatedSet) {
                if (issuePoints.size() >= ISSUE_POINT_LIMIT) break;
                ValidationNode n = nodeById.get(id);
                if (n == null) continue;
                issuePoints.add(new GraphIssuePoint(id, "isolated", n.lat, n.lng));
                addedIssueIds.add(id);
            }
            for (Long id : disconnectedSet) {
                if (issuePoints.size() >= ISSUE_POINT_LIMIT) break;
                if (addedIssueIds.contains(id)) continue;
                ValidationNode n = nodeById.get(id);
                if (n == null) continue;
                issuePoints.add(new GraphIssuePoint(id, "disconnected", n.lat, n.lng));
                addedIssueIds.add(id);
            }
            for (Long id : deadEndSet) {
                if (issuePoints.size() >= ISSUE_POINT_LIMIT) break;
                if (addedIssueIds.contains(id)) continue;
                ValidationNode n = nodeById.get(id);
                if (n == null) continue;
                issuePoints.add(new GraphIssuePoint(id, "dead_end", n.lat, n.lng));
                addedIssueIds.add(id);
            }
        }

        double qualityScore;
        if (nodeCount == 0) {
            qualityScore = 0.0;
        } else {
            double isolatedRatio = (double) isolatedNodes / nodeCount;
            double disconnectedRatio = (double) disconnectedNodes / nodeCount;
            double deadEndRatio = (double) deadEndNodes / nodeCount;
            double danglingRatio = edgeCount == 0 ? 0.0 : (double) danglingEdgeCount / edgeCount;
            double duplicateRatio = edgeCount == 0 ? 0.0 : (double) duplicateEdgeCount / edgeCount;
            double score = 100.0;
            score -= Math.min(40.0, isolatedRatio * 100.0);
            score -= Math.min(30.0, disconnectedRatio * 100.0);
            score -= Math.min(20.0, deadEndRatio * 100.0);
            score -= Math.min(15.0, danglingRatio * 100.0);
            score -= Math.min(10.0, duplicateRatio * 100.0);
            score -= Math.min(10.0, Math.max(0, componentCount - 1) * 2.0);
            if (score < 0.0) score = 0.0;
            if (score > 100.0) score = 100.0;
            qualityScore = Math.round(score * 10.0) / 10.0;
        }

        List<String> suggestions = new ArrayList<>();
        if (nodeCount == 0) {
            suggestions.add("?????????????");
        } else {
            double isolatedRatio = (double) isolatedNodes / nodeCount;
            double disconnectedRatio = (double) disconnectedNodes / nodeCount;
            double deadEndRatio = (double) deadEndNodes / nodeCount;
            if (componentCount > 1 || disconnectedRatio > 0.05) {
                suggestions.add("???????????????");
            }
            if (isolatedRatio > 0.02) {
                suggestions.add("??????????????");
            }
            if (deadEndRatio > 0.15) {
                suggestions.add("?????????????????");
            }
            if (danglingEdgeCount > 0) {
                suggestions.add("????????????????");
            }
            if (duplicateEdgeCount > 0) {
                suggestions.add("?????????????");
            }
            if (suggestions.isEmpty()) {
                suggestions.add("???????");
            }
        }
        return new GraphValidationReport(
                nodeCount,
                edgeCount,
                undirectedEdgeCount,
                componentCount,
                largestComponentNodes,
                disconnectedNodes,
                isolatedNodes,
                deadEndNodes,
                danglingEdgeCount,
                duplicateEdgeCount,
                isolatedSample,
                disconnectedSample,
                danglingEdgeSample,
                duplicateEdgeSample,
                qualityScore,
                issuePoints,
                suggestions
        );
    }

    private String edgeKey(Long fromId, Long toId) {
        return fromId + "-" + toId;
    }

    private EdgeEntity selectEdgeToKeep(List<EdgeEntity> edges) {
        EdgeEntity best = null;
        long bestId = Long.MAX_VALUE;
        for (EdgeEntity edge : edges) {
            if (edge == null) {
                continue;
            }
            Long id = edge.getId();
            long edgeId = id != null ? id : Long.MAX_VALUE;
            if (best == null || edgeId < bestId) {
                best = edge;
                bestId = edgeId;
            }
        }
        return best;
    }

    private void deleteObstacleRefs(List<EdgeEntity> edges) {
        List<Long> edgeIds = new ArrayList<>();
        for (EdgeEntity edge : edges) {
            if (edge != null && edge.getId() != null) {
                edgeIds.add(edge.getId());
            }
        }
        if (edgeIds.isEmpty()) {
            return;
        }
        obstacleEffectRepository.deleteAllByEdge_IdIn(edgeIds);
        obstacleReportRepository.deleteAllByEdge_IdIn(edgeIds);
    }

    private ComponentsResult computeComponents(Map<Long, Set<Long>> neighbors) {
        List<Set<Long>> components = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Set<Long> largest = new HashSet<>();

        for (Long start : neighbors.keySet()) {
            if (visited.contains(start)) {
                continue;
            }
            Set<Long> component = new HashSet<>();
            Deque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            visited.add(start);

            while (!queue.isEmpty()) {
                Long cur = queue.poll();
                component.add(cur);
                for (Long next : neighbors.getOrDefault(cur, Collections.emptySet())) {
                    if (visited.add(next)) {
                        queue.add(next);
                    }
                }
            }

            components.add(component);
            if (component.size() > largest.size()) {
                largest = component;
            }
        }

        return new ComponentsResult(components, largest);
    }

    private NodePair findNearestInSet(Long fromId,
                                      Set<Long> candidates,
                                      Map<Long, NodeEntity> nodeById,
                                      double maxMeters) {
        if (fromId == null || candidates == null || candidates.isEmpty()) {
            return null;
        }
        NodeEntity from = nodeById.get(fromId);
        if (from == null) {
            return null;
        }
        int level = nodeLevel(from);
        NodePair best = null;

        for (Long candidateId : candidates) {
            if (candidateId == null || candidateId.equals(fromId)) {
                continue;
            }
            NodeEntity candidate = nodeById.get(candidateId);
            if (candidate == null) {
                continue;
            }
            if (nodeLevel(candidate) != level) {
                continue;
            }
            double dist = haversineMeters(
                    from.getLng().doubleValue(),
                    from.getLat().doubleValue(),
                    candidate.getLng().doubleValue(),
                    candidate.getLat().doubleValue()
            );
            if (best == null || dist < best.distanceM) {
                best = new NodePair(fromId, candidateId, dist);
            }
        }

        if (best == null) {
            return null;
        }
        if (maxMeters > 0 && best.distanceM > maxMeters) {
            return null;
        }
        return best;
    }

    private NodePair findClosestPair(Set<Long> component,
                                     Set<Long> target,
                                     Map<Long, NodeEntity> nodeById,
                                     double maxMeters) {
        if (component == null || component.isEmpty() || target == null || target.isEmpty()) {
            return null;
        }
        NodePair best = null;
        for (Long fromId : component) {
            NodeEntity from = nodeById.get(fromId);
            if (from == null) {
                continue;
            }
            int level = nodeLevel(from);
            for (Long toId : target) {
                NodeEntity to = nodeById.get(toId);
                if (to == null) {
                    continue;
                }
                if (nodeLevel(to) != level) {
                    continue;
                }
                double dist = haversineMeters(
                        from.getLng().doubleValue(),
                        from.getLat().doubleValue(),
                        to.getLng().doubleValue(),
                        to.getLat().doubleValue()
                );
                if (best == null || dist < best.distanceM) {
                    best = new NodePair(fromId, toId, dist);
                }
            }
        }

        if (best == null) {
            return null;
        }
        if (maxMeters > 0 && best.distanceM > maxMeters) {
            return null;
        }
        return best;
    }

    private int addConnection(NodePair pair,
                              Map<Long, NodeEntity> nodeById,
                              Set<String> existingEdgeKeys,
                              Map<Long, Set<Long>> neighbors,
                              List<EdgeEntity> newEdges) {
        if (pair == null) {
            return 0;
        }
        NodeEntity from = nodeById.get(pair.fromId);
        NodeEntity to = nodeById.get(pair.toId);
        if (from == null || to == null) {
            return 0;
        }

        int created = 0;
        created += addDirectedEdge(from, to, pair.distanceM, existingEdgeKeys, newEdges);
        created += addDirectedEdge(to, from, pair.distanceM, existingEdgeKeys, newEdges);
        if (created > 0) {
            neighbors.computeIfAbsent(pair.fromId, k -> new HashSet<>()).add(pair.toId);
            neighbors.computeIfAbsent(pair.toId, k -> new HashSet<>()).add(pair.fromId);
        }
        return created;
    }

    private int addDirectedEdge(NodeEntity from,
                                NodeEntity to,
                                double distanceM,
                                Set<String> existingEdgeKeys,
                                List<EdgeEntity> newEdges) {
        String key = edgeKey(from.getId(), to.getId());
        if (existingEdgeKeys.contains(key)) {
            return 0;
        }
        existingEdgeKeys.add(key);
        newEdges.add(buildAutoEdge(from, to, distanceM));
        return 1;
    }

    private EdgeEntity buildAutoEdge(NodeEntity from, NodeEntity to, double distanceM) {
        EdgeEntity edge = new EdgeEntity();
        edge.setFromNode(from);
        edge.setToNode(to);
        edge.setTenantId(com.demo.accessiblenav.tenant.TenantContext.get());
        edge.setDistanceM(distanceM);
        edge.setOneway(false);
        edge.setHasStairs(false);
        edge.setElevator(false);
        edge.setSlopeLevel(0);
        edge.setAccessibleDefault(true);
        edge.setBaseCost(distanceM);
        return edge;
    }

    private int nodeLevel(NodeEntity node) {
        if (node == null || node.getLevel() == null) {
            return 1;
        }
        return node.getLevel();
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

    @SuppressWarnings("unused")
    private static class ValidationNode {
        final long id;
        final double lat;
        final double lng;

        ValidationNode(long id, double lat, double lng) {
            this.id = id;
            this.lat = lat;
            this.lng = lng;
        }
    }

    private static class ComponentsResult {
        final List<Set<Long>> components;
        final Set<Long> largestComponent;

        ComponentsResult(List<Set<Long>> components, Set<Long> largestComponent) {
            this.components = components;
            this.largestComponent = largestComponent;
        }
    }

    private static class NodePair {
        final Long fromId;
        final Long toId;
        final double distanceM;

        NodePair(Long fromId, Long toId, double distanceM) {
            this.fromId = fromId;
            this.toId = toId;
            this.distanceM = distanceM;
        }
    }

    private static class ValidationEdge {
        final Long edgeId;
        final Long fromId;
        final Long toId;

        ValidationEdge(Long edgeId, Long fromId, Long toId) {
            this.edgeId = edgeId;
            this.fromId = fromId;
            this.toId = toId;
        }
    }
}
