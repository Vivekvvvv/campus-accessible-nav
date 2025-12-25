package com.demo.accessiblenav.route;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.profile.AccessibilityProfileEntity;
import com.demo.accessiblenav.profile.AccessibilityProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.routing.max-snap-meters=200"
})
class RouteControllerAccessibilityPolicyIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    AccessibilityProfileRepository accessibilityProfileRepository;

    @Autowired
    GraphVersionService graphVersionService;

    @Autowired
    RouteCache routeCache;

    @BeforeEach
    void reset() {
        routeCache.clear();
        graphVersionService.bump();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        accessibilityProfileRepository.deleteAll();
    }

    @Test
    void route_withoutStrategyAndSlope_shouldApplyUserProfilePolicy() throws Exception {
        seedSimpleGraph();
        seedProfile("u1", "WHEELCHAIR", true, true, true, 8.0);
        Map<String, Object> payload = basePayload();

        mockMvc.perform(post("/api/route")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routingPolicy.profileApplied").value(true))
                .andExpect(jsonPath("$.routingPolicy.profileMobilityMode").value("WHEELCHAIR"))
                .andExpect(jsonPath("$.routingPolicy.strategy").value("SAFEST"))
                .andExpect(jsonPath("$.routingPolicy.strategySource").value("PROFILE"))
                .andExpect(jsonPath("$.routingPolicy.slopeWeightSource").value("PROFILE"))
                .andExpect(jsonPath("$.routingPolicy.slopeWeight").value(0.85))
                .andExpect(jsonPath("$.routingPolicy.passabilityPenaltyEnabled").value(true))
                .andExpect(jsonPath("$.routingPolicy.passabilityMinClamp").value(0.01))
                .andExpect(jsonPath("$.routingPolicy.passabilityWeightFactor").value(1.0))
                .andExpect(jsonPath("$.routingPolicy.passabilityPolicySource").value("DEFAULT"));
    }

    @Test
    void route_withExplicitStrategyAndSlope_shouldKeepRequestPolicy() throws Exception {
        seedSimpleGraph();
        seedProfile("u1", "WHEELCHAIR", true, true, true, 8.0);
        Map<String, Object> payload = basePayload();
        payload.put("strategy", "SHORTEST");
        payload.put("slopeWeight", 0.15);

        mockMvc.perform(post("/api/route")
                        .with(SecurityMockMvcRequestPostProcessors.user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routingPolicy.profileApplied").value(true))
                .andExpect(jsonPath("$.routingPolicy.strategy").value("SHORTEST"))
                .andExpect(jsonPath("$.routingPolicy.strategySource").value("REQUEST"))
                .andExpect(jsonPath("$.routingPolicy.slopeWeightSource").value("REQUEST"))
                .andExpect(jsonPath("$.routingPolicy.slopeWeight").value(0.15))
                .andExpect(jsonPath("$.routingPolicy.passabilityPenaltyEnabled").value(true))
                .andExpect(jsonPath("$.routingPolicy.passabilityMinClamp").value(0.01))
                .andExpect(jsonPath("$.routingPolicy.passabilityWeightFactor").value(1.0))
                .andExpect(jsonPath("$.routingPolicy.passabilityPolicySource").value("DEFAULT"));
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("startLat", 23.2750001);
        req.put("startLng", 113.2000001);
        req.put("endLat", 23.2750501);
        req.put("endLng", 113.2000501);
        req.put("startLevel", 1);
        req.put("endLevel", 1);
        req.put("mode", "WALK");
        return req;
    }

    private void seedSimpleGraph() {
        NodeEntity a = seedNode("23.2750000", "113.2000000", 1);
        NodeEntity b = seedNode("23.2750500", "113.2000500", 1);
        seedEdge(a, b, false);
        seedEdge(b, a, false);
        routeCache.clear();
        graphVersionService.bump();
    }

    private void seedProfile(String userId,
                             String mobilityMode,
                             boolean avoidStairs,
                             boolean avoidSlope,
                             boolean avoidConstruction,
                             double maxSlopePercent) {
        AccessibilityProfileEntity entity = new AccessibilityProfileEntity();
        entity.setUserId(userId);
        entity.setMobilityMode(mobilityMode);
        entity.setAvoidStairs(avoidStairs);
        entity.setAvoidSlope(avoidSlope);
        entity.setAvoidConstruction(avoidConstruction);
        entity.setMaxSlopePercent(maxSlopePercent);
        accessibilityProfileRepository.save(entity);
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
