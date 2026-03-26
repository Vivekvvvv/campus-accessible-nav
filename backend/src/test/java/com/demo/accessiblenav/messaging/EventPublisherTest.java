package com.demo.accessiblenav.messaging;

import com.demo.accessiblenav.messaging.events.EmergencyTriggeredEvent;
import com.demo.accessiblenav.messaging.events.GraphUpdatedEvent;
import com.demo.accessiblenav.messaging.events.ObstacleReportedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * 消息/事件模块单元测试 — 验证 EventPublisher 广播与 DomainEvent 构造。
 */
@ExtendWith(MockitoExtension.class)
class EventPublisherTest {

    @Mock
    ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    EventPublisher eventPublisher;

    // ------------------------------------------------------------------ publish

    @Test
    void publish_shouldDelegateToApplicationEventPublisher() {
        ObstacleReportedEvent event = new ObstacleReportedEvent("obs-1", "user1");

        eventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(event);
    }

    @Test
    void publishAll_shouldPublishEachEvent() {
        ObstacleReportedEvent e1 = new ObstacleReportedEvent("obs-1", "user1");
        ObstacleReportedEvent e2 = new ObstacleReportedEvent("obs-2", "user2");

        eventPublisher.publishAll(List.of(e1, e2));

        verify(applicationEventPublisher, times(2)).publishEvent(any(DomainEvent.class));
    }

    @Test
    void publishAll_emptyList_shouldNotPublishAnything() {
        eventPublisher.publishAll(List.of());

        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    // ------------------------------------------------------------------ DomainEvent base

    @Test
    void domainEvent_shouldHaveUniqueEventIds() {
        ObstacleReportedEvent e1 = new ObstacleReportedEvent("obs-1", "u1");
        ObstacleReportedEvent e2 = new ObstacleReportedEvent("obs-2", "u2");

        assertThat(e1.getEventId()).isNotEqualTo(e2.getEventId());
    }

    @Test
    void domainEvent_shouldSetAggregateFields() {
        ObstacleReportedEvent event = new ObstacleReportedEvent("obs-99", "reporter");
        event.setLat(31.23);
        event.setLng(121.47);
        event.setObstacleType("STEP");
        event.setRequiresReview(true);

        assertThat(event.getAggregateId()).isEqualTo("obs-99");
        assertThat(event.getAggregateType()).isEqualTo("Obstacle");
        assertThat(event.getEventType()).isEqualTo("ObstacleReportedEvent");
        assertThat(event.getOccurredAt()).isNotNull();
        assertThat(event.isRequiresReview()).isTrue();
        assertThat(event.getLat()).isEqualTo(31.23);
    }

    @Test
    void graphUpdatedEvent_shouldSetUpdateType() {
        GraphUpdatedEvent event = new GraphUpdatedEvent("node-1", GraphUpdatedEvent.UpdateType.NODE_ADDED);

        assertThat(event.getUpdateType()).isEqualTo(GraphUpdatedEvent.UpdateType.NODE_ADDED);
        assertThat(event.getAggregateType()).isEqualTo("Graph");
        assertThat(event.getEventType()).isEqualTo("GraphUpdatedEvent");
    }

    @Test
    void emergencyTriggeredEvent_shouldContainLocationAndType() {
        EmergencyTriggeredEvent event = new EmergencyTriggeredEvent("evt-1", "user99");
        event.setEmergencyType("FALL");
        event.setLat(31.23);
        event.setLng(121.47);

        assertThat(event.getUserId()).isEqualTo("user99");
        assertThat(event.getEmergencyType()).isEqualTo("FALL");
        assertThat(event.getLat()).isEqualTo(31.23);
        assertThat(event.getLng()).isEqualTo(121.47);
        assertThat(event.getAggregateId()).isEqualTo("evt-1");
    }

    // ------------------------------------------------------------------ publish captures correct event

    @Test
    void publish_shouldPassExactEventInstance() {
        EmergencyTriggeredEvent event = new EmergencyTriggeredEvent("e1", "u1");
        event.setEmergencyType("LOST");
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);

        eventPublisher.publish(event);

        verify(applicationEventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isSameAs(event);
    }
}
