package com.demo.accessiblenav.apikey;

import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * Authenticates requests carrying X-API-Key and X-API-Signature headers.
 * Only intercepts /api/open/** paths.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiKeyAuthFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String API_SIGNATURE_HEADER = "X-API-Signature";

    private final ApiKeyService apiKeyService;

    public ApiKeyAuthFilter(ApiKeyService apiKeyService) {
        this.apiKeyService = apiKeyService;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/open/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String keyId = request.getHeader(API_KEY_HEADER);
        String signature = request.getHeader(API_SIGNATURE_HEADER);

        if (keyId == null || signature == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing API key or signature");
            return;
        }

        ApiKeyEntity key = apiKeyService.validate(keyId, signature, "");
        if (key == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key or signature");
            return;
        }

        // Set tenant context from API key
        TenantContext.set(key.getTenantId());

        // Record usage asynchronously
        apiKeyService.recordUsage(key);

        // Store key info as request attributes for downstream use
        request.setAttribute("apiKey", key);
        request.setAttribute("apiKeyScopes", key.getScopes());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        key.getKeyId(),
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_API"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
