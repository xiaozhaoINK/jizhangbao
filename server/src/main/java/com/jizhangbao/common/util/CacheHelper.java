package com.jizhangbao.common.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Redis 缓存门面：统一封装 "读缓存 -> 回源 -> 写缓存" 与限流能力。
 *
 * <p>缓存不可用时自动降级为直接回源，保证业务可用性（缓存是加速手段，不是强依赖）。</p>
 */
@Component
public class CacheHelper {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public CacheHelper(RedisTemplate<String, Object> redisTemplate,
                       StringRedisTemplate stringRedisTemplate,
                       ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 以 JSON 字符串缓存的读穿透封装（推荐用于 DTO / 集合，类型信息不丢失）。
     */
    public <T> T rememberJson(String key, Duration ttl, TypeReference<T> typeReference, Supplier<T> loader) {
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, typeReference);
            }
        } catch (Exception ignored) {
            // 反序列化失败（例如 DTO 结构已变更）时直接回源
        }
        T loaded = loader.get();
        if (loaded != null) {
            try {
                stringRedisTemplate.opsForValue()
                        .set(key, objectMapper.writeValueAsString(loaded), ttl);
            } catch (Exception ignored) {
            }
        }
        return loaded;
    }

    public void setJson(String key, Object value, Duration ttl) {
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value), ttl);
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return (T) value;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public void set(String key, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
            // 缓存写入失败不影响主流程
        }
    }

    /**
     * 读缓存，未命中则回源并写入缓存。
     */
    public <T> T remember(String key, Duration ttl, Class<T> type, Supplier<T> loader) {
        T cached = get(key, type);
        if (cached != null) {
            return cached;
        }
        T loaded = loader.get();
        if (loaded != null) {
            set(key, loaded, ttl);
        }
        return loaded;
    }

    public void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }

    public void delete(Collection<String> keys) {
        try {
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    /** 按前缀批量删除（首页统计缓存失效用） */
    public void deleteByPrefix(String prefix) {
        try {
            Set<String> keys = redisTemplate.keys(prefix + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception ignored) {
        }
    }

    /**
     * 计数器自增（用于分钟级限流）。
     *
     * @return 自增后的值；Redis 不可用时返回 0（表示不限流）
     */
    public long increment(String key, Duration ttl) {
        try {
            Long value = redisTemplate.opsForValue().increment(key);
            if (value != null && value == 1L) {
                redisTemplate.expire(key, ttl);
            }
            return value == null ? 0 : value;
        } catch (Exception e) {
            return 0;
        }
    }

    public boolean setIfAbsent(String key, Duration ttl) {
        try {
            Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean ping() {
        try {
            return Boolean.TRUE.equals(redisTemplate.getConnectionFactory() != null);
        } catch (Exception e) {
            return false;
        }
    }
}
