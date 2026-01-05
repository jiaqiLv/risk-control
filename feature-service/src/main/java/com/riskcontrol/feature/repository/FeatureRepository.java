package com.riskcontrol.feature.repository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class FeatureRepository {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    public FeatureRepository(
        @Qualifier("reactiveRedisTemplate")
        ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> saveFeatures(String key, Map<String, String> features) {
        // Save as hash
        return redisTemplate.opsForHash().putAll(key, features);
    }

    public Mono<Map<String, String>> getFeatures(String key) {
        return redisTemplate.opsForHash().entries(key)
                .collectMap(
                    entry -> String.valueOf(entry.getKey()),
                    entry -> String.valueOf(entry.getValue())
                );
    }

    public Mono<Boolean> deleteFeature(String key) {
        return redisTemplate.opsForHash().delete(key);
    }
}
