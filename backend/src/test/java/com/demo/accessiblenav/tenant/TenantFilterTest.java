package com.demo.accessiblenav.tenant;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantFilter 单元测试 — 验证 X-Tenant-ID header 解析与 TenantContext 注入。
 */
class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    // ------------------------------------------------------------------ header present

    @Test
    void validTenantHeader_shouldSetTenantContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "campus-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // 在 filter chain 内捕获 TenantContext 的值
        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain(null, (req, res) -> {
            captured[0] = TenantContext.get();
        });

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo("campus-a");
    }

    @Test
    void missingHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain(null, (req, res) -> {
            captured[0] = TenantContext.get();
        });

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void blankHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain(null, (req, res) -> {
            captured[0] = TenantContext.get();
        });

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void invalidCharactersInHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        // 包含非法字符（空格和点）
        request.addHeader(TenantFilter.TENANT_HEADER, "bad tenant!");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain(null, (req, res) -> {
            captured[0] = TenantContext.get();
        });

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void validHeader_contextShouldBeClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "campus-b");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        // filter finally block should have cleared context
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void headerTooLong_shouldFallbackToDefault() throws Exception {
        // regex 限制 1~32 个字符，超过 32 个字符应回退
        String longTenantId = "a".repeat(33);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, longTenantId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        MockFilterChain chain = new MockFilterChain(null, (req, res) -> {
            captured[0] = TenantContext.get();
        });

        filter.doFilter(request, response, chain);

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }
}
