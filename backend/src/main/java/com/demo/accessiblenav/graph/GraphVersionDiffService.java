package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.graph.dto.GraphSnapshotResponse;
import com.demo.accessiblenav.graph.dto.GraphVersionDiff;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes diffs between two graph snapshots by comparing their node/edge sets.
 */
@Service
public class GraphVersionDiffService {

    private final GraphSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public GraphVersionDiffService(GraphSnapshotRepository snapshotRepository,
                                   ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    public GraphVersionDiff diff(Long snapshotId1, Long snapshotId2) {
        GraphSnapshotEntity snap1 = snapshotRepository.findById(snapshotId1)
                .orElseThrow(() -> new IllegalArgumentException("snapshot " + snapshotId1 + " not found"));
        GraphSnapshotEntity snap2 = snapshotRepository.findById(snapshotId2)
                .orElseThrow(() -> new IllegalArgumentException("snapshot " + snapshotId2 + " not found"));

        GraphSnapshotResponse r1 = parseSnapshot(snap1.getSnapshotJson());
        GraphSnapshotResponse r2 = parseSnapshot(snap2.getSnapshotJson());

        GraphVersionDiff result = new GraphVersionDiff();
        result.setFromVersion(snap1.getVersion());
        result.setToVersion(snap2.getVersion());

        List<String> entries = new ArrayList<>();

        // Compare nodes
        Set<Long> nodeIds1 = extractNodeIds(r1);
        Set<Long> nodeIds2 = extractNodeIds(r2);

        Set<Long> nodesAdded = new HashSet<>(nodeIds2);
        nodesAdded.removeAll(nodeIds1);
        Set<Long> nodesRemoved = new HashSet<>(nodeIds1);
        nodesRemoved.removeAll(nodeIds2);
        Set<Long> nodesCommon = new HashSet<>(nodeIds1);
        nodesCommon.retainAll(nodeIds2);

        result.setNodesAdded(nodesAdded.size());
        result.setNodesRemoved(nodesRemoved.size());
        result.setNodesModified(0); // simplified

        if (!nodesAdded.isEmpty()) {
            entries.add("Nodes added: " + nodesAdded.size());
        }
        if (!nodesRemoved.isEmpty()) {
            entries.add("Nodes removed: " + nodesRemoved.size());
        }

        // Compare edges
        Set<Long> edgeIds1 = extractEdgeIds(r1);
        Set<Long> edgeIds2 = extractEdgeIds(r2);

        Set<Long> edgesAdded = new HashSet<>(edgeIds2);
        edgesAdded.removeAll(edgeIds1);
        Set<Long> edgesRemoved = new HashSet<>(edgeIds1);
        edgesRemoved.removeAll(edgeIds2);

        result.setEdgesAdded(edgesAdded.size());
        result.setEdgesRemoved(edgesRemoved.size());
        result.setEdgesModified(0); // simplified

        if (!edgesAdded.isEmpty()) {
            entries.add("Edges added: " + edgesAdded.size());
        }
        if (!edgesRemoved.isEmpty()) {
            entries.add("Edges removed: " + edgesRemoved.size());
        }

        if (entries.isEmpty()) {
            entries.add("No differences detected");
        }

        result.setDiffEntries(entries);
        return result;
    }

    private GraphSnapshotResponse parseSnapshot(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new GraphSnapshotResponse();
        }
        try {
            return objectMapper.readValue(json, GraphSnapshotResponse.class);
        } catch (Exception e) {
            return new GraphSnapshotResponse();
        }
    }

    private Set<Long> extractNodeIds(GraphSnapshotResponse r) {
        if (r == null || r.getNodes() == null) return Collections.emptySet();
        return r.getNodes().stream()
                .map(GraphSnapshotResponse.NodeDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> extractEdgeIds(GraphSnapshotResponse r) {
        if (r == null || r.getEdges() == null) return Collections.emptySet();
        return r.getEdges().stream()
                .map(GraphSnapshotResponse.EdgeDto::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
