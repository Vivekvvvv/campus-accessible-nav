package com.demo.accessiblenav.tenant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TenantFilter 单元测试 — 验证 X-Tenant-ID header 解析与 TenantContext 注入。
 */
class TenantFilterTest {

    private final TenantFilter filter = new TenantFilter();

    // ------------------------------------------------------------------ helpers

    /** 简单的捕获链：在 doFilter 内执行 runnable，然后传播调用。 */
    private static FilterChain capturingChain(Runnable capture) {
        return new FilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res)
                    throws IOException, ServletException {
                capture.run();
            }
        };
    }

    /** 空链：什么都不做，只用来验证 filter 是否调用了 chain.doFilter。 */
    private static FilterChain emptyChain() {
        return (req, res) -> {};
    }

    // ------------------------------------------------------------------ header present

    @Test
    void validTenantHeader_shouldSetTenantContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "campus-a");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        filter.doFilter(request, response, capturingChain(() -> captured[0] = TenantContext.get()));

        assertThat(captured[0]).isEqualTo("campus-a");
    }

    @Test
    void missingHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        filter.doFilter(request, response, capturingChain(() -> captured[0] = TenantContext.get()));

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void blankHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        filter.doFilter(request, response, capturingChain(() -> captured[0] = TenantContext.get()));

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void invalidCharsInHeader_shouldFallbackToDefault() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "bad tenant!@#");
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        filter.doFilter(request, response, capturingChain(() -> captured[0] = TenantContext.get()));

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void contextShouldBeClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, "campus-b");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, emptyChain());

        // finally block should have cleared context
        assertThat(TenantContext.get()).isEqualTo(TenantContext.DEFAULT_TENANT);
    }

    @Test
    void headerTooLong_shouldFallbackToDefault() throws Exception {
        String longTenantId = "a".repeat(33);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantFilter.TENANT_HEADER, longTenantId);
        MockHttpServletResponse response = new MockHttpServletResponse();

        String[] captured = new String[1];
        filter.doFilter(request, response, capturingChain(() -> captured[0] = TenantContext.get()));

        assertThat(captured[0]).isEqualTo(TenantContext.DEFAULT_TENANT);
    }
}
