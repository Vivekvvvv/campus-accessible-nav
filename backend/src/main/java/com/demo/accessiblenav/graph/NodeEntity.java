package com.demo.accessiblenav.graph;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(
        name = "t_node",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_node_lat_lng_level", columnNames = {"lat", "lng", "level"})
        },
        indexes = {
                @Index(name = "idx_node_lat", columnList = "lat"),
                @Index(name = "idx_node_lng", columnList = "lng"),
                @Index(name = "idx_node_level", columnList = "level")
        }
)
public class NodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(nullable = false, precision = 10, scale = 7)
    private BigDecimal lng;

    @Column(nullable = false)
    private Integer level = 1;

    @Column(name = "node_type", nullable = false, length = 32)
    private String nodeType;

    @Column(name = "extra_json", columnDefinition = "TEXT")
    private String extraJson;

    @Column(name = "building_id")
    private Long buildingId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    public Long getId() {
        return id;
    }

    public BigDecimal getLat() {
        return lat;
    }

    public void setLat(BigDecimal lat) {
        this.lat = lat;
    }

    public BigDecimal getLng() {
        return lng;
    }

    public void setLng(BigDecimal lng) {
        this.lng = lng;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public String getNodeType() {
        return nodeType;
    }

    public void setNodeType(String nodeType) {
        this.nodeType = nodeType;
    }

    public String getExtraJson() {
        return extraJson;
    }

    public void setExtraJson(String extraJson) {
        this.extraJson = extraJson;
    }

    public Long getBuildingId() {
        return buildingId;
    }

    public void setBuildingId(Long buildingId) {
        this.buildingId = buildingId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}
