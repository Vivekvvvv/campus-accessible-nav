package com.demo.accessiblenav.experiment;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.experiment.dto.ExperimentAssignmentResponse;
import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ExperimentService {

    private final ExperimentRepository experimentRepository;
    private final ExperimentAssignmentRepository assignmentRepository;
    private final OperationLogService logService;

    public ExperimentService(ExperimentRepository experimentRepository,
                             ExperimentAssignmentRepository assignmentRepository,
                             OperationLogService logService) {
        this.experimentRepository = experimentRepository;
        this.assignmentRepository = assignmentRepository;
        this.logService = logService;
    }

    @Transactional
    public ExperimentAssignmentResponse assign(String experimentName, String userIdRaw) {
        String tenantId = TenantContext.get();
        String userId = normalizeUserId(userIdRaw);
        ExperimentEntity experiment = experimentRepository.findByName(experimentName)
                .orElseThrow(() -> new IllegalArgumentException("experiment not found"));
        if (!tenantId.equals(experiment.getTenantId())) {
            throw new IllegalArgumentException("experiment not found");
        }
        if (!"RUNNING".equalsIgnoreCase(experiment.getStatus())) {
            throw new IllegalStateException("experiment is not running");
        }

        ExperimentAssignmentEntity existing = assignmentRepository
                .findByExperimentAndUserId(experiment, userId)
                .orElse(null);
        if (existing != null) {
            return new ExperimentAssignmentResponse(experiment.getName(), existing.getVariant());
        }

        String variant = chooseVariant(experiment, tenantId, userId);
        ExperimentAssignmentEntity entity = new ExperimentAssignmentEntity();
        entity.setExperiment(experiment);
        entity.setUserId(userId);
        entity.setVariant(variant);
        assignmentRepository.save(entity);

        logService.log("EXPERIMENT_ASSIGN", "name=" + experiment.getName() + ", variant=" + variant);
        return new ExperimentAssignmentResponse(experiment.getName(), variant);
    }

    public void recordExposure(String experimentName, String userIdRaw, String eventRaw) {
        String userId = normalizeUserId(userIdRaw);
        String event = (eventRaw == null || eventRaw.trim().isEmpty()) ? "EXPOSURE" : eventRaw.trim().toUpperCase(Locale.ROOT);
        logService.log("EXPERIMENT_" + event, "name=" + experimentName + ", userId=" + userId);
    }

    private String chooseVariant(ExperimentEntity experiment, String tenantId, String userId) {
        List<String> variants = parseVariants(experiment.getVariantsJson());
        if (variants.isEmpty()) {
            variants = List.of("control", "treatment");
        }

        int traffic = Math.max(0, Math.min(100, experiment.getTrafficPercent()));
        int bucket = deterministicBucket(tenantId + ":" + experiment.getName() + ":" + userId);
        if (bucket >= traffic) {
            return variants.get(0);
        }

        int index = deterministicBucket("variant:" + tenantId + ":" + experiment.getName() + ":" + userId) % variants.size();
        return variants.get(index);
    }

    private int deterministicBucket(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            int v = ((hash[0] & 0xFF) << 8) | (hash[1] & 0xFF);
            return Math.floorMod(v, 100);
        } catch (Exception e) {
            return Math.floorMod(input.hashCode(), 100);
        }
    }

    private List<String> parseVariants(String variantsJson) {
        if (variantsJson == null || variantsJson.trim().isEmpty()) {
            return List.of();
        }
        String raw = variantsJson.trim();
        if (raw.startsWith("[") && raw.endsWith("]")) {
            raw = raw.substring(1, raw.length() - 1);
        }
        String[] parts = raw.split(",");
        List<String> out = new ArrayList<>();
        for (String p : parts) {
            String s = p == null ? "" : p.trim();
            if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
                s = s.substring(1, s.length() - 1);
            }
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }

    private String normalizeUserId(String userIdRaw) {
        String userId = userIdRaw == null ? "anonymous" : userIdRaw.trim();
        if (userId.isEmpty()) {
            userId = "anonymous";
        }
        if (userId.length() > 64) {
            userId = userId.substring(0, 64);
        }
        return userId;
    }
}
