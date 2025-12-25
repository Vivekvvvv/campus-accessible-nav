package com.demo.accessiblenav.emergency;

import com.demo.accessiblenav.common.ApiResponse;
import com.demo.accessiblenav.emergency.dto.AddContactRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastDto;
import com.demo.accessiblenav.emergency.dto.EmergencyBroadcastRequest;
import com.demo.accessiblenav.emergency.dto.EmergencyContactDto;
import com.demo.accessiblenav.emergency.dto.EmergencyDispatchTaskDto;
import com.demo.accessiblenav.emergency.dto.EmergencyEventDto;
import com.demo.accessiblenav.emergency.dto.TriggerEmergencyRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/emergency")
@Tag(name = "Emergency", description = "Emergency linkage and broadcast APIs")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = emergencyService;
    }

    @PostMapping("/trigger")
    @Operation(summary = "Trigger emergency")
    public ResponseEntity<ApiResponse<EmergencyEventDto>> triggerEmergency(
            @Valid @RequestBody TriggerEmergencyRequest request,
            Principal principal) {
        EmergencyEventDto result = emergencyService.triggerEmergency(principal.getName(), principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("emergency triggered", result));
    }

    @PostMapping("/{eventId}/respond")
    @Operation(summary = "Respond emergency")
    public ResponseEntity<ApiResponse<Void>> respondToEmergency(
            @PathVariable Long eventId,
            @RequestParam ResponderType responderType,
            Principal principal) {
        emergencyService.respondToEmergency(eventId, principal.getName(), principal.getName(), responderType);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{eventId}/resolve")
    @Operation(summary = "Resolve emergency")
    public ResponseEntity<ApiResponse<Void>> resolveEmergency(
            @PathVariable Long eventId,
            @RequestParam(required = false) String resolutionNote,
            Principal principal) {
        emergencyService.resolveEmergency(eventId, principal.getName(), resolutionNote);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/{eventId}/cancel")
    @Operation(summary = "Cancel emergency")
    public ResponseEntity<ApiResponse<Void>> cancelEmergency(
            @PathVariable Long eventId,
            Principal principal) {
        emergencyService.cancelEmergency(eventId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @GetMapping("/active")
    @Operation(summary = "List active emergencies")
    public ResponseEntity<ApiResponse<List<EmergencyEventDto>>> getActiveEmergencies() {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.getActiveEmergencies()));
    }

    @GetMapping("/history")
    @Operation(summary = "List my emergency history")
    public ResponseEntity<ApiResponse<List<EmergencyEventDto>>> getUserHistory(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.getUserEmergencyHistory(principal.getName())));
    }

    @GetMapping("/{eventId}")
    @Operation(summary = "Get emergency by id")
    public ResponseEntity<ApiResponse<EmergencyEventDto>> getEmergencyById(@PathVariable Long eventId) {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.getEmergencyById(eventId)));
    }

    @PostMapping("/contacts")
    @Operation(summary = "Add emergency contact")
    public ResponseEntity<ApiResponse<EmergencyContactDto>> addContact(
            @Valid @RequestBody AddContactRequest request,
            Principal principal) {
        EmergencyContactDto contact = emergencyService.addContact(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("contact added", contact));
    }

    @GetMapping("/contacts")
    @Operation(summary = "List emergency contacts")
    public ResponseEntity<ApiResponse<List<EmergencyContactDto>>> getContacts(Principal principal) {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.getUserContacts(principal.getName())));
    }

    @DeleteMapping("/contacts/{contactId}")
    @Operation(summary = "Delete emergency contact")
    public ResponseEntity<ApiResponse<Void>> deleteContact(
            @PathVariable Long contactId,
            Principal principal) {
        emergencyService.deleteContact(contactId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/volunteer/register")
    @Operation(summary = "Register volunteer")
    public ResponseEntity<ApiResponse<Void>> registerAsVolunteer(
            @RequestParam String name,
            @RequestParam(required = false) String phoneNumber,
            Principal principal) {
        emergencyService.registerAsVolunteer(principal.getName(), name, phoneNumber);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/volunteer/location")
    @Operation(summary = "Update volunteer location")
    public ResponseEntity<ApiResponse<Void>> updateVolunteerLocation(
            @RequestParam Double lat,
            @RequestParam Double lng,
            Principal principal) {
        emergencyService.updateVolunteerLocation(principal.getName(), lat, lng);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/volunteer/status")
    @Operation(summary = "Set volunteer status")
    public ResponseEntity<ApiResponse<Void>> setVolunteerStatus(
            @RequestParam boolean isActive,
            Principal principal) {
        emergencyService.setVolunteerActiveStatus(principal.getName(), isActive);
        return ResponseEntity.ok(ApiResponse.success());
    }

    @PostMapping("/broadcast")
    @Operation(summary = "Publish emergency broadcast")
    public ResponseEntity<ApiResponse<EmergencyBroadcastDto>> publishBroadcast(
            @Valid @RequestBody EmergencyBroadcastRequest request,
            Principal principal) {
        EmergencyBroadcastDto broadcast = emergencyService.publishBroadcast(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("broadcast published", broadcast));
    }

    @GetMapping("/broadcast/history")
    @Operation(summary = "List emergency broadcast history")
    public ResponseEntity<ApiResponse<List<EmergencyBroadcastDto>>> latestBroadcasts() {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.latestBroadcasts()));
    }

    @GetMapping("/{eventId}/dispatch/suggest")
    @Operation(summary = "Suggest volunteer dispatch")
    public ResponseEntity<ApiResponse<List<EmergencyDispatchTaskDto>>> suggestDispatch(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(ApiResponse.success(emergencyService.suggestVolunteerDispatch(eventId, limit)));
    }
}
