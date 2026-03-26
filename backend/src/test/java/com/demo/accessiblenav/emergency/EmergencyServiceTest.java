package com.demo.accessiblenav.emergency;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.emergency.dto.AddContactRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastRequest;
import com.demo.accessiblenav.emergency.dto.TriggerEmergencyRequest;
import com.demo.accessiblenav.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * EmergencyService 单元测试 — 使用 Mockito 隔离所有依赖。
 */
@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock EmergencyEventRepository eventRepository;
    @Mock EmergencyContactRepository contactRepository;
    @Mock VolunteerRepository volunteerRepository;
    @Mock EmergencyResponseRepository responseRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock SmsNotificationService smsService;
    @Mock EmergencyZoneRepository zoneRepository;
    @Mock EmergencyBroadcastRepository broadcastRepository;
    @Mock OperationLogService logService;

    @InjectMocks
    EmergencyService emergencyService;

    @BeforeEach
    void setTenant() {
        TenantContext.set("test-tenant");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ triggerEmergency

    @Test
    void triggerEmergency_shouldSaveEventAndReturnDto() {
        TriggerEmergencyRequest req = buildTriggerRequest();

        when(zoneRepository.findZonesNear(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(contactRepository.findByUserIdAndTenantId(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(volunteerRepository.findByTenantIdAndActiveTrueOrderByLastSeenAtDesc(anyString()))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<EmergencyEventEntity> captor = ArgumentCaptor.forClass(EmergencyEventEntity.class);
        when(eventRepository.save(captor.capture())).thenAnswer(inv -> {
            EmergencyEventEntity e = captor.getValue();
            e.setId(1L);
            return e;
        });

        var dto = emergencyService.triggerEmergency("user1", "user1", req);

        assertThat(dto).isNotNull();
        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getSeverity()).isEqualTo("HIGH");
        verify(eventRepository).save(any(EmergencyEventEntity.class));
        verify(logService).log(eq("EMERGENCY_TRIGGERED"), anyString());
    }

    @Test
    void triggerEmergency_criticalSeverity_shouldSetRadius1000() {
        TriggerEmergencyRequest req = buildTriggerRequest();
        req.setSeverity("critical"); // 测试大小写归一化

        when(zoneRepository.findZonesNear(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(contactRepository.findByUserIdAndTenantId(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(volunteerRepository.findByTenantIdAndActiveTrueOrderByLastSeenAtDesc(anyString()))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<EmergencyEventEntity> captor = ArgumentCaptor.forClass(EmergencyEventEntity.class);
        when(eventRepository.save(captor.capture())).thenAnswer(inv -> {
            EmergencyEventEntity e = captor.getValue();
            e.setId(2L);
            return e;
        });

        emergencyService.triggerEmergency("user1", "user1", req);

        assertThat(captor.getValue().getBroadcastRadiusM()).isEqualTo(1000);
    }

    @Test
    void triggerEmergency_normalSeverity_shouldSetRadius500() {
        TriggerEmergencyRequest req = buildTriggerRequest();
        req.setSeverity("NORMAL");

        when(zoneRepository.findZonesNear(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(Collections.emptyList());
        when(contactRepository.findByUserIdAndTenantId(anyString(), anyString()))
                .thenReturn(Collections.emptyList());
        when(volunteerRepository.findByTenantIdAndActiveTrueOrderByLastSeenAtDesc(anyString()))
                .thenReturn(Collections.emptyList());

        ArgumentCaptor<EmergencyEventEntity> captor = ArgumentCaptor.forClass(EmergencyEventEntity.class);
        when(eventRepository.save(captor.capture())).thenAnswer(inv -> {
            EmergencyEventEntity e = captor.getValue();
            e.setId(3L);
            return e;
        });

        emergencyService.triggerEmergency("user1", "user1", req);

        assertThat(captor.getValue().getBroadcastRadiusM()).isEqualTo(500);
    }

    // ------------------------------------------------------------------ cancelEmergency

    @Test
    void cancelEmergency_ownerCanCancel() {
        EmergencyEventEntity event = buildActiveEvent(10L, "user1");
        when(eventRepository.findById(10L)).thenReturn(Optional.of(event));
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        emergencyService.cancelEmergency(10L, "user1");

        assertThat(event.getStatus()).isEqualTo(EmergencyStatus.CANCELLED);
        verify(logService).log(eq("EMERGENCY_CANCELLED"), anyString());
    }

    @Test
    void cancelEmergency_nonOwner_shouldThrow() {
        EmergencyEventEntity event = buildActiveEvent(11L, "user1");
        when(eventRepository.findById(11L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> emergencyService.cancelEmergency(11L, "other-user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("only owner can cancel");
    }

    @Test
    void cancelEmergency_resolvedEvent_shouldThrow() {
        EmergencyEventEntity event = buildActiveEvent(12L, "user1");
        event.setStatus(EmergencyStatus.RESOLVED);
        when(eventRepository.findById(12L)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> emergencyService.cancelEmergency(12L, "user1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("status not allowed for cancel");
    }

    // ------------------------------------------------------------------ addContact / deleteContact

    @Test
    void addContact_shouldSaveAndReturnDto() {
        AddContactRequest req = new AddContactRequest();
        req.setContactName("妈妈");
        req.setPhoneNumber("13900139000");
        req.setRelationship(ContactRelationship.FAMILY);
        req.setIsPrimary(true);

        ArgumentCaptor<EmergencyContactEntity> captor = ArgumentCaptor.forClass(EmergencyContactEntity.class);
        when(contactRepository.save(captor.capture())).thenAnswer(inv -> {
            EmergencyContactEntity c = captor.getValue();
            c.setId(1L);
            return c;
        });

        var dto = emergencyService.addContact("user1", req);

        assertThat(dto.getContactName()).isEqualTo("妈妈");
        assertThat(dto.getPhoneNumber()).isEqualTo("13900139000");
    }

    @Test
    void deleteContact_wrongOwner_shouldThrow() {
        EmergencyContactEntity contact = new EmergencyContactEntity();
        contact.setId(1L);
        contact.setUserId("owner");
        contact.setTenantId("test-tenant");

        when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

        assertThatThrownBy(() -> emergencyService.deleteContact(1L, "other"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no permission");
    }

    // ------------------------------------------------------------------ volunteer

    @Test
    void registerVolunteer_duplicate_shouldThrow() {
        when(volunteerRepository.existsByUserIdAndTenantId("v1", "test-tenant")).thenReturn(true);

        assertThatThrownBy(() -> emergencyService.registerAsVolunteer("v1", "志愿者", "13800138000"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("volunteer already exists");
    }

    @Test
    void registerVolunteer_newUser_shouldSave() {
        when(volunteerRepository.existsByUserIdAndTenantId("v2", "test-tenant")).thenReturn(false);
        when(volunteerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 不抛异常即为成功
        emergencyService.registerAsVolunteer("v2", "志愿者2", "13700137000");
        verify(volunteerRepository).save(any(VolunteerEntity.class));
    }

    // ------------------------------------------------------------------ helpers

    private TriggerEmergencyRequest buildTriggerRequest() {
        TriggerEmergencyRequest req = new TriggerEmergencyRequest();
        req.setEventType(EmergencyType.FALL);
        req.setDescription("测试");
        req.setLat(31.23);
        req.setLng(121.47);
        req.setSeverity("HIGH");
        return req;
    }

    private EmergencyEventEntity buildActiveEvent(Long id, String userId) {
        EmergencyEventEntity e = new EmergencyEventEntity();
        e.setId(id);
        e.setUserId(userId);
        e.setStatus(EmergencyStatus.ACTIVE);
        e.setTenantId("test-tenant");
        return e;
    }
}
