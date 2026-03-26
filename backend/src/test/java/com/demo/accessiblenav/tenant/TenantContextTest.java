package com.demo.accessiblenav.tenant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantContext 单元测试 — 验证 ThreadLocal 隔离与 TenantFilter 注入行为。
 */
class TenantContextTest {

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ TenantContext

    @Test
    void get_whenNotSet_shouldReturnDefault() {
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void set_shouldReturnSetValue() {
        TenantContext.set("campus-a");
        assertThat(TenantContext.get()).isEqualTo("campus-a");
    }

    @Test
    void clear_shouldRevertToDefault() {
        TenantContext.set("campus-b");
        TenantContext.clear();
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void set_overwrite_shouldReturnNewValue() {
        TenantContext.set("tenant-1");
        TenantContext.set("tenant-2");
        assertThat(TenantContext.get()).isEqualTo("tenant-2");
    }
}
