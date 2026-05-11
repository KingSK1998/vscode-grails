package kingsk.grails.lsp.core.gradle

import groovy.transform.CompileStatic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@CompileStatic
class LRUCache<K, V> {
    private final int maxSize
    private final long ttlMs
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>()
    private final AtomicLong accessCounter = new AtomicLong(0)

    LRUCache(int maxSize, long ttlMs = 30000L) {
        this.maxSize = maxSize
        this.ttlMs = ttlMs
    }

    void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, accessCounter.incrementAndGet()))
        evictIfNecessary()
    }

    V get(K key) {
        CacheEntry<V> entry = cache.get(key)
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
                cache.remove(key)
                return null
            }
            entry.accessOrder = accessCounter.incrementAndGet()
            return entry.value
        }
        return null
    }

    private void evictIfNecessary() {
        if (cache.size() > maxSize) {
            def entries = cache.entrySet().toList()
            entries.sort { it.value.accessOrder }
            def toRemove = entries.size() - maxSize
            entries.take(toRemove).each { cache.remove(it.key) }
        }
    }

    int size() {
        return cache.size()
    }

    void clear() {
        cache.clear()
    }

    private static class CacheEntry<V> {
        final V value
        final long timestamp
        volatile long accessOrder

        CacheEntry(V value, long accessOrder) {
            this.value = value
            this.timestamp = System.currentTimeMillis()
            this.accessOrder = accessOrder
        }
    }
}