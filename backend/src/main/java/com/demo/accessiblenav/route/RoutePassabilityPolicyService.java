package com.demo.accessiblenav.route;

import com.demo.accessiblenav.auth.AdminPermissionService;
import com.demo.accessiblenav.auth.UserRole;
import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.route.dto.RoutePassabilityPolicyDto;
import com.demo.accessiblenav.route.dto.RoutePassabilityPolicyUpdateRequest;
import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoutePassabilityPolicyService {

    private final RoutePassabilityPolicyRepository repository;
    private final AdminPermissionService permissionService;
    private final OperationLogService operationLogService;

    @Value("${app.routing.passability.penalty-enabled:true}")
    private boolean defaultPenaltyEnabled;

    @Value("${app.routing.passability.min-clamp:0.01}")
    private double defaultMinClamp;

    @Value("${app.routing.passability.weight-factor:1.0}")
    private double defaultWeightFactor;

    public RoutePassabilityPolicyService(RoutePassabilityPolicyRepository repository,
                                         AdminPermissionService permissionService,
                                         OperationLogService operationLogService) {
        this.repository = repository;
        this.permissionService = permissionService;
        this.operationLogService = operationLogService;
    }

    @Transactional(readOnly = true)
    public ResolvedRoutePassabilityPolicy resolveForCurrentTenant() {
        String tenantId = TenantContext.get();
        RoutePassabilityPolicyEntity entity = repository.findByTenantId(tenantId).orElse(null);
        if (entity == null) {
            return new ResolvedRoutePassabilityPolicy(
                    tenantId,
                    defaultPenaltyEnabled,
                    clamp(defaultMinClamp, 0.01, 0.5, 0.01),
                    clamp(defaultWeightFactor, 0.0, 2.0, 1.0),
                    "DEFAULT",
                    null,
                    null
            );
        }
        return new ResolvedRoutePassabilityPolicy(
                tenantId,
                entity.isPassabilityPenaltyEnabled(),
                clamp(entity.getPassabilityMinClamp(), 0.01, 0.5, clamp(defaultMinClamp, 0.01, 0.5, 0.01)),
                clamp(entity.getPassabilityWeightFactor(), 0.0, 2.0, clamp(defaultWeightFactor, 0.0, 2.0, 1.0)),
                "TENANT_POLICY",
                entity.getUpdatedBy(),
                entity.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public RoutePassabilityPolicyDto getForCurrentTenant() {
        return toDto(resolveForCurrentTenant());
    }

    @Transactional
    public RoutePassabilityPolicyDto updateForCurrentTenant(RoutePassabilityPolicyUpdateRequest request) {
        permissionService.requireAny(UserRole.ADMIN, UserRole.EDITOR);

        String tenantId = TenantContext.get();
        RoutePassabilityPolicyEntity entity = repository.findByTenantId(tenantId)
                .orElseGet(() -> {
                    RoutePassabilityPolicyEntity created = new RoutePassabilityPolicyEntity();
                    created.setTenantId(tenantId);
                    created.setPassabilityPenaltyEnabled(defaultPenaltyEnabled);
                    created.setPassabilityMinClamp(clamp(defaultMinClamp, 0.01, 0.5, 0.01));
                    created.setPassabilityWeightFactor(clamp(defaultWeightFactor, 0.0, 2.0, 1.0));
                    return created;
                });

        if (request.getPassabilityPenaltyEnabled() != null) {
            entity.setPassabilityPenaltyEnabled(request.getPassabilityPenaltyEnabled());
        }
        if (request.getPassabilityMinClamp() != null) {
            entity.setPassabilityMinClamp(clamp(request.getPassabilityMinClamp(), 0.01, 0.5, 0.01));
        }
        if (request.getPassabilityWeightFactor() != null) {
            entity.setPassabilityWeightFactor(clamp(request.getPassabilityWeightFactor(), 0.0, 2.0, 1.0));
        }

        String actor = permissionService.currentUsername();
        entity.setUpdatedBy(actor == null || actor.isBlank() ? "system" : actor);

        RoutePassabilityPolicyEntity saved = repository.save(entity);

        operationLogService.log("ROUTE_PASSABILITY_POLICY_UPDATED",
                "tenant=" + tenantId
                        + ", enabled=" + saved.isPassabilityPenaltyEnabled()
                        + ", minClamp=" + saved.getPassabilityMinClamp()
                        + ", weightFactor=" + saved.getPassabilityWeightFactor());

        return toDto(new ResolvedRoutePassabilityPolicy(
                saved.getTenantId(),
                saved.isPassabilityPenaltyEnabled(),
                saved.getPassabilityMinClamp(),
                saved.getPassabilityWeightFactor(),
                "TENANT_POLICY",
                saved.getUpdatedBy(),
                saved.getUpdatedAt()
        ));
    }

    private RoutePassabilityPolicyDto toDto(ResolvedRoutePassabilityPolicy policy) {
        return new RoutePassabilityPolicyDto(
                policy.getTenantId(),
                policy.isPassabilityPenaltyEnabled(),
                policy.getPassabilityMinClamp(),
                policy.getPassabilityWeightFactor(),
                policy.getUpdatedBy(),
                policy.getUpdatedAt()
        );
    }

    private double clamp(Double value, double min, double max, double fallback) {
        double v = value == null ? fallback : value;
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    public static final class ResolvedRoutePassabilityPolicy {
        private final String tenantId;
        private final boolean passabilityPenaltyEnabled;
        private final double passabilityMinClamp;
        private final double passabilityWeightFactor;
        private final String source;
        private final String updatedBy;
        private final java.time.Instant updatedAt;

        public ResolvedRoutePassabilityPolicy(String tenantId,
                                              boolean passabilityPenaltyEnabled,
                                              double passabilityMinClamp,
                                              double passabilityWeightFactor,
                                              String source,
                                              String updatedBy,
                                              java.time.Instant updatedAt) {
            this.tenantId = tenantId;
            this.passabilityPenaltyEnabled = passabilityPenaltyEnabled;
            this.passabilityMinClamp = passabilityMinClamp;
            this.passabilityWeightFactor = passabilityWeightFactor;
            this.source = source;
            this.updatedBy = updatedBy;
            this.updatedAt = updatedAt;
        }

        public String getTenantId() {
            return tenantId;
        }

        public boolean isPassabilityPenaltyEnabled() {
            return passabilityPenaltyEnabled;
        }

        public double getPassabilityMinClamp() {
            return passabilityMinClamp;
        }

        public double getPassabilityWeightFactor() {
            return passabilityWeightFactor;
        }

        public String getSource() {
            return source;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public java.time.Instant getUpdatedAt() {
            return updatedAt;
        }
    }
}
