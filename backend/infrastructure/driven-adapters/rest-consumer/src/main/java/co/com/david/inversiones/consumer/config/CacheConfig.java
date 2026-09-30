package co.com.david.inversiones.consumer.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Caffeine-backed caches for the two n8n endpoints the spec asks to be cached: the TRM (for the
 * rest of the calendar day) and quotes (for five minutes). The six signal endpoints are not cached
 * here — a snapshot is meant to reflect what n8n currently has, and re-fetching is exactly how a
 * previously-failing source is noticed to have recovered.
 *
 * <p>This class lives under {@code config} and is excluded from the coverage gate by convention.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Cache name for the current TRM, valid until local midnight. */
    public static final String TRM_CACHE = "trm";

    /** Cache name for current quotes, valid for five minutes. */
    public static final String QUOTES_CACHE = "quotes";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(TRM_CACHE, QUOTES_CACHE);
        manager.registerCustomCache(TRM_CACHE, Caffeine.newBuilder().expireAfter(untilMidnight()).build());
        manager.registerCustomCache(QUOTES_CACHE,
                Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).build());
        return manager;
    }

    /**
     * An entry created at any point during the day expires at the next local midnight, which is
     * what "the TRM for the rest of the day" means: it is only ever re-fetched once per calendar
     * day, however many requests arrive in between.
     */
    private static Expiry<Object, Object> untilMidnight() {
        return new Expiry<>() {
            @Override
            public long expireAfterCreate(Object key, Object value, long currentTime) {
                LocalDateTime now = LocalDateTime.now(ZoneId.systemDefault());
                LocalDateTime nextMidnight = LocalDate.from(now).plusDays(1).atTime(LocalTime.MIDNIGHT);
                return ChronoUnit.NANOS.between(now, nextMidnight);
            }

            @Override
            public long expireAfterUpdate(Object key, Object value, long currentTime, long currentDuration) {
                return currentDuration;
            }

            @Override
            public long expireAfterRead(Object key, Object value, long currentTime, long currentDuration) {
                return currentDuration;
            }
        };
    }
}
