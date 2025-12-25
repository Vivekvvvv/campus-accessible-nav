package com.demo.accessiblenav.experiment;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "t_experiment_assignment",
        uniqueConstraints = @UniqueConstraint(columnNames = {"experiment_id", "user_id"}))
public class ExperimentAssignmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String variant;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @PrePersist
    protected void onCreate() {
        if (assignedAt == null) assignedAt = Instant.now();
    }

    public Long getId() { return id; }

    public ExperimentEntity getExperiment() { return experiment; }
    public void setExperiment(ExperimentEntity experiment) { this.experiment = experiment; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getVariant() { return variant; }
    public void setVariant(String variant) { this.variant = variant; }

    public Instant getAssignedAt() { return assignedAt; }
}
