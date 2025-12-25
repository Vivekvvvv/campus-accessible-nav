package com.demo.accessiblenav.experiment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "t_experiment")
public class ExperimentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Column(length = 512)
    private String description;

    @Column(nullable = false, length = 16)
    private String status = "DRAFT";

    @Column(name = "traffic_percent", nullable = false)
    private int trafficPercent = 100;

    @Column(name = "variants_json", nullable = false, columnDefinition = "TEXT")
    private String variantsJson = "[\"control\",\"treatment\"]";

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTrafficPercent() { return trafficPercent; }
    public void setTrafficPercent(int trafficPercent) { this.trafficPercent = trafficPercent; }

    public String getVariantsJson() { return variantsJson; }
    public void setVariantsJson(String variantsJson) { this.variantsJson = variantsJson; }

    public String getConfigJson() { return configJson; }
    public void setConfigJson(String configJson) { this.configJson = configJson; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
