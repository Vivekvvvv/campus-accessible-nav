package com.demo.accessiblenav.apikey;

import com.demo.accessiblenav.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ApiKeyService 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    ApiKeyRepository repository;

    @InjectMocks
    ApiKeyService apiKeyService;

    @BeforeEach
    void setTenant() {
        TenantContext.set("test-tenant");
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ create

    @Test
    void create_shouldGenerateKeyIdAndHashSecret() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.ApiKeyCreateResult result = apiKeyService.create("owner1", "route:read", 60);

        assertThat(result.keyId()).startsWith("ak_");
        assertThat(result.secret()).isNotBlank();
        // secret 不能明文存储 — hash 必须不等于 secret
        assertThat(result.entity().getKeySecretHash()).isNotEqualTo(result.secret());
        assertThat(result.entity().getOwnerId()).isEqualTo("owner1");
        assertThat(result.entity().getTenantId()).isEqualTo("test-tenant");
        assertThat(result.entity().getScopes()).isEqualTo("route:read");
        assertThat(result.entity().getRateLimitPerMinute()).isEqualTo(60);
        verify(repository).save(any(ApiKeyEntity.class));
    }

    @Test
    void create_nullScopes_shouldDefaultToRouteRead() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.ApiKeyCreateResult result = apiKeyService.create("owner2", null, 0);

        assertThat(result.entity().getScopes()).isEqualTo("route:read");
        assertThat(result.entity().getRateLimitPerMinute()).isEqualTo(60);
    }

    @Test
    void create_zeroRateLimit_shouldDefaultTo60() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.ApiKeyCreateResult result = apiKeyService.create("owner3", "route:read", 0);

        assertThat(result.entity().getRateLimitPerMinute()).isEqualTo(60);
    }

    @Test
    void create_twoKeys_shouldHaveDifferentKeyIds() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.ApiKeyCreateResult r1 = apiKeyService.create("owner", "route:read", 60);
        ApiKeyService.ApiKeyCreateResult r2 = apiKeyService.create("owner", "route:read", 60);

        assertThat(r1.keyId()).isNotEqualTo(r2.keyId());
        assertThat(r1.secret()).isNotEqualTo(r2.secret());
    }

    // ------------------------------------------------------------------ sha256Hex

    @Test
    void sha256Hex_knownInput_shouldProduceCorrectHash() {
        // SHA-256("hello") is well-known
        String hash = ApiKeyService.sha256Hex("hello");
        assertThat(hash).isEqualTo("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824");
    }

    @Test
    void sha256Hex_sameInput_shouldBeDeterministic() {
        assertThat(ApiKeyService.sha256Hex("test")).isEqualTo(ApiKeyService.sha256Hex("test"));
    }

    // ------------------------------------------------------------------ deactivate

    @Test
    void deactivate_existingKey_shouldSetActiveFalse() {
        ApiKeyEntity entity = buildActiveKey(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        apiKeyService.deactivate(1L);

        assertThat(entity.isActive()).isFalse();
        verify(repository).save(entity);
    }

    @Test
    void deactivate_nonExistentKey_shouldDoNothing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        apiKeyService.deactivate(99L);

        verify(repository, never()).save(any());
    }

    // ------------------------------------------------------------------ validate

    @Test
    void validate_correctSignature_shouldReturnKey() {
        String secret = "abc123";
        String keyId = "ak_test";
        String body = "{\"foo\":\"bar\"}";
        String secretHash = ApiKeyService.sha256Hex(secret);
        // 签名规则: sha256(keySecretHash + body)
        String expectedSig = ApiKeyService.sha256Hex(secretHash + body);

        ApiKeyEntity entity = buildActiveKey(2L);
        entity.setKeyId(keyId);
        entity.setKeySecretHash(secretHash);

        when(repository.findByKeyIdAndActiveTrue(keyId)).thenReturn(Optional.of(entity));

        ApiKeyEntity result = apiKeyService.validate(keyId, expectedSig, body);

        assertThat(result).isNotNull();
        assertThat(result.getKeyId()).isEqualTo(keyId);
    }

    @Test
    void validate_wrongSignature_shouldReturnNull() {
        String keyId = "ak_test2";
        ApiKeyEntity entity = buildActiveKey(3L);
        entity.setKeyId(keyId);
        entity.setKeySecretHash(ApiKeyService.sha256Hex("real-secret"));

        when(repository.findByKeyIdAndActiveTrue(keyId)).thenReturn(Optional.of(entity));

        ApiKeyEntity result = apiKeyService.validate(keyId, "wrong-sig", "body");

        assertThat(result).isNull();
    }

    @Test
    void validate_inactiveKey_shouldReturnNull() {
        // findByKeyIdAndActiveTrue 对非活跃 key 返回 empty
        String keyId = "ak_inactive";
        when(repository.findByKeyIdAndActiveTrue(keyId)).thenReturn(Optional.empty());

        ApiKeyEntity result = apiKeyService.validate(keyId, "any", "body");

        assertThat(result).isNull();
    }

    @Test
    void validate_unknownKeyId_shouldReturnNull() {
        when(repository.findByKeyIdAndActiveTrue("unknown")).thenReturn(Optional.empty());

        ApiKeyEntity result = apiKeyService.validate("unknown", "sig", "body");

        assertThat(result).isNull();
    }

    // ------------------------------------------------------------------ listByOwner

    @Test
    void listByOwner_shouldDelegateToRepository() {
        List<ApiKeyEntity> keys = List.of(buildActiveKey(1L), buildActiveKey(2L));
        when(repository.findByOwnerIdOrderByCreatedAtDesc("owner1")).thenReturn(keys);

        List<ApiKeyEntity> result = apiKeyService.listByOwner("owner1");

        assertThat(result).hasSize(2);
    }

    // ------------------------------------------------------------------ helpers

    private ApiKeyEntity buildActiveKey(Long id) {
        ApiKeyEntity e = new ApiKeyEntity();
        // id 字段无 setter（@GeneratedValue），使用反射注入
        try {
            java.lang.reflect.Field f = ApiKeyEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        e.setKeyId("ak_" + id);
        e.setKeySecretHash(ApiKeyService.sha256Hex("secret" + id));
        e.setOwnerId("owner");
        e.setTenantId("test-tenant");
        e.setScopes("route:read");
        e.setRateLimitPerMinute(60);
        e.setActive(true);
        e.setCreatedAt(Instant.now());
        return e;
    }
}
