package com.demo.accessiblenav.obstacle;

import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportCreateRequest;
import com.demo.accessiblenav.obstacle.dto.ObstacleReportReviewRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ObstacleLoopIntegrationTest {

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    ObstacleReportService reportService;

    @Autowired
    ObstacleReportRepository reportRepository;

    @Autowired
    ObstacleEffectRepository effectRepository;

    @Autowired
    ObstacleEffectManager effectManager;

    @AfterEach
    void cleanupSecurity() {
        SecurityContextHolder.clearContext();
    }

    private void asUser(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "N/A", Collections.emptyList())
        );
    }

    private EdgeEntity seedBidirectionalEdge() {
        NodeEntity a = new NodeEntity();
        a.setLat(new BigDecimal("23.2750000"));
        a.setLng(new BigDecimal("113.2000000"));
        a.setLevel(1);
        a.setNodeType("NORMAL");
        nodeRepository.save(a);

        NodeEntity b = new NodeEntity();
        b.setLat(new BigDecimal("23.2751000"));
        b.setLng(new BigDecimal("113.2001000"));
        b.setLevel(1);
        b.setNodeType("NORMAL");
        nodeRepository.save(b);

        EdgeEntity ab = new EdgeEntity();
        ab.setFromNode(a);
        ab.setToNode(b);
        ab.setDistanceM(10.0);
        ab.setOneway(false);
        ab.setHasStairs(false);
        ab.setElevator(false);
        ab.setSlopeLevel(0);
        ab.setAccessibleDefault(true);
        ab.setBaseCost(10.0);
        edgeRepository.save(ab);

        EdgeEntity ba = new EdgeEntity();
        ba.setFromNode(b);
        ba.setToNode(a);
        ba.setDistanceM(10.0);
        ba.setOneway(false);
        ba.setHasStairs(false);
        ba.setElevator(false);
        ba.setSlopeLevel(0);
        ba.setAccessibleDefault(true);
        ba.setBaseCost(10.0);
        edgeRepository.save(ba);
        return ab;
    }

    @Test
    void approve_shouldKeepEffectHistory_andRevokePreviousActiveEffect() {
        EdgeEntity edge = seedBidirectionalEdge();

        asUser("user1");
        ObstacleReportCreateRequest createA = new ObstacleReportCreateRequest();
        createA.setEdgeId(edge.getId());
        createA.setType("CONSTRUCTION");
        createA.setReason("A");
        createA.setSubmitterLat(23.2750000);
        createA.setSubmitterLng(113.2000000);
        Long reportAId = reportService.createReport(createA).getId();

        asUser("admin");
        reportService.approve(reportAId, new ObstacleReportReviewRequest());

        ObstacleEffectEntity active1 = effectRepository.findByEdge_IdAndActiveTrue(edge.getId()).orElse(null);
        assertThat(active1).isNotNull();
        assertThat(active1.isActive()).isTrue();
        assertThat(active1.getReport()).isNotNull();
        assertThat(active1.getReport().getId()).isEqualTo(reportAId);

        // Approve another report on the same edge: should revoke the old active effect and insert a new row.
        asUser("user2");
        ObstacleReportCreateRequest createB = new ObstacleReportCreateRequest();
        createB.setEdgeId(edge.getId());
        createB.setType("CONSTRUCTION");
        createB.setReason("B");
        createB.setSubmitterLat(23.2754000);
        createB.setSubmitterLng(113.2004000);
        Long reportBId = reportService.createReport(createB).getId();

        asUser("admin2");
        reportService.approve(reportBId, new ObstacleReportReviewRequest());

        ObstacleEffectEntity active2 = effectRepository.findByEdge_IdAndActiveTrue(edge.getId()).orElse(null);
        assertThat(active2).isNotNull();
        assertThat(active2.getId()).isNotEqualTo(active1.getId());
        assertThat(active2.getReport()).isNotNull();
        assertThat(active2.getReport().getId()).isEqualTo(reportBId);

        // History exists: the original effect row is now inactive.
        List<ObstacleEffectEntity> allEffects = effectRepository.findAll();
        assertThat(allEffects.stream().filter(e -> e.getEdge().getId().equals(edge.getId())).count()).isGreaterThanOrEqualTo(2);
        ObstacleEffectEntity old = allEffects.stream().filter(e -> e.getId().equals(active1.getId())).findFirst().orElse(null);
        assertThat(old).isNotNull();
        assertThat(old.isActive()).isFalse();
        assertThat(old.getRevokedAt()).isNotNull();
        assertThat(old.getRevokedBy()).isNotBlank();

        ObstacleReportEntity reportA = reportRepository.findById(reportAId).orElse(null);
        assertThat(reportA).isNotNull();
        assertThat(reportA.getStatus()).isEqualTo(ObstacleReportService.STATUS_REVOKED);
    }

    @Test
    void expireDueEffects_shouldMarkReportExpired_andDeactivateEffects() {
        EdgeEntity edge = seedBidirectionalEdge();

        asUser("user1");
        ObstacleReportCreateRequest create = new ObstacleReportCreateRequest();
        create.setEdgeId(edge.getId());
        create.setType("CONSTRUCTION");
        create.setReason("TEMP");
        Long reportId = reportService.createReport(create).getId();

        asUser("admin");
        ObstacleReportReviewRequest approve = new ObstacleReportReviewRequest();
        approve.setDurationMinutes(1);
        reportService.approve(reportId, approve);

        ObstacleEffectEntity active = effectRepository.findByEdge_IdAndActiveTrue(edge.getId()).orElse(null);
        assertThat(active).isNotNull();
        assertThat(active.getEndAt()).isNotNull();

        effectManager.expireDueEffects(Instant.now().plusSeconds(120));

        ObstacleEffectEntity after = effectRepository.findById(active.getId()).orElse(null);
        assertThat(after).isNotNull();
        assertThat(after.isActive()).isFalse();

        ObstacleReportEntity report = reportRepository.findById(reportId).orElse(null);
        assertThat(report).isNotNull();
        assertThat(report.getStatus()).isEqualTo(ObstacleReportService.STATUS_EXPIRED);
    }

    @Test
    void revoke_shouldSetStatusRevoked_andDeactivateEffects() {
        EdgeEntity edge = seedBidirectionalEdge();

        asUser("user1");
        ObstacleReportCreateRequest create = new ObstacleReportCreateRequest();
        create.setEdgeId(edge.getId());
        create.setType("CONSTRUCTION");
        create.setReason("R");
        Long reportId = reportService.createReport(create).getId();

        asUser("admin");
        reportService.approve(reportId, new ObstacleReportReviewRequest());

        assertThat(effectRepository.findByEdge_IdAndActiveTrue(edge.getId())).isPresent();

        asUser("admin");
        reportService.revoke(reportId, "manual revoke");

        assertThat(effectRepository.findByEdge_IdAndActiveTrue(edge.getId())).isEmpty();
        ObstacleReportEntity report = reportRepository.findById(reportId).orElse(null);
        assertThat(report).isNotNull();
        assertThat(report.getStatus()).isEqualTo(ObstacleReportService.STATUS_REVOKED);
    }
}
