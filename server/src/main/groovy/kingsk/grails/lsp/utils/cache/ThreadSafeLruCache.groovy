package kingsk.grails.lsp.utils.cache

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

@Slf4j
@CompileStatic
class ThreadSafeLruCache<K, V> {
    private final int maxSize
    private final long ttlMs
    private final long cleanupIntervalMs
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>()
    private final AtomicLong hits = new AtomicLong(0)
    private final AtomicLong misses = new AtomicLong(0)
    private final AtomicLong evictions = new AtomicLong(0)
    private final Closure<V> loader
    private final boolean allowLoad

    private static final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1)

    ThreadSafeLruCache(int maxSize, long ttlMs = 30000L, long cleanupIntervalMs = 10000L) {
        this.maxSize = maxSize
        this.ttlMs = ttlMs
        this.cleanupIntervalMs = cleanupIntervalMs
        this.allowLoad = false
        startCleanupTask()
    }

    ThreadSafeLruCache(int maxSize, Closure<V> loader, long ttlMs = 30000L, long cleanupIntervalMs = 10000L) {
        this.maxSize = maxSize
        this.ttlMs = ttlMs
        this.cleanupIntervalMs = cleanupIntervalMs
        this.loader = loader
        this.allowLoad = true
        startCleanupTask()
    }

    private void startCleanupTask() {
        cleanupExecutor.scheduleAtFixedRate({
            try {
                cleanupExpired()
            } catch (Exception e) {
                log.warn("[LRU] Cleanup task failed", e)
            }
        }, cleanupIntervalMs, cleanupIntervalMs, TimeUnit.MILLISECONDS)
    }

    void put(K key, V value) {
        if (key == null) return
        CacheEntry<V> newEntry = new CacheEntry<>(value, System.currentTimeMillis())
        CacheEntry<V> existing = cache.put(key, newEntry)
        if (existing == null && cache.size() > maxSize) {
            evictLRU()
        }
    }

    V get(K key) {
        if (key == null) return null
        CacheEntry<V> entry = cache.get(key)
        if (entry == null) {
            misses.incrementAndGet()
            if (allowLoad && loader != null) {
                V loaded = loader(key)
                if (loaded != null) {
                    put(key, loaded)
                    return loaded
                }
            }
            return null
        }
        if (isExpired(entry)) {
            cache.remove(key, entry)
            misses.incrementAndGet()
            return null
        }
        hits.incrementAndGet()
        entry.accessOrder = incrementAccess()
        return entry.value
    }

    V getIfPresent(K key) {
        if (key == null) return null
        CacheEntry<V> entry = cache.get(key)
        if (entry == null) return null
        if (isExpired(entry)) {
            cache.remove(key, entry)
            return null
        }
        hits.incrementAndGet()
        entry.accessOrder = incrementAccess()
        return entry.value
    }

    boolean contains(K key) {
        if (key == null) return false
        CacheEntry<V> entry = cache.get(key)
        return entry != null && !isExpired(entry)
    }

    void remove(K key) {
        if (key == null) return
        cache.remove(key)
    }

    void clear() {
        cache.clear()
    }

    int size() {
        return cache.size()
    }

    boolean isEmpty() {
        return cache.isEmpty()
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis()
        long cutoff = now - ttlMs
        int removed = 0
        for (Map.Entry<K, CacheEntry<V>> entry : cache.entrySet()) {
            if (entry.value.timestamp < cutoff) {
                if (cache.remove(entry.key, entry.value)) {
                    removed++
                }
            }
        }
        if (removed > 0) {
            log.debug("[LRU] Cleaned up {} expired entries, current size: {}", removed, cache.size())
        }
        int toEvict = cache.size() - maxSize
        if (toEvict > 0) {
            evictLRU(toEvict)
        }
    }

    private void evictLRU() {
        evictLRU(1)
    }

    private void evictLRU(int count) {
        List<Map.Entry<K, CacheEntry<V>>> entries = cache.entrySet().toList()
        entries.sort { it.value.accessOrder <=> it.value.accessOrder }
        int evicted = 0
        for (int i = 0; i < Math.min(count, entries.size()); i++) {
            if (cache.remove(entries[i].key, entries[i].value)) {
                evicted++
            }
        }
        evictions.addAndGet(evicted)
        if (evicted > 0) {
            log.debug("[LRU] Evicted {} entries, current size: {}", evicted, cache.size())
        }
    }

    private boolean isExpired(CacheEntry<V> entry) {
        System.currentTimeMillis() - entry.timestamp > ttlMs
    }

    private long incrementAccess() {
        ThreadSafeLruCache.accessCounter.incrementAndGet()
    }

    private static final AtomicLong accessCounter = new AtomicLong(0)

    Map<String, Object> getStats() {
        [
            size: size(),
            maxSize: maxSize,
            hits: hits.get(),
            misses: misses.get(),
            evictions: evictions.get(),
            hitRate: hits.get() + misses.get() > 0 ?
                hits.get() / (hits.get() + misses.get()) * 100 : 0
        ]
    }

    void resetStats() {
        hits.set(0)
        misses.set(0)
        evictions.set(0)
    }

    static void shutdown() {
        cleanupExecutor.shutdown()
    }

    private static class CacheEntry<V> {
        final V value
        final long timestamp
        volatile long accessOrder

        CacheEntry(V value, long timestamp) {
            this.value = value
            this.timestamp = timestamp
            this.accessOrder = ThreadSafeLruCache.accessCounter.incrementAndGet()
        }
    }
}