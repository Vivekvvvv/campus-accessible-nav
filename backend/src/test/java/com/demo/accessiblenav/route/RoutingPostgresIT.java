package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.it.PostgresITBase;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import com.demo.accessiblenav.route.dto.TravelMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL regression for routing computation and graph loading.
 *
 * Run with: mvn -Pit verify
 */
public class RoutingPostgresIT extends PostgresITBase {

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

    @BeforeEach
    void resetCaches() {
        routeCache.clear();
        graphVersionService.bump();
    }

    @Test
    void route_shouldReturnPath_forSeededSmallGraph() {
        NodeEntity a = new NodeEntity();
        a.setLat(new BigDecimal("23.2750000"));
        a.setLng(new BigDecimal("113.2000000"));
        a.setLevel(1);
        a.setNodeType("NORMAL");
        nodeRepository.save(a);

        NodeEntity b = new NodeEntity();
        b.setLat(new BigDecimal("23.2750500"));
        b.setLng(new BigDecimal("113.2000500"));
        b.setLevel(1);
        b.setNodeType("NORMAL");
        nodeRepository.save(b);

        NodeEntity c = new NodeEntity();
        c.setLat(new BigDecimal("23.2751000"));
        c.setLng(new BigDecimal("113.2001000"));
        c.setLevel(1);
        c.setNodeType("NORMAL");
        nodeRepository.save(c);

        // Bidirectional edges A<->B and B<->C.
        seedEdge(a, b, false);
        seedEdge(b, a, false);
        seedEdge(b, c, false);
        seedEdge(c, b, false);

        // Ensure graph version changes so cached graph (if any) reloads.
        routeCache.clear();
        graphVersionService.bump();

        RouteRequest req = new RouteRequest();
        req.setStartLat(23.2750001);
        req.setStartLng(113.2000001);
        req.setEndLat(23.2751001);
        req.setEndLng(113.2001001);
        req.setStartLevel(1);
        req.setEndLevel(1);
        req.setMode(TravelMode.WALK);
        req.setSlopeWeight(0.2);

        RouteResponse resp = routingService.route(req);

        assertThat(resp).isNotNull();
        assertThat(resp.getDistanceM()).isGreaterThan(0);
        assertThat(resp.getDurationSec()).isGreaterThanOrEqualTo(0);
        assertThat(resp.getPath()).isNotNull();
        assertThat(resp.getPath().size()).isGreaterThanOrEqualTo(2);
        assertThat(resp.getDebug()).isNotNull();
        assertThat(resp.getStartSnap()).isNotNull();
        assertThat(resp.getEndSnap()).isNotNull();
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
