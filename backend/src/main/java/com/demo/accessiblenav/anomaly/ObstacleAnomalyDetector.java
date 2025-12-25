package com.demo.accessiblenav.anomaly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 障碍上报异常检测服务
 * 用于检测和过滤可疑的障碍上报
 */
@Service
public class ObstacleAnomalyDetector {

    private static final Logger log = LoggerFactory.getLogger(ObstacleAnomalyDetector.class);

    // 用户上报历史记录（userId -> List<ReportRecord>）
    private final Map<String, List<ReportRecord>> userReportHistory = new ConcurrentHashMap<>();

    // 位置上报历史（locationKey -> List<ReportRecord>）
    private final Map<String, List<ReportRecord>> locationReportHistory = new ConcurrentHashMap<>();

    // 用户信誉分数（userId -> score）
    private final Map<String, Integer> userCreditScores = new ConcurrentHashMap<>();

    // 可疑关键词列表
    private static final List<Pattern> SPAM_PATTERNS = Arrays.asList(
            Pattern.compile("(?i)(测试|test|aaa|123|xxx)"),
            Pattern.compile("(?i)(广告|推广|优惠|折扣)"),
            Pattern.compile("(?i)(http|www|.com|.cn)"),
            Pattern.compile("(?i)(微信|qq|电话|联系)")
    );

    // 配置参数
    private static final int MAX_REPORTS_PER_HOUR = 5;           // 每小时最大上报次数
    private static final int MAX_REPORTS_SAME_LOCATION = 3;      // 同一位置最大上报次数
    private static final int LOW_CREDIT_THRESHOLD = 50;          // 低信誉阈值
    private static final int DEFAULT_CREDIT_SCORE = 100;         // 默认信誉分数

    /**
     * 检测上报是否可疑
     *
     * @param report 上报信息
     * @return 检测结果
     */
    public AnomalyDetectionResult detect(ObstacleReport report) {
        int suspicionScore = 0;
        List<String> reasons = new ArrayList<>();

        // 1. 检查用户短时间内大量上报
        int recentReports = countUserRecentReports(report.getUserId(), 1);
        if (recentReports >= MAX_REPORTS_PER_HOUR) {
            suspicionScore += 30;
            reasons.add(String.format("用户1小时内上报%d次，超过限制%d次",
                    recentReports, MAX_REPORTS_PER_HOUR));
        }

        // 2. 检查同一位置重复上报
        int locationReports = countLocationReports(report.getLat(), report.getLng());
        if (locationReports >= MAX_REPORTS_SAME_LOCATION) {
            suspicionScore += 20;
            reasons.add(String.format("同一位置已有%d次上报", locationReports));
        }

        // 3. 检查用户信誉度
        int userCredit = getUserCredit(report.getUserId());
        if (userCredit < LOW_CREDIT_THRESHOLD) {
            suspicionScore += 25;
            reasons.add(String.format("用户信誉度较低: %d", userCredit));
        }

        // 4. 检查上报内容
        if (containsSpamContent(report.getDescription())) {
            suspicionScore += 40;
            reasons.add("上报内容包含可疑关键词");
        }

        // 5. 检查描述长度
        if (report.getDescription() != null) {
            if (report.getDescription().length() < 2) {
                suspicionScore += 10;
                reasons.add("描述过短");
            } else if (report.getDescription().length() > 500) {
                suspicionScore += 15;
                reasons.add("描述过长");
            }
        }

        // 6. 检查上报时间（深夜上报）
        int hour = Instant.now().atZone(java.time.ZoneId.systemDefault()).getHour();
        if (hour >= 0 && hour <= 5) {
            suspicionScore += 10;
            reasons.add("深夜时段上报");
        }

        // 7. 检查坐标有效性
        if (!isValidCoordinate(report.getLat(), report.getLng())) {
            suspicionScore += 50;
            reasons.add("坐标无效或超出范围");
        }

        // 8. 检查是否是新用户大量上报
        if (isNewUserWithManyReports(report.getUserId())) {
            suspicionScore += 20;
            reasons.add("新用户短期内大量上报");
        }

        // 记录本次上报
        recordReport(report);

        // 构建结果
        AnomalyDetectionResult result = new AnomalyDetectionResult();
        result.setSuspicious(suspicionScore >= 50);
        result.setSuspicionScore(suspicionScore);
        result.setReasons(reasons);
        result.setRecommendedAction(determineAction(suspicionScore));

        if (result.isSuspicious()) {
            log.warn("检测到可疑上报: userId={}, score={}, reasons={}",
                    report.getUserId(), suspicionScore, reasons);
        }

        return result;
    }

    /**
     * 统计用户最近N小时的上报次数
     */
    private int countUserRecentReports(String userId, int hours) {
        List<ReportRecord> records = userReportHistory.get(userId);
        if (records == null) return 0;

        Instant cutoff = Instant.now().minus(hours, ChronoUnit.HOURS);
        return (int) records.stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff))
                .count();
    }

    /**
     * 统计同一位置的上报次数
     */
    private int countLocationReports(double lat, double lng) {
        String locationKey = getLocationKey(lat, lng);
        List<ReportRecord> records = locationReportHistory.get(locationKey);
        if (records == null) return 0;

        // 只统计最近24小时内的
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        return (int) records.stream()
                .filter(r -> r.getTimestamp().isAfter(cutoff))
                .count();
    }

    /**
     * 获取用户信誉分数
     */
    public int getUserCredit(String userId) {
        return userCreditScores.getOrDefault(userId, DEFAULT_CREDIT_SCORE);
    }

    /**
     * 更新用户信誉分数
     */
    public void updateUserCredit(String userId, int delta) {
        userCreditScores.compute(userId, (k, v) -> {
            int current = v != null ? v : DEFAULT_CREDIT_SCORE;
            int newScore = Math.max(0, Math.min(200, current + delta));
            return newScore;
        });
    }

    /**
     * 检查内容是否包含垃圾信息
     */
    private boolean containsSpamContent(String content) {
        if (content == null || content.isEmpty()) return false;

        for (Pattern pattern : SPAM_PATTERNS) {
            if (pattern.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检查坐标是否有效
     */
    private boolean isValidCoordinate(double lat, double lng) {
        // 基本范围检查
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            return false;
        }

        // 可以添加更严格的校园边界检查
        // 这里假设校园范围大致在某个区域内
        return true;
    }

    /**
     * 检查是否是新用户大量上报
     */
    private boolean isNewUserWithManyReports(String userId) {
        List<ReportRecord> records = userReportHistory.get(userId);
        if (records == null || records.size() < 3) return false;

        // 检查第一次上报是否在24小时内
        Instant firstReport = records.stream()
                .map(ReportRecord::getTimestamp)
                .min(Comparator.naturalOrder())
                .orElse(Instant.now());

        boolean isNew = firstReport.isAfter(Instant.now().minus(24, ChronoUnit.HOURS));
        return isNew && records.size() >= 3;
    }

    /**
     * 记录上报
     */
    private void recordReport(ObstacleReport report) {
        ReportRecord record = new ReportRecord(
                report.getUserId(),
                report.getLat(),
                report.getLng(),
                Instant.now()
        );

        // 记录用户历史
        userReportHistory.computeIfAbsent(report.getUserId(), k -> new ArrayList<>())
                .add(record);

        // 记录位置历史
        String locationKey = getLocationKey(report.getLat(), report.getLng());
        locationReportHistory.computeIfAbsent(locationKey, k -> new ArrayList<>())
                .add(record);

        // 清理过期记录（保留7天）
        cleanupOldRecords();
    }

    /**
     * 生成位置键（网格化）
     */
    private String getLocationKey(double lat, double lng) {
        // 约10米精度的网格
        int latGrid = (int) (lat * 10000);
        int lngGrid = (int) (lng * 10000);
        return latGrid + "_" + lngGrid;
    }

    /**
     * 确定推荐操作
     */
    private RecommendedAction determineAction(int suspicionScore) {
        if (suspicionScore >= 80) {
            return RecommendedAction.REJECT;
        } else if (suspicionScore >= 50) {
            return RecommendedAction.MANUAL_REVIEW;
        } else if (suspicionScore >= 30) {
            return RecommendedAction.FLAG_FOR_REVIEW;
        } else {
            return RecommendedAction.AUTO_APPROVE;
        }
    }

    /**
     * 清理过期记录
     */
    private void cleanupOldRecords() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);

        userReportHistory.values().forEach(list ->
                list.removeIf(r -> r.getTimestamp().isBefore(cutoff)));

        locationReportHistory.values().forEach(list ->
                list.removeIf(r -> r.getTimestamp().isBefore(cutoff)));

        // 移除空列表
        userReportHistory.entrySet().removeIf(e -> e.getValue().isEmpty());
        locationReportHistory.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /**
     * 确认上报有效（增加用户信誉）
     */
    public void confirmValidReport(String userId) {
        updateUserCredit(userId, 5);
        log.debug("用户 {} 上报被确认有效，信誉+5", userId);
    }

    /**
     * 标记上报无效（降低用户信誉）
     */
    public void markInvalidReport(String userId) {
        updateUserCredit(userId, -10);
        log.debug("用户 {} 上报被标记无效，信誉-10", userId);
    }

    /**
     * 标记上报为恶意（大幅降低信誉）
     */
    public void markMaliciousReport(String userId) {
        updateUserCredit(userId, -30);
        log.warn("用户 {} 上报被标记为恶意，信誉-30", userId);
    }

    // ========== 内部类 ==========

    /**
     * 上报记录
     */
    @SuppressWarnings("unused")
    private static class ReportRecord {
        private final String userId;
        private final double lat;
        private final double lng;
        private final Instant timestamp;

        public ReportRecord(String userId, double lat, double lng, Instant timestamp) {
            this.userId = userId;
            this.lat = lat;
            this.lng = lng;
            this.timestamp = timestamp;
        }

        public String getUserId() { return userId; }
        public double getLat() { return lat; }
        public double getLng() { return lng; }
        public Instant getTimestamp() { return timestamp; }
    }

    /**
     * 障碍上报输入
     */
    public static class ObstacleReport {
        private String userId;
        private double lat;
        private double lng;
        private String description;
        private String type;

        public String getUserId() { return userId; }
        public void setUserId(String userId) { this.userId = userId; }
        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }

    /**
     * 检测结果
     */
    public static class AnomalyDetectionResult {
        private boolean suspicious;
        private int suspicionScore;
        private List<String> reasons;
        private RecommendedAction recommendedAction;

        public boolean isSuspicious() { return suspicious; }
        public void setSuspicious(boolean suspicious) { this.suspicious = suspicious; }
        public int getSuspicionScore() { return suspicionScore; }
        public void setSuspicionScore(int suspicionScore) { this.suspicionScore = suspicionScore; }
        public List<String> getReasons() { return reasons; }
        public void setReasons(List<String> reasons) { this.reasons = reasons; }
        public RecommendedAction getRecommendedAction() { return recommendedAction; }
        public void setRecommendedAction(RecommendedAction recommendedAction) { this.recommendedAction = recommendedAction; }
    }

    /**
     * 推荐操作
     */
    public enum RecommendedAction {
        AUTO_APPROVE("自动通过"),
        FLAG_FOR_REVIEW("标记待审"),
        MANUAL_REVIEW("人工审核"),
        REJECT("拒绝");

        private final String displayName;

        RecommendedAction(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
