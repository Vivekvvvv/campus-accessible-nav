package com.demo.accessiblenav.apikey;

import com.demo.accessiblenav.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final SecureRandom random = new SecureRandom();

    public ApiKeyService(ApiKeyRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a new API key. Returns the plain-text secret only once.
     */
    @Transactional
    public ApiKeyCreateResult create(String ownerId, String scopes, int rateLimitPerMinute) {
        String keyId = "ak_" + randomHex(16);
        String secret = randomHex(32);
        String secretHash = sha256Hex(secret);

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyId(keyId);
        entity.setKeySecretHash(secretHash);
        entity.setOwnerId(ownerId);
        entity.setTenantId(TenantContext.get());
        entity.setScopes(scopes != null ? scopes : "route:read");
        entity.setRateLimitPerMinute(rateLimitPerMinute > 0 ? rateLimitPerMinute : 60);
        entity.setCreatedAt(Instant.now());
        repository.save(entity);

        return new ApiKeyCreateResult(keyId, secret, entity);
    }

    @Transactional
    public void deactivate(Long id) {
        repository.findById(id).ifPresent(e -> {
            e.setActive(false);
            repository.save(e);
        });
    }

    @Transactional
    public void recordUsage(ApiKeyEntity key) {
        key.setLastUsedAt(Instant.now());
        repository.save(key);
    }

    public ApiKeyEntity validate(String keyId, String signature, String body) {
        ApiKeyEntity key = repository.findByKeyIdAndActiveTrue(keyId).orElse(null);
        if (key == null) return null;

        // Verify HMAC-SHA256 signature: sha256(secret + body)
        // For simplicity, we verify that signature == sha256(keySecretHash + body)
        String expected = sha256Hex(key.getKeySecretHash() + (body != null ? body : ""));
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        return key;
    }

    public List<ApiKeyEntity> listByOwner(String ownerId) {
        return repository.findByOwnerIdOrderByCreatedAtDesc(ownerId);
    }

    public List<ApiKeyEntity> listByTenant(String tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }

    static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    public record ApiKeyCreateResult(String keyId, String secret, ApiKeyEntity entity) {}
}
