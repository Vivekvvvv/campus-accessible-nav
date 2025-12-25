package com.demo.accessiblenav.emergency;

import com.demo.accessiblenav.audit.OperationLogService;
import com.demo.accessiblenav.emergency.dto.AddContactRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyAlertMessage;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastDto;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyContactDto;
import com.demo.accessiblenav.emergency.dto.EmergencyDispatchTaskDto;
import com.demo.accessiblenav.emergency.dto.EmergencyEventDto;
import com.demo.accessiblenav.emergency.dto.TriggerEmergencyRequest;
import com.demo.accessiblenav.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class EmergencyService {

    private static final Logger log = LoggerFactory.getLogger(EmergencyService.class);
    private static final double DEFAULT_NEARBY_RANGE_DEGREES = 0.005;

    private final EmergencyEventRepository eventRepository;
    private final EmergencyContactRepository contactRepository;
    private final VolunteerRepository volunteerRepository;
    private final EmergencyResponseRepository responseRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SmsNotificationService smsService;
    private final EmergencyZoneRepository zoneRepository;
    private final EmergencyBroadcastRepository broadcastRepository;
    private final OperationLogService logService;

    public EmergencyService(EmergencyEventRepository eventRepository,
                            EmergencyContactRepository contactRepository,
                            VolunteerRepository volunteerRepository,
                            EmergencyResponseRepository responseRepository,
                            SimpMessagingTemplate messagingTemplate,
                            SmsNotificationService smsService,
                            EmergencyZoneRepository zoneRepository,
                            EmergencyBroadcastRepository broadcastRepository,
                            OperationLogService logService) {
        this.eventRepository = eventRepository;
        this.contactRepository = contactRepository;
        this.volunteerRepository = volunteerRepository;
        this.responseRepository = responseRepository;
        this.messagingTemplate = messagingTemplate;
        this.smsService = smsService;
        this.zoneRepository = zoneRepository;
        this.broadcastRepository = broadcastRepository;
        this.logService = logService;
    }

    @Transactional
    public EmergencyEventDto triggerEmergency(String userId, String username, TriggerEmergencyRequest request) {
        String tenantId = TenantContext.get();
        String severity = normalizeSeverity(request == null ? null : request.getSeverity());

        EmergencyEventEntity event = new EmergencyEventEntity();
        event.setUserId(userId);
        event.setUsername(username);
        event.setEventType(request.getEventType());
        event.setDescription(request.getDescription());
        event.setLat(request.getLat());
        event.setLng(request.getLng());
        event.setAccuracy(request.getAccuracy());
        event.setStatus(EmergencyStatus.ACTIVE);
        event.setSeverity(severity);
        event.setBroadcastRadiusM("CRITICAL".equals(severity) ? 1000 : 500);
        event.setTenantId(tenantId);

        double zoneDeg = 0.01;
        List<EmergencyZoneEntity> zones = zoneRepository.findZonesNear(
                tenantId,
                request.getLat() - zoneDeg,
                request.getLat() + zoneDeg,
                request.getLng() - zoneDeg,
                request.getLng() + zoneDeg
        );
        if (!zones.isEmpty()) {
            event.setZoneId(zones.get(0).getId());
        }

        EmergencyEventEntity saved = eventRepository.save(event);

        notifySecurityCenter(saved);
        notifyNearbyVolunteers(saved);
        notifyEmergencyContacts(userId, saved);

        logService.log("EMERGENCY_TRIGGERED", "eventId=" + saved.getId() + ", severity=" + saved.getSeverity());
        return toDto(saved);
    }

    @Transactional
    public void respondToEmergency(Long eventId, String responderId, String responderName, ResponderType responderType) {
        EmergencyEventEntity event = requireTenantEvent(eventId);

        if (event.getStatus() != EmergencyStatus.ACTIVE && event.getStatus() != EmergencyStatus.RESPONDING) {
            throw new IllegalStateException("emergency status not allowed for response");
        }

        if (responseRepository.existsByEventIdAndResponderIdAndTenantId(eventId, responderId, event.getTenantId())) {
            throw new IllegalStateException("already responded");
        }

        event.setStatus(EmergencyStatus.RESPONDING);
        event.setHandledBy(responderId);
        eventRepository.save(event);

        EmergencyResponseEntity response = new EmergencyResponseEntity();
        response.setEvent(event);
        response.setResponderId(responderId);
        response.setResponderName(responderName);
        response.setResponderType(responderType);
        response.setTenantId(event.getTenantId());
        responseRepository.save(response);

        messagingTemplate.convertAndSendToUser(
                event.getUserId(),
                "/queue/emergency/update",
                new EmergencyAlertMessage(
                        event.getId(),
                        null,
                        responderName,
                        responderType == null ? null : responderType.getDisplayName(),
                        null,
                        null,
                        null,
                        String.format(Locale.ROOT, "%s %s is responding", responderType == null ? "Responder" : responderType.getDisplayName(), responderName)
                )
        );

        logService.log("EMERGENCY_RESPONDED", "eventId=" + eventId + ", responder=" + responderId + ", type=" + responderType);
    }

    @Transactional
    public void resolveEmergency(Long eventId, String handledBy, String resolutionNote) {
        EmergencyEventEntity event = requireTenantEvent(eventId);

        event.setStatus(EmergencyStatus.RESOLVED);
        event.setHandledBy(handledBy);
        event.setHandledAt(Instant.now());
        event.setResolutionNote(resolutionNote);
        eventRepository.save(event);

        messagingTemplate.convertAndSendToUser(
                event.getUserId(),
                "/queue/emergency/update",
                new EmergencyAlertMessage(
                        event.getId(),
                        null,
                        handledBy,
                        "RESOLVED",
                        resolutionNote,
                        null,
                        null,
                        "Emergency resolved"
                )
        );

        logService.log("EMERGENCY_RESOLVED", "eventId=" + eventId);
    }

    @Transactional
    public void cancelEmergency(Long eventId, String userId) {
        EmergencyEventEntity event = requireTenantEvent(eventId);

        if (!Objects.equals(event.getUserId(), userId)) {
            throw new IllegalStateException("only owner can cancel");
        }
        if (event.getStatus() != EmergencyStatus.ACTIVE && event.getStatus() != EmergencyStatus.RESPONDING) {
            throw new IllegalStateException("status not allowed for cancel");
        }

        event.setStatus(EmergencyStatus.CANCELLED);
        eventRepository.save(event);

        messagingTemplate.convertAndSend("/topic/emergency/cancelled", event.getId());
        logService.log("EMERGENCY_CANCELLED", "eventId=" + eventId);
    }

    @Transactional(readOnly = true)
    public List<EmergencyEventDto> getActiveEmergencies() {
        List<EmergencyStatus> activeStatuses = Arrays.asList(EmergencyStatus.ACTIVE, EmergencyStatus.RESPONDING);
        return eventRepository.findByStatusInAndTenantIdOrderByCreatedAtDesc(activeStatuses, TenantContext.get())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmergencyEventDto> getUserEmergencyHistory(String userId) {
        return eventRepository.findByUserIdAndTenantIdOrderByCreatedAtDesc(userId, TenantContext.get())
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public EmergencyEventDto getEmergencyById(Long eventId) {
        return toDto(requireTenantEvent(eventId));
    }

    @Transactional
    public EmergencyContactDto addContact(String userId, AddContactRequest request) {
        String tenantId = TenantContext.get();
        if (contactRepository.existsByUserIdAndPhoneNumberAndTenantId(userId, request.getPhoneNumber(), tenantId)) {
            throw new IllegalStateException("contact already exists");
        }

        EmergencyContactEntity contact = new EmergencyContactEntity();
        contact.setUserId(userId);
        contact.setContactName(request.getContactName());
        contact.setPhoneNumber(request.getPhoneNumber());
        contact.setEmail(request.getEmail());
        contact.setRelationship(request.getRelationship());
        contact.setIsPrimary(request.getIsPrimary());
        contact.setTenantId(tenantId);

        return toContactDto(contactRepository.save(contact));
    }

    @Transactional(readOnly = true)
    public List<EmergencyContactDto> getUserContacts(String userId) {
        return contactRepository.findByUserIdAndTenantIdOrderByIsPrimaryDesc(userId, TenantContext.get())
                .stream()
                .map(this::toContactDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteContact(Long contactId, String userId) {
        EmergencyContactEntity contact = contactRepository.findById(Objects.requireNonNull(contactId))
                .orElseThrow(() -> new IllegalArgumentException("contact not found"));
        if (!Objects.equals(contact.getUserId(), userId) || !Objects.equals(contact.getTenantId(), TenantContext.get())) {
            throw new IllegalStateException("no permission to delete contact");
        }
        contactRepository.delete(contact);
    }

    @Transactional
    public void registerAsVolunteer(String userId, String name, String phoneNumber) {
        String tenantId = TenantContext.get();
        if (volunteerRepository.existsByUserIdAndTenantId(userId, tenantId)) {
            throw new IllegalStateException("volunteer already exists");
        }

        VolunteerEntity volunteer = new VolunteerEntity();
        volunteer.setUserId(userId);
        volunteer.setName(name);
        volunteer.setPhoneNumber(phoneNumber);
        volunteer.setIsActive(true);
        volunteer.setTenantId(tenantId);
        volunteerRepository.save(volunteer);

        logService.log("EMERGENCY_VOLUNTEER_REGISTERED", "userId=" + userId);
    }

    @Transactional
    public void updateVolunteerLocation(String userId, Double lat, Double lng) {
        VolunteerEntity volunteer = volunteerRepository.findByUserIdAndTenantId(userId, TenantContext.get())
                .orElseThrow(() -> new IllegalArgumentException("volunteer not found"));
        volunteer.setLastLocationLat(lat);
        volunteer.setLastLocationLng(lng);
        volunteer.setLastLocationAt(Instant.now());
        volunteerRepository.save(volunteer);
    }

    @Transactional
    public void setVolunteerActiveStatus(String userId, boolean isActive) {
        VolunteerEntity volunteer = volunteerRepository.findByUserIdAndTenantId(userId, TenantContext.get())
                .orElseThrow(() -> new IllegalArgumentException("volunteer not found"));
        volunteer.setIsActive(isActive);
        volunteerRepository.save(volunteer);
    }

    @Transactional
    public EmergencyBroadcastDto publishBroadcast(String publisherId, EmergencyBroadcastRequest request) {
        String tenantId = TenantContext.get();
        EmergencyBroadcastEntity entity = new EmergencyBroadcastEntity();
        entity.setPublisherId(publisherId);
        entity.setTargetScope(normalizeScope(request.getTargetScope()));
        entity.setSeverity(normalizeSeverity(request.getSeverity()));
        entity.setMessage(request.getMessage().trim());
        entity.setTenantId(tenantId);

        if (request.getEventId() != null) {
            entity.setEvent(requireTenantEvent(request.getEventId()));
        }

        EmergencyBroadcastEntity saved = broadcastRepository.save(entity);
        EmergencyAlertMessage alert = new EmergencyAlertMessage(
                saved.getEvent() == null ? null : saved.getEvent().getId(),
                null,
                publisherId,
                "BROADCAST",
                saved.getMessage(),
                null,
                null,
                saved.getMessage()
        );

        if ("SECURITY".equals(saved.getTargetScope()) || "ALL".equals(saved.getTargetScope())) {
            messagingTemplate.convertAndSend("/topic/emergency/security", alert);
        }
        if ("VOLUNTEER".equals(saved.getTargetScope()) || "ALL".equals(saved.getTargetScope())) {
            messagingTemplate.convertAndSend("/topic/emergency/volunteer", alert);
        }

        logService.log("EMERGENCY_BROADCAST",
                "broadcastId=" + saved.getId() + ", scope=" + saved.getTargetScope() + ", severity=" + saved.getSeverity());
        return toBroadcastDto(saved);
    }

    @Transactional(readOnly = true)
    public List<EmergencyBroadcastDto> latestBroadcasts() {
        return broadcastRepository.findTop100ByTenantIdOrderByCreatedAtDesc(TenantContext.get())
                .stream()
                .map(this::toBroadcastDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<EmergencyDispatchTaskDto> suggestVolunteerDispatch(Long eventId, int limit) {
        EmergencyEventEntity event = requireTenantEvent(eventId);
        double range = Math.max(DEFAULT_NEARBY_RANGE_DEGREES, event.getBroadcastRadiusM() / 111320.0);

        List<VolunteerEntity> volunteers = volunteerRepository.findNearbyActiveVolunteersByTenant(
                event.getTenantId(),
                event.getLat() - range,
                event.getLat() + range,
                event.getLng() - range,
                event.getLng() + range
        );

        return volunteers.stream()
                .filter(v -> !responseRepository.existsByEventIdAndResponderIdAndTenantId(event.getId(), v.getUserId(), event.getTenantId()))
                .map(v -> toDispatchTask(event, v))
                .sorted((a, b) -> Double.compare(
                        a.getDistanceMeters() == null ? Double.MAX_VALUE : a.getDistanceMeters(),
                        b.getDistanceMeters() == null ? Double.MAX_VALUE : b.getDistanceMeters()))
                .limit(Math.max(1, Math.min(20, limit)))
                .collect(Collectors.toList());
    }

    private void notifySecurityCenter(EmergencyEventEntity event) {
        EmergencyAlertMessage alert = new EmergencyAlertMessage(
                event.getId(),
                event.getUserId(),
                event.getUsername(),
                event.getEventType().getDisplayName(),
                event.getDescription(),
                event.getLat(),
                event.getLng(),
                String.format(Locale.ROOT, "Emergency %s at %.6f, %.6f", event.getEventType().getDisplayName(), event.getLat(), event.getLng())
        );
        messagingTemplate.convertAndSend("/topic/emergency/security", alert);
    }

    private void notifyNearbyVolunteers(EmergencyEventEntity event) {
        double range = Math.max(DEFAULT_NEARBY_RANGE_DEGREES, event.getBroadcastRadiusM() / 111320.0);
        List<VolunteerEntity> volunteers = volunteerRepository.findNearbyActiveVolunteersByTenant(
                event.getTenantId(),
                event.getLat() - range,
                event.getLat() + range,
                event.getLng() - range,
                event.getLng() + range
        );

        EmergencyAlertMessage alert = new EmergencyAlertMessage(
                event.getId(),
                event.getUserId(),
                event.getUsername(),
                event.getEventType().getDisplayName(),
                event.getDescription(),
                event.getLat(),
                event.getLng(),
                "Nearby emergency needs support"
        );

        for (VolunteerEntity volunteer : volunteers) {
            messagingTemplate.convertAndSendToUser(volunteer.getUserId(), "/queue/emergency/volunteer", alert);
        }
    }

    private void notifyEmergencyContacts(String userId, EmergencyEventEntity event) {
        List<EmergencyContactEntity> contacts = contactRepository.findByUserIdAndTenantIdOrderByIsPrimaryDesc(userId, event.getTenantId());
        for (EmergencyContactEntity contact : contacts) {
            String sms = String.format(Locale.ROOT, "Emergency alert for %s at %.6f, %.6f", event.getUsername(), event.getLat(), event.getLng());
            smsService.send(contact.getPhoneNumber(), sms);
        }
    }

    private EmergencyEventEntity requireTenantEvent(Long eventId) {
        return eventRepository.findById(Objects.requireNonNull(eventId))
                .filter(e -> TenantContext.get().equals(e.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("emergency not found: " + eventId));
    }

    private String normalizeScope(String scopeRaw) {
        String s = scopeRaw == null ? "ALL" : scopeRaw.trim().toUpperCase(Locale.ROOT);
        if (!"SECURITY".equals(s) && !"VOLUNTEER".equals(s) && !"ALL".equals(s)) {
            throw new IllegalArgumentException("invalid broadcast scope");
        }
        return s;
    }

    private String normalizeSeverity(String severityRaw) {
        String s = severityRaw == null ? "NORMAL" : severityRaw.trim().toUpperCase(Locale.ROOT);
        if (!"NORMAL".equals(s) && !"HIGH".equals(s) && !"CRITICAL".equals(s)) {
            return "NORMAL";
        }
        return s;
    }

    private EmergencyEventDto toDto(EmergencyEventEntity entity) {
        EmergencyEventDto dto = new EmergencyEventDto();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUserId());
        dto.setUsername(entity.getUsername());
        dto.setEventType(entity.getEventType());
        dto.setEventTypeDisplay(entity.getEventType() == null ? null : entity.getEventType().getDisplayName());
        dto.setDescription(entity.getDescription());
        dto.setLat(entity.getLat());
        dto.setLng(entity.getLng());
        dto.setAccuracy(entity.getAccuracy());
        dto.setStatus(entity.getStatus());
        dto.setStatusDisplay(entity.getStatus() == null ? null : entity.getStatus().getDisplayName());
        dto.setHandledBy(entity.getHandledBy());
        dto.setHandledAt(entity.getHandledAt());
        dto.setResolutionNote(entity.getResolutionNote());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setSeverity(entity.getSeverity());
        dto.setZoneId(entity.getZoneId());
        dto.setBroadcastRadiusM(entity.getBroadcastRadiusM());
        return dto;
    }

    private EmergencyContactDto toContactDto(EmergencyContactEntity entity) {
        EmergencyContactDto dto = new EmergencyContactDto();
        dto.setId(entity.getId());
        dto.setContactName(entity.getContactName());
        dto.setPhoneNumber(entity.getPhoneNumber());
        dto.setEmail(entity.getEmail());
        dto.setRelationship(entity.getRelationship());
        if (entity.getRelationship() != null) {
            dto.setRelationshipDisplay(entity.getRelationship().getDisplayName());
        }
        dto.setIsPrimary(entity.getIsPrimary());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private EmergencyBroadcastDto toBroadcastDto(EmergencyBroadcastEntity entity) {
        EmergencyBroadcastDto dto = new EmergencyBroadcastDto();
        dto.setId(entity.getId());
        dto.setEventId(entity.getEvent() == null ? null : entity.getEvent().getId());
        dto.setPublisherId(entity.getPublisherId());
        dto.setTargetScope(entity.getTargetScope());
        dto.setSeverity(entity.getSeverity());
        dto.setMessage(entity.getMessage());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    private EmergencyDispatchTaskDto toDispatchTask(EmergencyEventEntity event, VolunteerEntity volunteer) {
        EmergencyDispatchTaskDto dto = new EmergencyDispatchTaskDto();
        dto.setEventId(event.getId());
        dto.setEventStatus(event.getStatus() == null ? null : event.getStatus().name());
        dto.setSeverity(event.getSeverity());
        dto.setVolunteerUserId(volunteer.getUserId());
        dto.setVolunteerName(volunteer.getName());
        dto.setVolunteerLat(volunteer.getLastLocationLat());
        dto.setVolunteerLng(volunteer.getLastLocationLng());
        if (volunteer.getLastLocationLat() != null && volunteer.getLastLocationLng() != null) {
            dto.setDistanceMeters(haversineMeters(event.getLat(), event.getLng(), volunteer.getLastLocationLat(), volunteer.getLastLocationLng()));
        }
        dto.setResponderType(ResponderType.VOLUNTEER);
        return dto;
    }

    private static double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
