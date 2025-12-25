package com.demo.accessiblenav.navigation;

import com.demo.accessiblenav.navigation.dto.LocationUpdateRequest;
import com.demo.accessiblenav.navigation.dto.NavigationInstructionMessage;
import com.demo.accessiblenav.navigation.dto.RouteUpdateMessage;
import com.demo.accessiblenav.route.GraphRoutingService;
import com.demo.accessiblenav.route.NavigationInstructionService;
import com.demo.accessiblenav.route.dto.RouteRequest;
import com.demo.accessiblenav.route.dto.RouteResponse;
import com.demo.accessiblenav.route.dto.TravelMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 实时导航会话管理服务
 */
@Service
public class NavigationSessionService {

    private static final Logger log = LoggerFactory.getLogger(NavigationSessionService.class);

    /**
     * 偏离路线阈值（米）
     */
    private static final double DEVIATION_THRESHOLD = 30.0;

    /**
     * 到达目的地阈值（米）
     */
    private static final double ARRIVAL_THRESHOLD = 10.0;

    /**
     * 活跃的导航会话
     */
    private final Map<String, NavigationSession> activeSessions = new ConcurrentHashMap<>();

    /**
     * 用户ID到会话ID的映射
     */
    private final Map<String, String> userSessionMap = new ConcurrentHashMap<>();

    private final SimpMessagingTemplate messagingTemplate;
    private final GraphRoutingService routingService;
    @SuppressWarnings("unused")
    private final NavigationInstructionService instructionService;

    public NavigationSessionService(
            SimpMessagingTemplate messagingTemplate,
            GraphRoutingService routingService,
            NavigationInstructionService instructionService) {
        this.messagingTemplate = messagingTemplate;
        this.routingService = routingService;
        this.instructionService = instructionService;
    }

    /**
     * 开始新的导航会话
     */
    public NavigationSession startNavigation(
            String oderId,
            double startLat, double startLng,
            double endLat, double endLng,
            String destinationName,
            String mode) {

        // 结束该用户之前的会话
        String existingSessionId = userSessionMap.get(oderId);
        if (existingSessionId != null) {
            endNavigation(existingSessionId);
        }

        // 计算路线
        RouteRequest routeRequest = new RouteRequest();
        routeRequest.setStartLat(startLat);
        routeRequest.setStartLng(startLng);
        routeRequest.setEndLat(endLat);
        routeRequest.setEndLng(endLng);
        routeRequest.setMode(TravelMode.valueOf(mode));

        RouteResponse routeResponse = routingService.route(routeRequest);

        if (routeResponse.getPath() == null || routeResponse.getPath().isEmpty()) {
            throw new IllegalStateException("无法找到可行路线");
        }

        // 创建新会话
        NavigationSession session = new NavigationSession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setUserId(oderId);
        session.setDestinationLat(endLat);
        session.setDestinationLng(endLng);
        session.setDestinationName(destinationName);
        session.setMode(mode);
        session.setRouteCoordinates(convertPathToCoordinates(routeResponse.getPath()));

        activeSessions.put(session.getSessionId(), session);
        userSessionMap.put(oderId, session.getSessionId());

        log.info("导航会话已启动: sessionId={}, userId={}, destination={}",
                session.getSessionId(), oderId, destinationName);

        return session;
    }

    /**
     * 更新用户位置
     */
    public void updateLocation(String sessionId, LocationUpdateRequest location) {
        NavigationSession session = activeSessions.get(sessionId);
        if (session == null || !session.isActive()) {
            return;
        }

        session.setLastUpdateAt(Instant.now());

        // 检查是否到达目的地
        double distanceToDestination = calculateDistance(
                location.getLat(), location.getLng(),
                session.getDestinationLat(), session.getDestinationLng()
        );

        if (distanceToDestination < ARRIVAL_THRESHOLD) {
            handleArrival(session);
            return;
        }

        // 检查是否偏离路线
        double distanceToRoute = calculateDistanceToRoute(
                location.getLat(), location.getLng(),
                session.getRouteCoordinates()
        );

        if (distanceToRoute > DEVIATION_THRESHOLD) {
            handleRouteDeviation(session, location);
        } else {
            // 更新当前位置并发送导航指令
            sendNavigationInstruction(session, location);
        }
    }

    /**
     * 处理到达目的地
     */
    private void handleArrival(NavigationSession session) {
        NavigationInstructionMessage message = new NavigationInstructionMessage();
        message.setAction("ARRIVE");
        message.setInstruction("您已到达目的地：" + session.getDestinationName());
        message.setDistanceRemaining(0);
        message.setEstimatedTimeRemaining(0);

        messagingTemplate.convertAndSendToUser(
                Objects.requireNonNull(session.getUserId()),
                "/queue/instruction",
                message
        );

        log.info("用户到达目的地: sessionId={}, destination={}",
                session.getSessionId(), session.getDestinationName());

        endNavigation(session.getSessionId());
    }

    /**
     * 处理路线偏离
     */
    private void handleRouteDeviation(NavigationSession session, LocationUpdateRequest location) {
        log.info("用户偏离路线，重新规划: sessionId={}", session.getSessionId());

        // 重新计算路线
        RouteRequest routeRequest = new RouteRequest();
        routeRequest.setStartLat(location.getLat());
        routeRequest.setStartLng(location.getLng());
        routeRequest.setEndLat(session.getDestinationLat());
        routeRequest.setEndLng(session.getDestinationLng());
        routeRequest.setMode(TravelMode.valueOf(session.getMode()));

        try {
            RouteResponse newRoute = routingService.route(routeRequest);

            if (newRoute.getPath() != null && !newRoute.getPath().isEmpty()) {
                // 更新会话路线
                session.setRouteCoordinates(convertPathToCoordinates(newRoute.getPath()));
                session.setCurrentSegmentIndex(0);

                // 推送新路线
                RouteUpdateMessage updateMessage = new RouteUpdateMessage(
                        convertPathToCoordinates(newRoute.getPath()),
                        newRoute.getDistanceM(),
                        "您已偏离原路线，已为您重新规划"
                );
                updateMessage.setReason("DEVIATION");

                messagingTemplate.convertAndSendToUser(
                        Objects.requireNonNull(session.getUserId()),
                        "/queue/route-update",
                        updateMessage
                );
            }
        } catch (Exception e) {
            log.error("重新规划路线失败: sessionId={}", session.getSessionId(), e);
        }
    }

    /**
     * 发送导航指令
     */
    private void sendNavigationInstruction(NavigationSession session, LocationUpdateRequest location) {
        List<double[]> coordinates = session.getRouteCoordinates();
        if (coordinates == null || coordinates.isEmpty()) {
            return;
        }

        // 找到最近的路线点
        int nearestIndex = findNearestPointIndex(location.getLat(), location.getLng(), coordinates);
        session.setCurrentSegmentIndex(nearestIndex);

        // 计算剩余距离
        double remainingDistance = calculateRemainingDistance(coordinates, nearestIndex);

        // 确定下一个动作
        String action = determineNextAction(coordinates, nearestIndex);
        double distanceToNext = calculateDistanceToNextTurn(coordinates, nearestIndex);

        // 构建导航指令
        NavigationInstructionMessage instruction = new NavigationInstructionMessage();
        instruction.setAction(action);
        instruction.setDistanceToNext(distanceToNext);
        instruction.setDistanceRemaining(remainingDistance);
        instruction.setEstimatedTimeRemaining((int) (remainingDistance / 1.2));
        instruction.setInstruction(generateInstructionText(action, distanceToNext));
        instruction.setCurrentSegmentIndex(nearestIndex);
        instruction.setTotalSegments(coordinates.size());

        messagingTemplate.convertAndSendToUser(
                Objects.requireNonNull(session.getUserId()),
                "/queue/instruction",
                instruction
        );
    }

    /**
     * 结束导航会话
     */
    public void endNavigation(String sessionId) {
        NavigationSession session = activeSessions.remove(sessionId);
        if (session != null) {
            session.setActive(false);
            userSessionMap.remove(session.getUserId());
            log.info("导航会话已结束: sessionId={}", sessionId);
        }
    }

    /**
     * 获取会话信息
     */
    public NavigationSession getSession(String sessionId) {
        return activeSessions.get(sessionId);
    }

    /**
     * 根据用户ID获取会话
     */
    public NavigationSession getSessionByUserId(String oderId) {
        String sessionId = userSessionMap.get(oderId);
        return sessionId != null ? activeSessions.get(sessionId) : null;
    }

    // ========== 辅助方法 ==========

    /**
     * 计算两点间距离（米）- Haversine公式
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    /**
     * 计算点到路线的最短距离
     */
    private double calculateDistanceToRoute(double lat, double lng, List<double[]> coordinates) {
        if (coordinates == null || coordinates.isEmpty()) {
            return Double.MAX_VALUE;
        }

        double minDistance = Double.MAX_VALUE;
        for (double[] coord : coordinates) {
            double distance = calculateDistance(lat, lng, coord[1], coord[0]);
            minDistance = Math.min(minDistance, distance);
        }
        return minDistance;
    }

    /**
     * 找到最近的路线点索引
     */
    private int findNearestPointIndex(double lat, double lng, List<double[]> coordinates) {
        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;

        for (int i = 0; i < coordinates.size(); i++) {
            double[] coord = coordinates.get(i);
            double distance = calculateDistance(lat, lng, coord[1], coord[0]);
            if (distance < minDistance) {
                minDistance = distance;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    /**
     * 计算剩余距离
     */
    private double calculateRemainingDistance(List<double[]> coordinates, int fromIndex) {
        double total = 0;
        for (int i = fromIndex; i < coordinates.size() - 1; i++) {
            double[] current = coordinates.get(i);
            double[] next = coordinates.get(i + 1);
            total += calculateDistance(current[1], current[0], next[1], next[0]);
        }
        return total;
    }

    /**
     * 确定下一个动作
     */
    private String determineNextAction(List<double[]> coordinates, int currentIndex) {
        if (currentIndex >= coordinates.size() - 2) {
            return "ARRIVE";
        }

        // 计算转弯角度
        if (currentIndex < coordinates.size() - 2) {
            double[] p1 = coordinates.get(currentIndex);
            double[] p2 = coordinates.get(currentIndex + 1);
            double[] p3 = coordinates.get(currentIndex + 2);

            double angle = calculateTurnAngle(p1, p2, p3);

            if (angle > 30) return "RIGHT";
            if (angle < -30) return "LEFT";
        }

        return "STRAIGHT";
    }

    /**
     * 计算转弯角度
     */
    private double calculateTurnAngle(double[] p1, double[] p2, double[] p3) {
        double bearing1 = calculateBearing(p1[1], p1[0], p2[1], p2[0]);
        double bearing2 = calculateBearing(p2[1], p2[0], p3[1], p3[0]);
        double angle = bearing2 - bearing1;

        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;

        return angle;
    }

    /**
     * 计算方位角
     */
    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double x = Math.sin(dLng) * Math.cos(lat2Rad);
        double y = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);

        return Math.toDegrees(Math.atan2(x, y));
    }

    /**
     * 计算到下一个转弯点的距离
     */
    private double calculateDistanceToNextTurn(List<double[]> coordinates, int currentIndex) {
        double distance = 0;
        for (int i = currentIndex; i < coordinates.size() - 1; i++) {
            double[] current = coordinates.get(i);
            double[] next = coordinates.get(i + 1);
            distance += calculateDistance(current[1], current[0], next[1], next[0]);

            // 检查是否有转弯
            if (i < coordinates.size() - 2) {
                double[] afterNext = coordinates.get(i + 2);
                double angle = calculateTurnAngle(current, next, afterNext);
                if (Math.abs(angle) > 30) {
                    break;
                }
            }
        }
        return distance;
    }

    /**
     * 生成指令文本
     */
    private String generateInstructionText(String action, double distance) {
        String distanceText = formatDistance(distance);

        switch (action) {
            case "STRAIGHT":
                return "沿当前方向直行 " + distanceText;
            case "LEFT":
                return "直行 " + distanceText + " 后左转";
            case "RIGHT":
                return "直行 " + distanceText + " 后右转";
            case "ARRIVE":
                return "继续前行 " + distanceText + "，即将到达目的地";
            default:
                return "继续前行 " + distanceText;
        }
    }

    /**
     * 格式化距离
     */
    private String formatDistance(double meters) {
        if (meters < 100) {
            return String.format("%.0f 米", meters);
        } else if (meters < 1000) {
            return String.format("约 %.0f 米", Math.round(meters / 10) * 10);
        } else {
            return String.format("约 %.1f 公里", meters / 1000);
        }
    }

    /**
     * 将RouteResponse.LngLat列表转换为double[]坐标列表
     */
    private List<double[]> convertPathToCoordinates(List<RouteResponse.LngLat> path) {
        if (path == null) {
            return new java.util.ArrayList<>();
        }
        return path.stream()
                .map(p -> new double[]{p.getLng(), p.getLat()})
                .collect(java.util.stream.Collectors.toList());
    }
}
