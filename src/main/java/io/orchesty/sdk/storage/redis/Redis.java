package io.orchesty.sdk.storage.redis;

import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

/**
 * Thin Redis wrapper over a Jedis connection pool.
 * Provides the key, counter, list and lock operations used by workers and {@code CacheService}.
 *
 * <p>Requires the optional {@code redis.clients:jedis} dependency on the classpath.
 */
public class Redis implements AutoCloseable {

    private final JedisPool pool;

    public Redis(String url) {
        this.pool = new JedisPool(java.net.URI.create(url));
    }

    public boolean has(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.exists(key);
        }
    }

    public String get(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.get(key);
        }
    }

    public boolean set(String key, String value, Integer expiresInSeconds) {
        try (Jedis jedis = pool.getResource()) {
            String reply = expiresInSeconds == null ? jedis.set(key, value) : jedis.setex(key, expiresInSeconds, value);
            return "OK".equalsIgnoreCase(reply);
        }
    }

    public boolean set(String key, String value) {
        return set(key, value, null);
    }

    public long incrBy(String key, long value, Integer expiresInSeconds) {
        try (Jedis jedis = pool.getResource()) {
            long result = jedis.incrBy(key, value);
            if (expiresInSeconds != null) {
                jedis.expire(key, expiresInSeconds);
            }
            return result;
        }
    }

    public List<String> getList(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.lrange(key, 0, -1);
        }
    }

    public boolean pushToList(String key, String value, Integer expiresInSeconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.rpush(key, value);
            if (expiresInSeconds != null) {
                jedis.expire(key, expiresInSeconds);
            }
            return true;
        }
    }

    /**
     * Tries to acquire a lock with {@code SET key 1 NX EX expiration}.
     *
     * @param key lock key
     * @param expiration lock expiration in seconds
     * @return {@code true} if the key was already locked (the lock was not acquired)
     */
    public boolean isLocked(String key, int expiration) {
        try (Jedis jedis = pool.getResource()) {
            String reply = jedis.set(key, "1", new SetParams().nx().ex(expiration));
            return !"OK".equalsIgnoreCase(reply);
        }
    }

    public boolean remove(String key) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.del(key) > 0;
        }
    }

    public void setExpire(String key, int seconds) {
        try (Jedis jedis = pool.getResource()) {
            jedis.expire(key, seconds);
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
