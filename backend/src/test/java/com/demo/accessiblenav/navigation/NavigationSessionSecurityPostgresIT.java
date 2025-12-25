package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.exception.BusinessException;
import com.demo.accessiblenav.exception.ErrorCode;
import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.it.PostgresITBase;
import com.demo.accessiblenav.navigation.api.dto.CreateNavigationSessionRequest;
import com.demo.accessiblenav.navigation.service.NavigationSessionAppService;
import com.demo.accessiblenav.route.GraphVersionService;
import com.demo.accessiblenav.route.RouteCache;
import com.demo.accessiblenav.route.dto.TravelMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real PostgreSQL regression for navigation session owner/authorization enforcement.
 *
 * Run with: mvn -Pit verify
 */
public class NavigationSessionSecurityPostgresIT extends PostgresITBase {

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    NavigationSessionAppService sessionService;

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
    void get_asDifferentUser_shouldThrowForbidden() {
        NodeEntity a = seedNode("23.2750000", "113.2000000", 1);
        NodeEntity b = seedNode("23.2750500", "113.2000500", 1);
        seedEdge(a, b, false);
        seedEdge(b, a, false);
        routeCache.clear();
        graphVersionService.bump();

        CreateNavigationSessionRequest req = new CreateNavigationSessionRequest();
        req.setStartLat(23.2750001);
        req.setStartLng(113.2000001);
        req.setEndLat(23.2750501);
        req.setEndLng(113.2000501);
        req.setDestinationName("B");
        req.setMode(TravelMode.WALK.name());

        var created = sessionService.create("u1", req);
        String sid = created.getSessionId();

        assertThatThrownBy(() -> sessionService.get("u2", sid))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    org.assertj.core.api.Assertions.assertThat(be.getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN);
                });
    }

    private NodeEntity seedNode(String lat, String lng, int level) {
        NodeEntity n = new NodeEntity();
        n.setLat(new BigDecimal(lat));
        n.setLng(new BigDecimal(lng));
        n.setLevel(level);
        n.setNodeType("NORMAL");
        return nodeRepository.save(n);
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

