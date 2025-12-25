package com.demo.accessiblenav.spatial;

import com.demo.accessiblenav.facility.FacilityEntity;
import com.demo.accessiblenav.facility.FacilityRepository;
import com.demo.accessiblenav.graph.EdgeEntity;
import com.demo.accessiblenav.graph.EdgeRepository;
import com.demo.accessiblenav.graph.NodeEntity;
import com.demo.accessiblenav.graph.NodeRepository;
import com.demo.accessiblenav.it.PostgresITBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL regression for SpatialIndexManager native SQL and index rebuild.
 */
public class SpatialIndexPostgresIT extends PostgresITBase {

    @Autowired
    SpatialIndexManager spatialIndexManager;

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    EdgeRepository edgeRepository;

    @Autowired
    FacilityRepository facilityRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearSpatialIndex() {
        // Ensure deterministic stats per test.
        spatialIndexManager.rebuildAllIndexes();
    }

    @Test
    void postgisExtension_shouldBeAvailable() {
        Integer installed = jdbcTemplate.queryForObject(
                "select count(*) from pg_extension where extname = 'postgis'",
                Integer.class
        );
        assertThat(installed).isNotNull();
        assertThat(installed).isGreaterThan(0);
    }

    @Test
    void rebuildAllIndexes_shouldIndexNodesEdgesAndFacilities() {
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

        EdgeEntity e = new EdgeEntity();
        e.setFromNode(a);
        e.setToNode(b);
        e.setDistanceM(10.0);
        e.setOneway(false);
        e.setHasStairs(false);
        e.setElevator(false);
        e.setSlopeLevel(0);
        e.setAccessibleDefault(true);
        e.setBaseCost(10.0);
        edgeRepository.save(e);

        FacilityEntity f = new FacilityEntity();
        f.setFacilityType(com.demo.accessiblenav.facility.FacilityType.ELEVATOR);
        f.setName("Test Facility");
        f.setLat(23.2750500);
        f.setLng(113.2000500);
        facilityRepository.save(f);

        spatialIndexManager.rebuildAllIndexes();
        RTreeSpatialIndex.SpatialIndexStats stats = spatialIndexManager.getStats();

        assertThat(stats.getNodeCount()).isGreaterThanOrEqualTo(2);
        assertThat(stats.getEdgeCount()).isGreaterThanOrEqualTo(1);
        assertThat(stats.getFacilityCount()).isGreaterThanOrEqualTo(1);
    }
}
