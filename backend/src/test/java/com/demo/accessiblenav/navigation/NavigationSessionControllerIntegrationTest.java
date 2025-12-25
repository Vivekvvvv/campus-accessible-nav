package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.navigation.api.dto.CreateNavigationSessionRequest;
import com.demo.accessiblenav.navigation.api.dto.NavigationClientEventRequest;
import com.demo.accessiblenav.navigation.api.dto.RerouteRequest;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionEventRepository;
import com.demo.accessiblenav.navigation.persistence.NavigationSessionRepository;
import com.demo.accessiblenav.obstacle.ObstacleEffectEntity;
import com.demo.accessiblenav.obstacle.ObstacleEffectRepository;
import com.demo.accessiblenav.route.GraphVersionService;
import com.demo.accessiblenav.route.RouteCache;
import com.demo.accessiblenav.route.dto.TravelMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.routing.max-snap-meters=200",
        "app.routing.use-postgis-knn=false"
})
class NavigationSessionControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    ObstacleEffectRepository effectRepository;

    @Autowired
    NavigationSessionEventRepository eventRepository;

    @Autowired
    NavigationSessionRepository sessionRepository;

    @Autowired
    GraphVersionService graphVersionService;

    @Autowired
    RouteCache routeCache;

    @BeforeEach
    void reset() {
        routeCache.clear();
        graphVersionService.bump();
        effectRepository.deleteAll();
        eventRepository.deleteAll();
        sessionRepository.deleteAll();
        edgeRepository.deleteAll();
        nodeRepository.deleteAll();
        routeCache.clear();
        graphVersionService.bump();
    }

    @Test
    @WithMockUser(username = "u1", roles = "USER")
    void createAndReroute_shouldPersistSessionAndReturnRoute() throws Exception {
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

        String body = objectMapper.writeValueAsString(req);

        String sessionId = mockMvc.perform(post("/api/navigation/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.route").isNotEmpty())
                .andExpect(jsonPath("$.rerouteCount").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Reroute from a slightly different start location.
        RerouteRequest r = new RerouteRequest();
        r.setLat(23.2750002);
        r.setLng(113.2000002);
        r.setReason("DEVIATION");

        // Extract sessionId via JSONPath-like simple parsing to avoid extra deps.
        String sid = objectMapper.readTree(sessionId).get("sessionId").asText();

        mockMvc.perform(post("/api/navigation/session/" + sid + "/reroute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(r)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rerouteCount").value(1))
                .andExpect(jsonPath("$.deviationCount").value(1))
                .andExpect(jsonPath("$.route").isNotEmpty());
    }

    @Test
    void getSession_asDifferentUser_shouldReturn403() throws Exception {
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

        String body = objectMapper.writeValueAsString(req);

        String resp = mockMvc.perform(post("/api/navigation/session")
                        .with(user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sid = objectMapper.readTree(resp).get("sessionId").asText();

        mockMvc.perform(get("/api/navigation/session/" + sid)
                        .with(user("u2").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void hazards_shouldReturnActiveEffectsNearSessionRoute() throws Exception {
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

        String resp = mockMvc.perform(post("/api/navigation/session")
                        .with(user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sid = objectMapper.readTree(resp).get("sessionId").asText();

        // Create a new active effect after the session was created (simulates a newly-approved obstacle).
        EdgeEntity ab = edgeRepository.findByFromNode_IdAndToNode_Id(a.getId(), b.getId()).orElseThrow();
        ObstacleEffectEntity ef = new ObstacleEffectEntity();
        ef.setEdge(ab);
        ef.setActive(true);
        ef.setDisabled(true);
        ef.setReason("blocked");
        ef.setStartAt(Instant.now());
        ef.setEndAt(Instant.now().plusSeconds(3600));
        effectRepository.save(ef);

        mockMvc.perform(get("/api/navigation/session/" + sid + "/hazards")
                        .with(user("u1").roles("USER"))
                        .param("radiusM", "50")
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].edgeId").value(ab.getId().intValue()));
    }

    @Test
    void clientEvent_shouldAcceptAndPersist() throws Exception {
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

        String resp = mockMvc.perform(post("/api/navigation/session")
                        .with(user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String sid = objectMapper.readTree(resp).get("sessionId").asText();

        NavigationClientEventRequest ev = new NavigationClientEventRequest();
        ev.setType("TURN_ANNOUNCED");
        ev.setPayload("left");

        mockMvc.perform(post("/api/navigation/session/" + sid + "/client-event")
                        .with(user("u1").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ev)))
                .andExpect(status().isNoContent());

        org.assertj.core.api.Assertions.assertThat(eventRepository.findAll())
                .anySatisfy(e -> org.assertj.core.api.Assertions.assertThat(e.getEventType()).isEqualTo("CLIENT_TURN_ANNOUNCED"));
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
