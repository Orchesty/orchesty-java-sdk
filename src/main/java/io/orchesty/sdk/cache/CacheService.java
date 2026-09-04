package io.orchesty.sdk.cache;

import io.orchesty.sdk.storage.redis.Redis;
import io.orchesty.sdk.transport.HttpSender;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.transport.ResponseDto;
import io.orchesty.sdk.transport.ResultRanges;
import java.util.function.Function;

/**
 * Cache-aside helper over {@link Redis} and {@link HttpSender}.
 * On a cache miss it performs the HTTP request, stores the derived value in Redis with a TTL
 * and returns it.
 */
public class CacheService {

    private final Redis redis;
    private final HttpSender httpSender;

    public CacheService(Redis redis, HttpSender httpSender) {
        this.redis = redis;
        this.httpSender = httpSender;
    }

    /** What to store on a cache miss: the serialized value and its TTL in seconds. */
    public record CacheEntry(String dataToStore, int expire) {}

    public String entry(
            String cacheKey, RequestDto request, Function<ResponseDto, CacheEntry> onMiss, ResultRanges allowedCodes) {
        String cached = redis.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ResponseDto response = httpSender.send(request, allowedCodes);
        CacheEntry entry = onMiss.apply(response);
        if (entry.expire() > 1) {
            redis.set(cacheKey, entry.dataToStore(), entry.expire());
        }
        return entry.dataToStore();
    }

    public boolean deleteCache(String cacheKey) {
        return redis.remove(cacheKey);
    }
}
