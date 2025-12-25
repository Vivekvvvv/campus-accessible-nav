package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.it.PostgresITBase;
import com.demo.accessiblenav.navigation.api.dto.CreateNavigationSessionRequest;
import com.demo.accessiblenav.navigation.api.dto.RerouteRequest;
import com.demo.accessiblenav.route.GraphVersionService;
import com.demo.accessiblenav.route.RouteCache;
import com.demo.accessiblenav.route.dto.TravelMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real PostgreSQL owner/forbidden regression at controller level.
 *
 * Verifies API returns 403 when session owner mismatches.
 */
@AutoConfigureMockMvc
public class NavigationSessionControllerSecurityPostgresIT extends PostgresITBase {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

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
    void ownerMismatch_shouldReturn403_forGetRerouteAndHazards() throws Exception {
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

        mockMvc.perform(get("/api/navigation/session/" + sid)
                        .with(user("u2").roles("USER")))
                .andExpect(status().isForbidden());

        RerouteRequest reroute = new RerouteRequest();
        reroute.setLat(23.2750002);
        reroute.setLng(113.2000002);
        reroute.setReason("DEVIATION");

        mockMvc.perform(post("/api/navigation/session/" + sid + "/reroute")
                        .with(user("u2").roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reroute)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/navigation/session/" + sid + "/hazards")
                        .with(user("u2").roles("USER")))
                .andExpect(status().isForbidden());
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
