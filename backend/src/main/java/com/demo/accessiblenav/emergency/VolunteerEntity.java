package com.demo.accessiblenav.emergency;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 志愿者实体
 */
@Entity
@Table(name = "t_volunteer")
public class VolunteerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "last_location_lat")
    private Double lastLocationLat;

    @Column(name = "last_location_lng")
    private Double lastLocationLng;

    @Column(name = "last_location_at")
    private Instant lastLocationAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId = "default";

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public Double getLastLocationLat() { return lastLocationLat; }
    public void setLastLocationLat(Double lastLocationLat) { this.lastLocationLat = lastLocationLat; }

    public Double getLastLocationLng() { return lastLocationLng; }
    public void setLastLocationLng(Double lastLocationLng) { this.lastLocationLng = lastLocationLng; }

    public Instant getLastLocationAt() { return lastLocationAt; }
    public void setLastLocationAt(Instant lastLocationAt) { this.lastLocationAt = lastLocationAt; }

    public Instant getCreatedAt() { return createdAt; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }
}
