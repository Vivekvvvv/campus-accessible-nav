package com.demo.accessiblenav.voice;

import com.demo.accessiblenav.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.security.Principal;

/**
 * 语音设置控制器
 */
@RestController
@RequestMapping("/api/v1/voice-settings")
@Tag(name = "语音设置", description = "语音播报设置相关接口")
public class VoiceSettingsController {

    private final VoiceSettingsService service;

    public VoiceSettingsController(VoiceSettingsService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "获取语音设置", description = "获取当前用户的语音播报设置")
    public ResponseEntity<ApiResponse<VoiceSettingsDto>> getSettings(Principal principal) {
        String userId = principal.getName();
        VoiceSettingsDto settings = service.getUserSettings(userId);
        return ResponseEntity.ok(ApiResponse.success(settings));
    }

    @PutMapping
    @Operation(summary = "更新语音设置", description = "更新当前用户的语音播报设置")
    public ResponseEntity<ApiResponse<VoiceSettingsDto>> updateSettings(
            @Valid @RequestBody VoiceSettingsDto request,
            Principal principal) {
        String userId = principal.getName();
        VoiceSettingsDto settings = service.updateSettings(userId, request);
        return ResponseEntity.ok(ApiResponse.success("设置已更新", settings));
    }
}
