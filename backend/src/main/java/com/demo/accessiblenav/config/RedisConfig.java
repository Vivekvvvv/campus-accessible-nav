package com.demo.accessiblenav.config;

import com.demo.accessiblenav.route.dto.RouteResponse;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Objects;

/**
 * Redis 配置类
 * 仅在 route.cache.redis.enabled=true 时激活
 */
@Configuration
@ConditionalOnProperty(name = "route.cache.redis.enabled", havingValue = "true")
public class RedisConfig {

    /**
     * 配置 RedisTemplate 用于路由缓存
     * 使用 JSON 序列化以便于调试和跨语言兼容
     */
    @Bean
    public RedisTemplate<String, RouteResponse> routeCacheRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, RouteResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(Objects.requireNonNull(connectionFactory));

        // Key 使用 String 序列化
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value 使用 JSON 序列化
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                objectMapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer =
                new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(serializer);
        template.setHashValueSerializer(serializer);

        template.afterPropertiesSet();
        return template;
    }
}
