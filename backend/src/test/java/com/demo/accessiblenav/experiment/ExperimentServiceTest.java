package com.demo.accessiblenav.experiment;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.experiment.dto.ExperimentAssignmentResponse;
import com.demo.accessiblenav.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ExperimentService 单元测试 — 验证确定性分流、流量百分比、variant 解析与曝光记录。
 */
@ExtendWith(MockitoExtension.class)
class ExperimentServiceTest {

    @Mock ExperimentRepository experimentRepository;
    @Mock ExperimentAssignmentRepository assignmentRepository;
    @Mock OperationLogService logService;

    @InjectMocks
    ExperimentService experimentService;

    @BeforeEach
    void setTenant() {
        TenantContext.set("test-tenant");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ assign — happy path

    @Test
    void assign_newUser_shouldSaveAndReturnVariant() {
        ExperimentEntity exp = buildRunningExperiment("nav-algo", 100, "[\"control\",\"treatment\"]");
        when(experimentRepository.findByName("nav-algo")).thenReturn(Optional.of(exp));
        when(assignmentRepository.findByExperimentAndUserId(eq(exp), anyString())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExperimentAssignmentResponse resp = experimentService.assign("nav-algo", "user1");

        assertThat(resp.getExperimentName()).isEqualTo("nav-algo");
        assertThat(resp.getVariant()).isIn("control", "treatment");
        verify(assignmentRepository).save(any(ExperimentAssignmentEntity.class));
        verify(logService).log(eq("EXPERIMENT_ASSIGN"), anyString());
    }

    @Test
    void assign_existingUser_shouldReturnCachedVariant() {
        ExperimentEntity exp = buildRunningExperiment("nav-algo", 100, null);
        ExperimentAssignmentEntity existing = new ExperimentAssignmentEntity();
        existing.setVariant("control");
        existing.setExperiment(exp);
        existing.setUserId("user1");

        when(experimentRepository.findByName("nav-algo")).thenReturn(Optional.of(exp));
        when(assignmentRepository.findByExperimentAndUserId(eq(exp), anyString())).thenReturn(Optional.of(existing));

        ExperimentAssignmentResponse resp = experimentService.assign("nav-algo", "user1");

        assertThat(resp.getVariant()).isEqualTo("control");
        verify(assignmentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ assign — deterministic bucketing

    @Test
    void assign_sameSeed_shouldAlwaysReturnSameVariant() {
        ExperimentEntity exp = buildRunningExperiment("det-exp", 100, "[\"A\",\"B\"]");
        when(experimentRepository.findByName("det-exp")).thenReturn(Optional.of(exp));
        when(assignmentRepository.findByExperimentAndUserId(any(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        String variant1 = experimentService.assign("det-exp", "stable-user").getVariant();

        // 重置 mock 调用计数，再次分配同一用户
        reset(assignmentRepository);
        when(assignmentRepository.findByExperimentAndUserId(any(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // 需要重新 stub experimentRepository
        when(experimentRepository.findByName("det-exp")).thenReturn(Optional.of(exp));

        String variant2 = experimentService.assign("det-exp", "stable-user").getVariant();

        assertThat(variant1).isEqualTo(variant2);
    }

    @Test
    void assign_differentUsers_shouldDistributeAcrossVariants() {
        ExperimentEntity exp = buildRunningExperiment("dist-exp", 100, "[\"control\",\"treatment\"]");
        when(experimentRepository.findByName("dist-exp")).thenReturn(Optional.of(exp));
        when(assignmentRepository.findByExperimentAndUserId(any(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Set<String> variants = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            variants.add(experimentService.assign("dist-exp", "user-" + i).getVariant());
        }
        // 50 个不同用户应至少命中 2 个 variant
        assertThat(variants.size()).isGreaterThan(1);
    }

    // ------------------------------------------------------------------ assign — traffic

    @Test
    void assign_zeroTraffic_shouldAlwaysReturnFirstVariant() {
        ExperimentEntity exp = buildRunningExperiment("low-exp", 0, "[\"control\",\"treatment\"]");
        when(experimentRepository.findByName("low-exp")).thenReturn(Optional.of(exp));
        when(assignmentRepository.findByExperimentAndUserId(any(), any())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // traffic=0 时 bucket>=0 永远成立，应返回 variants[0]
        for (int i = 0; i < 10; i++) {
            String v = experimentService.assign("low-exp", "u" + i).getVariant();
            assertThat(v).isEqualTo("control");
        }
    }

    // ------------------------------------------------------------------ assign — errors

    @Test
    void assign_notFound_shouldThrow() {
        when(experimentRepository.findByName("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.assign("ghost", "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("experiment not found");
    }

    @Test
    void assign_wrongTenant_shouldThrow() {
        ExperimentEntity exp = buildRunningExperiment("cross-exp", 100, null);
        exp.setTenantId("other-tenant"); // 与 TenantContext("test-tenant") 不一致
        when(experimentRepository.findByName("cross-exp")).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> experimentService.assign("cross-exp", "user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("experiment not found");
    }

    @Test
    void assign_notRunning_shouldThrow() {
        ExperimentEntity exp = buildRunningExperiment("paused-exp", 100, null);
        exp.setStatus("PAUSED");
        when(experimentRepository.findByName("paused-exp")).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> experimentService.assign("paused-exp", "user1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not running");
    }

    // ------------------------------------------------------------------ recordExposure

    @Test
    void recordExposure_shouldLogExposureEvent() {
        experimentService.recordExposure("nav-algo", "user1", null);
        verify(logService).log(eq("EXPERIMENT_EXPOSURE"), anyString());
    }

    @Test
    void recordExposure_customEvent_shouldLogUppercased() {
        experimentService.recordExposure("nav-algo", "user1", "click");
        verify(logService).log(eq("EXPERIMENT_CLICK"), anyString());
    }

    @Test
    void recordExposure_nullUserId_shouldNormalize() {
        // null userId 不抛异常，记录为 anonymous
        experimentService.recordExposure("exp", null, null);
        verify(logService).log(anyString(), contains("userId=anonymous"));
    }

    // ------------------------------------------------------------------ helpers

    private ExperimentEntity buildRunningExperiment(String name, int traffic, String variantsJson) {
        ExperimentEntity e = new ExperimentEntity();
        e.setId(1L);
        e.setName(name);
        e.setStatus("RUNNING");
        e.setTenantId("test-tenant");
        e.setTrafficPercent(traffic);
        e.setVariantsJson(variantsJson);
        return e;
    }
}
