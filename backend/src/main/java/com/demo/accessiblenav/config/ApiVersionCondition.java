package com.demo.accessiblenav.config;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.mvc.condition.RequestCondition;

import jakarta.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * API版本请求条件
 * 支持通过URL路径匹配API版本
 */
public class ApiVersionCondition implements RequestCondition<ApiVersionCondition> {

    private static final Pattern VERSION_PREFIX_PATTERN = Pattern.compile("/v(\\d+)/");

    private final String apiVersion;

    public ApiVersionCondition(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    @Override
    @NonNull
    public ApiVersionCondition combine(@NonNull ApiVersionCondition other) {
        // 方法级别的注解优先于类级别
        return new ApiVersionCondition(other.getApiVersion());
    }

    @Override
    @Nullable
    public ApiVersionCondition getMatchingCondition(@NonNull HttpServletRequest request) {
        Matcher matcher = VERSION_PREFIX_PATTERN.matcher(request.getRequestURI());
        if (matcher.find()) {
            String version = matcher.group(1);
            if (version.equals(this.apiVersion)) {
                return this;
            }
        }
        return null;
    }

    @Override
    public int compareTo(@NonNull ApiVersionCondition other, @NonNull HttpServletRequest request) {
        // 版本号大的优先匹配
        return Integer.compare(
                Integer.parseInt(other.getApiVersion()),
                Integer.parseInt(this.apiVersion)
        );
    }

    public String getApiVersion() {
        return apiVersion;
    }
}
