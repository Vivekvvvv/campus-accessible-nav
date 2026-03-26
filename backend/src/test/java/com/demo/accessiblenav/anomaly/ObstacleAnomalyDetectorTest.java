package com.demo.accessiblenav.anomaly;

import com.demo.accessiblenav.anomaly.ObstacleAnomalyDetector.AnomalyDetectionResult;
import com.demo.accessiblenav.anomaly.ObstacleAnomalyDetector.ObstacleReport;
import com.demo.accessiblenav.anomaly.ObstacleAnomalyDetector.RecommendedAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ObstacleAnomalyDetector 单元测试 — 验证检测阈值与边界行为。
 */
class ObstacleAnomalyDetectorTest {

    private ObstacleAnomalyDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ObstacleAnomalyDetector();
    }

    // ------------------------------------------------------------------ helpers

    private ObstacleReport buildReport(String userId, double lat, double lng, String description) {
        ObstacleReport report = new ObstacleReport();
        report.setUserId(userId);
        report.setLat(lat);
        report.setLng(lng);
        report.setDescription(description);
        report.setType("STEP");
        return report;
    }

    // ------------------------------------------------------------------ clean report

    @Test
    void cleanReport_shouldNotBeSuspicious() {
        ObstacleReport report = buildReport("user1", 31.23, 121.47, "路面有台阶，不易通过");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.isSuspicious()).isFalse();
        assertThat(result.getSuspicionScore()).isLessThan(50);
        assertThat(result.getRecommendedAction()).isIn(
                RecommendedAction.AUTO_APPROVE, RecommendedAction.FLAG_FOR_REVIEW);
    }

    // ------------------------------------------------------------------ spam content

    @Test
    void spamDescription_shouldRaiseSuspicion() {
        ObstacleReport report = buildReport("user2", 31.23, 121.47, "测试报告 aaa");
        AnomalyDetectionResult result = detector.detect(report);

        // 包含 "测试" 匹配垃圾词规则，suspicionScore += 40
        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(40);
        assertThat(result.getReasons()).anyMatch(r -> r.contains("可疑关键词"));
    }

    @Test
    void adContent_shouldRaiseSuspicion() {
        ObstacleReport report = buildReport("user3", 31.23, 121.47, "广告推广优惠折扣");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(40);
    }

    @Test
    void urlInDescription_shouldRaiseSuspicion() {
        ObstacleReport report = buildReport("user4", 31.23, 121.47, "请访问 http://example.com");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(40);
    }

    // ------------------------------------------------------------------ description length

    @Test
    void tooShortDescription_shouldAddScore() {
        ObstacleReport report = buildReport("user5", 31.23, 121.47, "X");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getReasons()).anyMatch(r -> r.contains("描述过短"));
        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void tooLongDescription_shouldAddScore() {
        String longDesc = "a".repeat(501);
        ObstacleReport report = buildReport("user6", 31.23, 121.47, longDesc);
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getReasons()).anyMatch(r -> r.contains("描述过长"));
        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(15);
    }

    // ------------------------------------------------------------------ invalid coordinates

    @Test
    void invalidLatitude_shouldBeSuspicious() {
        ObstacleReport report = buildReport("user7", 999.0, 121.47, "正常描述内容在这里");
        AnomalyDetectionResult result = detector.detect(report);

        // 无效坐标 +50 分，应超过阈值
        assertThat(result.isSuspicious()).isTrue();
        assertThat(result.getReasons()).anyMatch(r -> r.contains("坐标"));
        assertThat(result.getRecommendedAction()).isIn(
                RecommendedAction.MANUAL_REVIEW, RecommendedAction.REJECT);
    }

    @Test
    void invalidLongitude_shouldBeSuspicious() {
        ObstacleReport report = buildReport("user8", 31.23, 999.0, "正常描述内容");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.isSuspicious()).isTrue();
    }

    @Test
    void boundaryCoordinates_shouldBeValid() {
        // 边界值：正好在合法范围内
        ObstacleReport report = buildReport("user9", 90.0, 180.0, "边界坐标测试点");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getReasons()).noneMatch(r -> r.contains("坐标"));
    }

    // ------------------------------------------------------------------ user credit

    @Test
    void defaultUserCredit_shouldBe100() {
        assertThat(detector.getUserCredit("new-user")).isEqualTo(100);
    }

    @Test
    void updateCredit_positiveDelta_shouldIncrease() {
        detector.updateUserCredit("user10", 20);
        assertThat(detector.getUserCredit("user10")).isEqualTo(120);
    }

    @Test
    void updateCredit_negativeDelta_shouldDecrease() {
        detector.updateUserCredit("user11", -60);
        assertThat(detector.getUserCredit("user11")).isEqualTo(40);
    }

    @Test
    void updateCredit_belowZero_shouldClampToZero() {
        detector.updateUserCredit("user12", -200);
        assertThat(detector.getUserCredit("user12")).isEqualTo(0);
    }

    @Test
    void updateCredit_aboveMax_shouldClampTo200() {
        detector.updateUserCredit("user13", 200);
        assertThat(detector.getUserCredit("user13")).isEqualTo(200);
    }

    @Test
    void lowCreditUser_shouldRaiseSuspicion() {
        detector.updateUserCredit("low-credit", -60); // credit = 40 < 50
        ObstacleReport report = buildReport("low-credit", 31.23, 121.47, "正常描述内容检测");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getReasons()).anyMatch(r -> r.contains("信誉度"));
        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(25);
    }

    // ------------------------------------------------------------------ rate limiting

    @Test
    void highFrequencyReports_shouldRaiseSuspicion() {
        // 同一用户连续上报 5 次后，第 6 次应触发频率检查
        String userId = "spammer";
        for (int i = 0; i < 5; i++) {
            detector.detect(buildReport(userId, 31.23 + i * 0.001, 121.47, "上报" + i + "正常描述"));
        }
        AnomalyDetectionResult result = detector.detect(
                buildReport(userId, 31.30, 121.50, "第六次上报内容"));

        assertThat(result.getSuspicionScore()).isGreaterThanOrEqualTo(30);
        assertThat(result.getReasons()).anyMatch(r -> r.contains("上报") && r.contains("次"));
    }

    // ------------------------------------------------------------------ recommended action

    @Test
    void highSuspicionScore_shouldRejectOrManualReview() {
        // 无效坐标(+50) + 垃圾内容(+40) = 90 分，应 REJECT
        ObstacleReport report = buildReport("bad-user", 999.0, 999.0, "广告推广测试");
        AnomalyDetectionResult result = detector.detect(report);

        assertThat(result.getRecommendedAction()).isIn(
                RecommendedAction.REJECT, RecommendedAction.MANUAL_REVIEW);
        assertThat(result.isSuspicious()).isTrue();
    }
}
