package com.demo.accessiblenav.route;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 导航指令生成服务
 * 将路线坐标转换为语音导航指令
 */
@Service
public class NavigationInstructionService {

    private static final double MIN_SEGMENT_DISTANCE = 5.0;  // 最小合并段距离（米）
    private static final double TURN_THRESHOLD = 30.0;       // 转弯角度阈值（度）

    /**
     * 从路线坐标生成导航指令
     *
     * @param coordinates 路线坐标点列表 [[lng, lat], ...]
     * @param levels      楼层列表（可选）
     * @return 导航指令列表
     */
    public List<NavigationInstruction> generateInstructions(
            List<double[]> coordinates,
            List<Integer> levels) {

        List<NavigationInstruction> instructions = new ArrayList<>();

        if (coordinates == null || coordinates.size() < 2) {
            return instructions;
        }

        // 计算每个点的方向角
        List<Double> bearings = calculateBearings(coordinates);

        int stepNumber = 1;
        double accumulatedDistance = 0;
        double segmentBearing = bearings.isEmpty() ? 0 : bearings.get(0);

        for (int i = 0; i < coordinates.size() - 1; i++) {
            double distance = calculateDistance(
                    coordinates.get(i)[1], coordinates.get(i)[0],
                    coordinates.get(i + 1)[1], coordinates.get(i + 1)[0]);
            accumulatedDistance += distance;

            // 检查是否需要生成新指令（转弯或楼层变化）
            boolean shouldCreateInstruction = false;
            String action = "STRAIGHT";
            String accessibilityNote = null;

            // 检查方向变化
            if (i < bearings.size() - 1) {
                double angleDiff = normalizeAngle(bearings.get(i + 1) - segmentBearing);
                if (Math.abs(angleDiff) > TURN_THRESHOLD) {
                    shouldCreateInstruction = true;
                    if (angleDiff > 0) {
                        action = angleDiff > 90 ? "SHARP_RIGHT" : "RIGHT";
                    } else {
                        action = angleDiff < -90 ? "SHARP_LEFT" : "LEFT";
                    }
                }
            }

            // 检查楼层变化
            if (levels != null && levels.size() > i + 1) {
                Integer currentLevel = levels.get(i);
                Integer nextLevel = levels.get(i + 1);
                if (currentLevel != null && nextLevel != null && !currentLevel.equals(nextLevel)) {
                    shouldCreateInstruction = true;
                    if (nextLevel > currentLevel) {
                        action = "GO_UP";
                        accessibilityNote = String.format("乘坐电梯或楼梯上到 %d 层", nextLevel);
                    } else {
                        action = "GO_DOWN";
                        accessibilityNote = String.format("乘坐电梯或楼梯下到 %d 层", nextLevel);
                    }
                }
            }

            // 最后一个点
            if (i == coordinates.size() - 2) {
                shouldCreateInstruction = true;
                action = "ARRIVE";
            }

            if (shouldCreateInstruction && accumulatedDistance >= MIN_SEGMENT_DISTANCE) {
                NavigationInstruction instruction = new NavigationInstruction();
                instruction.setStepNumber(stepNumber++);
                instruction.setAction(action);
                instruction.setDistanceMeters(accumulatedDistance);
                instruction.setDistanceText(formatDistance(accumulatedDistance));
                instruction.setBearing(segmentBearing);
                instruction.setLat(coordinates.get(i + 1)[1]);
                instruction.setLng(coordinates.get(i + 1)[0]);

                if (levels != null && levels.size() > i + 1) {
                    instruction.setLevel(levels.get(i + 1));
                }

                instruction.setAccessibilityNote(accessibilityNote);
                instruction.setDescription(generateDescription(action, accumulatedDistance, accessibilityNote));

                instructions.add(instruction);

                // 重置累计
                accumulatedDistance = 0;
                if (i < bearings.size() - 1) {
                    segmentBearing = bearings.get(i + 1);
                }
            }
        }

        return instructions;
    }

    /**
     * 生成语音播报文本
     */
    private String generateDescription(String action, double distance, String accessibilityNote) {
        String distanceText = formatDistance(distance);

        String description;
        switch (action) {
            case "STRAIGHT":
                description = String.format("沿当前方向直行 %s", distanceText);
                break;
            case "LEFT":
                description = String.format("直行 %s 后左转", distanceText);
                break;
            case "RIGHT":
                description = String.format("直行 %s 后右转", distanceText);
                break;
            case "SHARP_LEFT":
                description = String.format("直行 %s 后向左大转弯", distanceText);
                break;
            case "SHARP_RIGHT":
                description = String.format("直行 %s 后向右大转弯", distanceText);
                break;
            case "GO_UP":
                description = String.format("直行 %s 后上楼", distanceText);
                break;
            case "GO_DOWN":
                description = String.format("直行 %s 后下楼", distanceText);
                break;
            case "ARRIVE":
                description = String.format("继续前行 %s，到达目的地", distanceText);
                break;
            default:
                description = String.format("继续前行 %s", distanceText);
        }

        if (accessibilityNote != null && !accessibilityNote.isEmpty()) {
            description += "。" + accessibilityNote;
        }

        return description;
    }

    /**
     * 格式化距离文本
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
     * 计算每段路线的方向角
     */
    private List<Double> calculateBearings(List<double[]> coordinates) {
        List<Double> bearings = new ArrayList<>();
        for (int i = 0; i < coordinates.size() - 1; i++) {
            double bearing = calculateBearing(
                    coordinates.get(i)[1], coordinates.get(i)[0],
                    coordinates.get(i + 1)[1], coordinates.get(i + 1)[0]);
            bearings.add(bearing);
        }
        return bearings;
    }

    /**
     * 计算两点之间的方向角（度）
     */
    private double calculateBearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);

        double x = Math.sin(dLng) * Math.cos(lat2Rad);
        double y = Math.cos(lat1Rad) * Math.sin(lat2Rad)
                - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(x, y));
        return (bearing + 360) % 360;
    }

    /**
     * 使用 Haversine 公式计算两点间的距离（米）
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
     * 将角度标准化到 -180 到 180 之间
     */
    private double normalizeAngle(double angle) {
        while (angle > 180) angle -= 360;
        while (angle < -180) angle += 360;
        return angle;
    }
}
