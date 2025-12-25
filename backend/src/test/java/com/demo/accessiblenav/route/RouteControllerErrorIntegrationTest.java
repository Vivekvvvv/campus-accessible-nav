package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.TravelMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.routing.max-snap-meters=200"
})
class RouteControllerErrorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NodeRepository nodeRepository;

    @Autowired
    private EdgeRepository edgeRepository;

    @Autowired
    private GraphVersionService graphVersionService;

    @Autowired
    private RouteCache routeCache;

    @BeforeEach
    void reset() {
        routeCache.clear();
        graphVersionService.bump();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        routeCache.clear();
        graphVersionService.bump();
    }

    @Test
    void route_shouldReturnGraphNotLoaded_withTraceId_whenGraphIsEmpty() throws Exception {
        RouteRequest req = baseReq();

        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("GRAPH_001"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void route_shouldReturnRouteNotFound_withTraceId_whenDisconnected() throws Exception {
        NodeEntity a = seedNode("23.2750000", "113.2000000", 1);
        NodeEntity b = seedNode("23.2750500", "113.2000500", 1);
        // Place C far away (> maxSnapMeters) so end won't snap to A-B edge projection.
        seedNode("23.2850000", "113.2100000", 1);

        // Only connect A<->B, leave C isolated.
        seedEdge(a, b, false);
        seedEdge(b, a, false);

        routeCache.clear();
        graphVersionService.bump();

        RouteRequest req = baseReq();
        req.setStartLat(23.2750001);
        req.setStartLng(113.2000001);
        req.setEndLat(23.2850001);
        req.setEndLng(113.2100001);

        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_001"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void route_shouldReturnSnapFailed_withTraceId_whenLevelHasNoNodes() throws Exception {
        NodeEntity a = seedNode("23.2750000", "113.2000000", 1);
        NodeEntity b = seedNode("23.2750500", "113.2000500", 1);
        seedEdge(a, b, false);
        seedEdge(b, a, false);

        routeCache.clear();
        graphVersionService.bump();

        RouteRequest req = baseReq();
        req.setStartLevel(2);
        req.setEndLevel(2);

        mockMvc.perform(post("/api/route")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ROUTE_004"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
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
