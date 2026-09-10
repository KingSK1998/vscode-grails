package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.cache.ThreadSafeLruCache
import java.util.concurrent.ConcurrentHashMap
import java.util.Set

/**
 * Thread-safe cache for Groovydocs associated with symbol descriptors.
 * Maintains an O(1) reverse-index mapping URIs to descriptors for fast invalidation on file changes.
 */
@Slf4j
@CompileStatic
class GroovydocCache {
    private final ThreadSafeLruCache<String, String> lruCache
    private final ConcurrentHashMap<String, Set<String>> uriToDescriptors = new ConcurrentHashMap<>()

    GroovydocCache(int capacity) {
        this.lruCache = new ThreadSafeLruCache<>(capacity)
    }

    /**
     * Retrieve the Groovydoc for a descriptor. If absent, load it using the loader,
     * cache it, and track its association with the given URI.
     */
    String getGroovydoc(String descriptor, String uri, Closure<String> loader) {
        if (!descriptor) return null
        
        String cached = lruCache.getIfPresent(descriptor)
        if (cached != null) {
            return cached
        }

        String loaded = loader.call()
        if (loaded != null) {
            lruCache.put(descriptor, loaded)
            if (uri) {
                Set<String> descriptors = uriToDescriptors.computeIfAbsent(uri, { k -> (Set<String>) ConcurrentHashMap.newKeySet() })
                descriptors.add(descriptor)
            }
            return loaded
        }
        return null
    }

    /**
     * Evict all descriptors associated with the given URI.
     */
    void evictFile(String uri) {
        if (!uri) return
        Set<String> descriptors = uriToDescriptors.remove(uri)
        if (descriptors) {
            for (String desc : descriptors) {
                lruCache.remove(desc)
            }
        }
    }

    void clear() {
        lruCache.clear()
        uriToDescriptors.clear()
    }

    int size() {
        return lruCache.size()
    }
}
