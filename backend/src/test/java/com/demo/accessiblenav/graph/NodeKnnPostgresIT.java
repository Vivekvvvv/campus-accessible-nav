package com.demo.accessiblenav.graph;

import com.demo.accessiblenav.it.PostgresITBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies PostGIS KNN snapping support on PostgreSQL (geog column + GIST index + <-> queries).
 */
public class NodeKnnPostgresIT extends PostgresITBase {

    @Autowired
    NodeRepository nodeRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void migrations_shouldCreateGeogColumnAndGistIndex() {
        Integer cols = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.columns " +
                        "where table_schema = current_schema() and table_name = 't_node' and column_name = 'geog'",
                Integer.class
        );
        assertThat(cols).isNotNull();
        assertThat(cols).isEqualTo(1);

        List<String> indexes = jdbcTemplate.queryForList(
                "select indexname from pg_indexes where schemaname = current_schema() and tablename = 't_node'",
                String.class
        );
        assertThat(indexes).contains("idx_node_geog");
    }

    @Test
    void knnQuery_shouldReturnNearestNodeId() {
        NodeEntity a = new NodeEntity();
        a.setLat(new BigDecimal("23.2750000"));
        a.setLng(new BigDecimal("113.2000000"));
        a.setLevel(1);
        a.setNodeType("NORMAL");
        nodeRepository.save(a);

        NodeEntity b = new NodeEntity();
        b.setLat(new BigDecimal("23.2752000"));
        b.setLng(new BigDecimal("113.2002000"));
        b.setLevel(1);
        b.setNodeType("NORMAL");
        nodeRepository.save(b);

        Long nearest = nodeRepository.findNearestIdWithinMetersByTenant(
                23.2750001,
                113.2000001,
                "default",
                1,
                500.0
        );
        assertThat(nearest).isNotNull();
        assertThat(nearest).isEqualTo(a.getId());
    }

    @Test
    void knnQuery_shouldRespectBboxFilter() {
        NodeEntity a = new NodeEntity();
        a.setLat(new BigDecimal("23.2750000"));
        a.setLng(new BigDecimal("113.2000000"));
        a.setLevel(1);
        a.setNodeType("NORMAL");
        nodeRepository.save(a);

        NodeEntity b = new NodeEntity();
        b.setLat(new BigDecimal("23.2752000"));
        b.setLng(new BigDecimal("113.2002000"));
        b.setLevel(1);
        b.setNodeType("NORMAL");
        nodeRepository.save(b);

        // Bbox excludes "a" but includes "b".
        Long nearest = nodeRepository.findNearestIdWithinMetersAndBboxByTenant(
                23.2750001,
                113.2000001,
                "default",
                1,
                500.0,
                23.2751500,
                23.2752500,
                113.2001500,
                113.2002500
        );
        assertThat(nearest).isNotNull();
        assertThat(nearest).isEqualTo(b.getId());
    }

    @Test
    void knnQuery_shouldReturnNull_whenNoNodeWithinRadius() {
        NodeEntity a = new NodeEntity();
        a.setLat(new BigDecimal("23.2750000"));
        a.setLng(new BigDecimal("113.2000000"));
        a.setLevel(1);
        a.setNodeType("NORMAL");
        nodeRepository.save(a);

        Long nearest = nodeRepository.findNearestIdWithinMetersByTenant(
                23.2750001,
                113.2000001,
                "default",
                1,
                0.001
        );
        assertThat(nearest).isNull();
    }
}
