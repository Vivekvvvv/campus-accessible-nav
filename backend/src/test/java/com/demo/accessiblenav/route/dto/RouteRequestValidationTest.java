package com.demo.accessiblenav.route.dto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteRequest 验证测试
 */
class RouteRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("有效请求应该通过验证")
    void validRequestShouldPass() {
        // Given
        RouteRequest request = createValidRequest();

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("起点纬度为空应该失败")
    void nullStartLatShouldFail() {
        // Given
        RouteRequest request = createValidRequest();
        request.setStartLat(null);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("起点纬度"));
    }

    @ParameterizedTest
    @CsvSource({
            "-91.0, 纬度值无效",
            "91.0, 纬度值无效",
            "-100.0, 纬度值无效",
            "100.0, 纬度值无效"
    })
    @DisplayName("无效纬度应该失败")
    void invalidLatitudeShouldFail(double lat, String expectedMessage) {
        // Given
        RouteRequest request = createValidRequest();
        request.setStartLat(lat);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains(expectedMessage)));
    }

    @ParameterizedTest
    @CsvSource({
            "-181.0, 经度值无效",
            "181.0, 经度值无效",
            "-200.0, 经度值无效",
            "200.0, 经度值无效"
    })
    @DisplayName("无效经度应该失败")
    void invalidLongitudeShouldFail(double lng, String expectedMessage) {
        // Given
        RouteRequest request = createValidRequest();
        request.setStartLng(lng);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains(expectedMessage)));
    }

    @Test
    @DisplayName("出行模式为空应该失败")
    void nullModeShouldFail() {
        // Given
        RouteRequest request = createValidRequest();
        request.setMode(null);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertEquals(1, violations.size());
        assertTrue(violations.iterator().next().getMessage().contains("出行模式"));
    }

    @ParameterizedTest
    @CsvSource({
            "-0.1, 坡度权重最小为 0",
            "1.1, 坡度权重最大为 1",
            "-1.0, 坡度权重最小为 0",
            "2.0, 坡度权重最大为 1"
    })
    @DisplayName("无效坡度权重应该失败")
    void invalidSlopeWeightShouldFail(double weight, String expectedMessage) {
        // Given
        RouteRequest request = createValidRequest();
        request.setSlopeWeight(weight);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains(expectedMessage)));
    }

    @Test
    @DisplayName("边界值纬度应该通过验证")
    void boundaryLatitudeShouldPass() {
        // Given
        RouteRequest request1 = createValidRequest();
        request1.setStartLat(-90.0);
        request1.setEndLat(90.0);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request1);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("边界值经度应该通过验证")
    void boundaryLongitudeShouldPass() {
        // Given
        RouteRequest request = createValidRequest();
        request.setStartLng(-180.0);
        request.setEndLng(180.0);

        // When
        Set<ConstraintViolation<RouteRequest>> violations = validator.validate(request);

        // Then
        assertTrue(violations.isEmpty());
    }

    @Test
    @DisplayName("边界值坡度权重应该通过验证")
    void boundarySlopeWeightShouldPass() {
        // Given
        RouteRequest request1 = createValidRequest();
        request1.setSlopeWeight(0.0);

        RouteRequest request2 = createValidRequest();
        request2.setSlopeWeight(1.0);

        // When
        Set<ConstraintViolation<RouteRequest>> violations1 = validator.validate(request1);
        Set<ConstraintViolation<RouteRequest>> violations2 = validator.validate(request2);

        // Then
        assertTrue(violations1.isEmpty());
        assertTrue(violations2.isEmpty());
    }

    private RouteRequest createValidRequest() {
        RouteRequest request = new RouteRequest();
        request.setStartLat(30.5);
        request.setStartLng(114.3);
        request.setEndLat(30.51);
        request.setEndLng(114.31);
        request.setMode(TravelMode.WALK);
        return request;
    }
}
