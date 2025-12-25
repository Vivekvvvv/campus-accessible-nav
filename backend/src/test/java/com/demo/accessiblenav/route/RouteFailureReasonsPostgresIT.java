package com.demo.accessiblenav.route;

import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.it.PostgresITBase;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.TravelMode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL regression for route failure reasons (stable error codes, not message parsing).
 *
 * Verifies:
 * - Exceptions are thrown with explicit codes (GRAPH_001/ROUTE_001/ROUTE_004/ROUTE_002)
 * - route.failures metric increments with reason=<code> and stage=<graph_load|primary_route>
 */
@TestPropertySource(properties = {
        "app.routing.max-snap-meters=200",
        "app.routing.use-postgis-knn=true"
})
public class RouteFailureReasonsPostgresIT extends PostgresITBase {

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    GraphRoutingService routingService;

    @Autowired
    GraphVersionService graphVersionService;

    @Autowired
    RouteCache routeCache;

    @Autowired
    MeterRegistry meterRegistry;

    @BeforeEach
    void resetCaches() {
        routeCache.clear();
        graphVersionService.bump();
    }

    @Test
    void graphEmpty_shouldCountFailure_GRAPH_001_atGraphLoadStage() {
        RouteRequest req = baseReq();
        double before = failures("GRAPH_001", "graph_load", "false");
        double errBefore = calculationErrors("GRAPH_001");

        assertThatThrownBy(() -> routingService.route(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("GRAPH_001");

        double after = failures("GRAPH_001", "graph_load", "false");
        double errAfter = calculationErrors("GRAPH_001");
        assertThat(after).isGreaterThan(before);
        assertThat(errAfter).isGreaterThan(errBefore);
    }

    @Test
    void snapFailed_shouldCountFailure_ROUTE_004_atGraphLoadStage() {
        seedComponent("23.2750000", "113.2000000", 1, "23.2750500", "113.2000500", 1);

        RouteRequest req = baseReq();
        req.setStartLevel(2);
        req.setEndLevel(2);

        double before = failures("ROUTE_004", "graph_load", "false");
        assertThatThrownBy(() -> routingService.route(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ROUTE_004");

        double after = failures("ROUTE_004", "graph_load", "false");
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void outOfCampus_shouldCountFailure_ROUTE_002_atGraphLoadStage() {
        seedComponent("23.2750000", "113.2000000", 1, "23.2750500", "113.2000500", 1);

        RouteRequest req = baseReq();
        req.setCampusOnly(true);
        // Default campus bbox in application.yml is around 23.27..; put start far away.
        req.setStartLat(0.0);
        req.setStartLng(0.0);

        double before = failures("ROUTE_002", "graph_load", "true");
        assertThatThrownBy(() -> routingService.route(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ROUTE_002");
        double after = failures("ROUTE_002", "graph_load", "true");
        assertThat(after).isGreaterThan(before);
    }

    @Test
    void noRouteFound_shouldCountFailure_ROUTE_001_atPrimaryRouteStage() {
        // Two disconnected components within snap radius; start in component A, end in component B.
        NodeEntity a1 = seedNode("23.2750000", "113.2000000", 1);
        NodeEntity b1 = seedNode("23.2750500", "113.2000500", 1);
        seedBidirectionalEdge(a1, b1);

        NodeEntity a2 = seedNode("23.2751200", "113.2001200", 1);
        NodeEntity b2 = seedNode("23.2751700", "113.2001700", 1);
        seedBidirectionalEdge(a2, b2);

        // Ensure graph reloads.
        routeCache.clear();
        graphVersionService.bump();

        RouteRequest req = baseReq();
        // Start near component 1, end near component 2.
        req.setStartLat(23.2750001);
        req.setStartLng(113.2000001);
        req.setEndLat(23.2751701);
        req.setEndLng(113.2001701);

        double before = failures("ROUTE_001", "primary_route", "false");
        assertThatThrownBy(() -> routingService.route(req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("ROUTE_001");
        double after = failures("ROUTE_001", "primary_route", "false");
        assertThat(after).isGreaterThan(before);
    }

    private double failures(String reason, String stage, String campusOnly) {
        // strategy defaults to BALANCED when request.strategy is null.
        Counter counter = meterRegistry.counter(
                "route.failures",
                "reason", reason,
                "stage", stage,
                "mode", "WALK",
                "strategy", "BALANCED",
                "campus_only", campusOnly
        );
        return counter.count();
    }

    private double calculationErrors(String reason) {
        Counter counter = meterRegistry.counter(
                "route.calculation.errors",
                "reason", reason
        );
        return counter.count();
    }

    private RouteRequest baseReq() {
        RouteRequest req = new RouteRequest();
        req.setStartLat(23.2750001);
        req.setStartLng(113.2000001);
        req.setEndLat(23.2750501);
        req.setEndLng(113.2000501);
        req.setStartLevel(1);
        req.setEndLevel(1);
        req.setMode(TravelMode.WALK);
        req.setSlopeWeight(0.2);
        return req;
    }

    private void seedComponent(String lat1, String lng1, int level1, String lat2, String lng2, int level2) {
        NodeEntity a = seedNode(lat1, lng1, level1);
        NodeEntity b = seedNode(lat2, lng2, level2);
        seedBidirectionalEdge(a, b);
        routeCache.clear();
        graphVersionService.bump();
    }

    private NodeEntity seedNode(String lat, String lng, int level) {
        NodeEntity n = new NodeEntity();
        n.setLat(new BigDecimal(lat));
        n.setLng(new BigDecimal(lng));
        n.setLevel(level);
        n.setNodeType("NORMAL");
        return nodeRepository.save(n);
    }

    private void seedBidirectionalEdge(NodeEntity a, NodeEntity b) {
        seedEdge(a, b, false);
        seedEdge(b, a, false);
    }

    private void seedEdge(NodeEntity from, NodeEntity to, boolean oneway) {
        EdgeEntity e = new EdgeEntity();
        e.setFromNode(from);
        e.setToNode(to);
        e.setDistanceM(10.0);
        e.setOneway(oneway);
        e.setHasStairs(false);
        e.setElevator(false);
        e.setSlopeLevel(0);
        e.setAccessibleDefault(true);
        e.setBaseCost(10.0);
        edgeRepository.save(e);
    }
}
