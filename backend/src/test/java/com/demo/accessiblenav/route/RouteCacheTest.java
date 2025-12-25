package com.demo.accessiblenav.route;

import com.demo.accessiblenav.route.dto.RouteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RouteCache 单元测试（仅测试 L1 Caffeine 缓存）
 */
class RouteCacheTest {

    private RouteCache routeCache;

    @BeforeEach
    void setUp() {
        routeCache = new RouteCache();
        // 设置缓存配置
        Object cache = Objects.requireNonNull(routeCache);
        ReflectionTestUtils.setField(cache, "exactCacheMaxSize", 100);
        ReflectionTestUtils.setField(cache, "exactCacheExpireMinutes", 30);
        ReflectionTestUtils.setField(cache, "gridCacheMaxSize", 200);
        ReflectionTestUtils.setField(cache, "gridCacheExpireMinutes", 120);
        ReflectionTestUtils.setField(cache, "gridSizeMeters", 50);
        ReflectionTestUtils.setField(cache, "redisEnabled", false);
        ReflectionTestUtils.setField(cache, "redisExpireMinutes", 30);
        ReflectionTestUtils.setField(cache, "redisKeyPrefix", "route:");
        // 初始化缓存
        routeCache.init();
    }

    @Test
    @DisplayName("缓存未命中应该返回null")
    void cacheMissShouldReturnNull() {
        // Given
        String key = "23.275_113.202_23.276_113.203_WALK";

        // When
        RouteResponse result = routeCache.get(key);

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("缓存命中应该返回存储的值")
    void cacheHitShouldReturnStoredValue() {
        // Given
        String key = "23.275_113.202_23.276_113.203_WALK";
        RouteResponse response = createTestRouteResponse();
        routeCache.put(key, response);

        // When
        RouteResponse result = routeCache.get(key);

        // Then
        assertNotNull(result);
        assertEquals(response.getDistanceM(), result.getDistanceM());
    }

    @Test
    @DisplayName("清空缓存后应该返回null")
    void clearShouldRemoveAllEntries() {
        // Given
        String key1 = "23.275_113.202_23.276_113.203_WALK";
        String key2 = "23.275_113.202_23.277_113.204_WHEELCHAIR";
        routeCache.put(key1, createTestRouteResponse());
        routeCache.put(key2, createTestRouteResponse());

        // When
        routeCache.clear();

        // Then
        assertNull(routeCache.get(key1));
        assertNull(routeCache.get(key2));
    }

    @Test
    @DisplayName("网格缓存应该能命中相近坐标")
    void gridCacheShouldHitNearbyCoordinates() {
        // Given - 存储一个路由
        String key1 = "23.27500000_113.20200000_23.27600000_113.20300000_WALK";
        RouteResponse response = createTestRouteResponse();
        routeCache.put(key1, response);

        // When - 使用略微不同但在同一网格内的坐标查询
        // 注意：50米网格大约是 0.00045 度（50/111320）
        String key2 = "23.27500010_113.20200010_23.27600010_113.20300010_WALK";
        RouteResponse result = routeCache.get(key2);

        // Then - 应该命中网格缓存
        assertNotNull(result, "相近坐标应该命中网格缓存");
        assertEquals(response.getDistanceM(), result.getDistanceM());
    }

    @Test
    @DisplayName("不同模式应该分别缓存")
    void differentModesShouldBeCachedSeparately() {
        // Given
        String keyWalk = "23.275_113.202_23.276_113.203_WALK";
        String keyWheel = "23.275_113.202_23.276_113.203_WHEELCHAIR";

        RouteResponse walkResponse = createTestRouteResponse();
        walkResponse.setDistanceM(100);

        RouteResponse wheelResponse = createTestRouteResponse();
        wheelResponse.setDistanceM(200);

        routeCache.put(keyWalk, walkResponse);
        routeCache.put(keyWheel, wheelResponse);

        // When
        RouteResponse walkResult = routeCache.get(keyWalk);
        RouteResponse wheelResult = routeCache.get(keyWheel);

        // Then
        assertNotNull(walkResult);
        assertNotNull(wheelResult);
        assertEquals(100, walkResult.getDistanceM());
        assertEquals(200, wheelResult.getDistanceM());
    }

    @Test
    @DisplayName("统计信息应该正确更新")
    void statisticsShouldBeUpdatedCorrectly() {
        // Given
        String key = "23.275_113.202_23.276_113.203_WALK";
        routeCache.put(key, createTestRouteResponse());

        // When - 触发一些缓存操作
        routeCache.get(key);  // 命中
        routeCache.get(key);  // 命中
        routeCache.get("nonexistent_key");  // 未命中

        // Then
        RouteCache.CacheStatistics stats = routeCache.getStatistics();
        assertEquals(3, stats.totalRequests);
        assertTrue(stats.l1ExactHits >= 2, "应该有至少2次L1精确命中");
    }

    @Test
    @DisplayName("缓存大小信息应该正确")
    void cacheSizeInfoShouldBeCorrect() {
        // Given
        routeCache.put("key1_1_1_1_WALK", createTestRouteResponse());
        routeCache.put("key2_2_2_2_WALK", createTestRouteResponse());

        // When
        RouteCache.CacheSizeInfo sizeInfo = routeCache.getSizeInfo();

        // Then
        assertTrue(sizeInfo.exactSize >= 2);
        assertEquals(100, sizeInfo.exactMaxSize);
        assertEquals(200, sizeInfo.gridMaxSize);
        assertFalse(sizeInfo.redisEnabled);
    }

    @Test
    @DisplayName("无效key格式应该正常处理")
    void invalidKeyFormatShouldBeHandled() {
        // Given
        String invalidKey = "invalid_key";
        RouteResponse response = createTestRouteResponse();
        routeCache.put(invalidKey, response);

        // When
        RouteResponse result = routeCache.get(invalidKey);

        // Then - 精确缓存应该工作，但网格缓存可能不工作
        assertNotNull(result);
    }

    @Test
    @DisplayName("命中率计算应该正确")
    void hitRatioCalculationShouldBeCorrect() {
        // Given - 先清空
        routeCache.clear();
        String key = "23.275_113.202_23.276_113.203_WALK";
        routeCache.put(key, createTestRouteResponse());

        // When
        routeCache.get(key);  // 命中
        routeCache.get(key);  // 命中
        routeCache.get("miss1");  // 未命中
        routeCache.get("miss2");  // 未命中

        // Then
        double hitRatio = routeCache.getHitRatio();
        assertEquals(0.5, hitRatio, 0.01, "命中率应该是50%");
    }

    private RouteResponse createTestRouteResponse() {
        RouteResponse response = new RouteResponse();
        response.setDistanceM(500);
        response.setDurationSec(120);
        response.setPath(Collections.emptyList());
        response.setMode("WALK");
        return response;
    }
}
