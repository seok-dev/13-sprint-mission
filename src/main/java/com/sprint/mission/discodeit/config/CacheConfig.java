package com.sprint.mission.discodeit.config;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.discodeit.dto.channel.ChannelDto;
import com.sprint.mission.discodeit.dto.notification.NotificationDto;
import com.sprint.mission.discodeit.dto.user.UserDto;
import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {


    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheConfiguration redisCacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("discodeit:")
                .entryTtl(Duration.ofSeconds(600))
                .disableCachingNullValues();
    }

    @Bean(name = "cacheManager")
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                               RedisCacheConfiguration configuration,
                                               CacheProperties properties,
                                               ObjectMapper objectMapper) {

        ObjectMapper redisObjectMapper = objectMapper.copy();

        var builder = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(configuration)
                .withInitialCacheConfigurations(Map.of(
                        "users", listCacheConfiguration(
                                configuration, redisObjectMapper, UserDto.class),
                        "channels", listCacheConfiguration(
                                configuration, redisObjectMapper, ChannelDto.class),
                        "notifications", listCacheConfiguration(
                                configuration, redisObjectMapper, NotificationDto.class)
                ))
                .transactionAware();

        if (properties.getRedis().isEnableStatistics()) {
            builder.enableStatistics();
        }

        return builder.build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine")
    public CacheManager cacheManager(CacheProperties properties) {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        manager.setCacheSpecification(
                properties.getCaffeine().getSpec()
        );

        manager.setCacheNames(properties.getCacheNames());

        return new TransactionAwareCacheManagerProxy(manager);
    }


    private RedisCacheConfiguration listCacheConfiguration(
            RedisCacheConfiguration configuration,
            ObjectMapper objectMapper,
            Class<?> elementType
    ) {
        JavaType listType = objectMapper.getTypeFactory()
                .constructCollectionType(List.class, elementType);

        Jackson2JsonRedisSerializer<Object> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, listType);

        return configuration.serializeValuesWith(
                RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer)
        );
    }
}
