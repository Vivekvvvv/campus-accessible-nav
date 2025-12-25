package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.navigation.dto.LocationUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * 实时导航 WebSocket 控制器
 */
@Controller
public class NavigationWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(NavigationWebSocketController.class);

    private final NavigationSessionService sessionService;

    public NavigationWebSocketController(NavigationSessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * 处理位置更新
     * 客户端发送: /app/location-update
     */
    @MessageMapping("/location-update")
    public void handleLocationUpdate(
            @Payload LocationUpdateRequest location,
            Principal principal) {

        String userId = requireAuthenticatedUser(principal);
        NavigationSession session = sessionService.getSessionByUserId(userId);

        if (session != null) {
            sessionService.updateLocation(session.getSessionId(), location);
        }
    }

    /**
     * 开始导航
     * 客户端发送: /app/start-navigation
     */
    @MessageMapping("/start-navigation")
    @SendToUser("/queue/navigation-started")
    public NavigationStartResponse startNavigation(
            @Payload NavigationStartRequest request,
            Principal principal) {

        String userId = requireAuthenticatedUser(principal);

        try {
            NavigationSession session = sessionService.startNavigation(
                    userId,
                    request.getStartLat(),
                    request.getStartLng(),
                    request.getEndLat(),
                    request.getEndLng(),
                    request.getDestinationName(),
                    request.getMode()
            );

            log.info("导航已启动: userId={}, sessionId={}", userId, session.getSessionId());

            return new NavigationStartResponse(
                    true,
                    session.getSessionId(),
                    "导航已启动",
                    session.getRouteCoordinates()
            );

        } catch (Exception e) {
            log.error("启动导航失败: userId={}", userId, e);
            return new NavigationStartResponse(false, null, "启动导航失败: " + e.getMessage(), null);
        }
    }

    /**
     * 停止导航
     * 客户端发送: /app/stop-navigation
     */
    @MessageMapping("/stop-navigation")
    @SendToUser("/queue/navigation-stopped")
    public NavigationStopResponse stopNavigation(Principal principal) {

        String userId = requireAuthenticatedUser(principal);
        NavigationSession session = sessionService.getSessionByUserId(userId);

        if (session != null) {
            sessionService.endNavigation(session.getSessionId());
            return new NavigationStopResponse(true, "导航已停止");
        }

        return new NavigationStopResponse(false, "没有活跃的导航会话");
    }

    private static String requireAuthenticatedUser(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
            throw new AccessDeniedException("authentication required");
        }
        return principal.getName();
    }

    // ========== 请求/响应类 ==========

    /**
     * 开始导航请求
     */
    public static class NavigationStartRequest {
        private double startLat;
        private double startLng;
        private double endLat;
        private double endLng;
        private String destinationName;
        private String mode = "WALK";

        public double getStartLat() { return startLat; }
        public void setStartLat(double startLat) { this.startLat = startLat; }
        public double getStartLng() { return startLng; }
        public void setStartLng(double startLng) { this.startLng = startLng; }
        public double getEndLat() { return endLat; }
        public void setEndLat(double endLat) { this.endLat = endLat; }
        public double getEndLng() { return endLng; }
        public void setEndLng(double endLng) { this.endLng = endLng; }
        public String getDestinationName() { return destinationName; }
        public void setDestinationName(String destinationName) { this.destinationName = destinationName; }
        public String getMode() { return mode; }
        public void setMode(String mode) { this.mode = mode; }
    }

    /**
     * 开始导航响应
     */
    public static class NavigationStartResponse {
        private boolean success;
        private String sessionId;
        private String message;
        private java.util.List<double[]> routeCoordinates;

        public NavigationStartResponse(boolean success, String sessionId, String message, java.util.List<double[]> routeCoordinates) {
            this.success = success;
            this.sessionId = sessionId;
            this.message = message;
            this.routeCoordinates = routeCoordinates;
        }

        public boolean isSuccess() { return success; }
        public String getSessionId() { return sessionId; }
        public String getMessage() { return message; }
        public java.util.List<double[]> getRouteCoordinates() { return routeCoordinates; }
    }

    /**
     * 停止导航响应
     */
    public static class NavigationStopResponse {
        private boolean success;
        private String message;

        public NavigationStopResponse(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
}
