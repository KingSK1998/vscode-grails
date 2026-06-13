package kingsk.grails.lsp.utils.cache

import spock.lang.Specification
import java.util.concurrent.TimeUnit

class ThreadSafeLruCacheSpec extends Specification {

    def cleanup() {
        ThreadSafeLruCache.shutdown()
    }

    def "should evict entries when maxSize exceeded"() {
        given: "a ThreadSafeLruCache with max size of 2"
        def cache = new ThreadSafeLruCache<String, String>(2)

        when: "adding 3 entries"
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        then: "least recently used entry should be evicted"
        cache.get("key1") == null
        cache.get("key2") == "value2"
        cache.get("key3") == "value3"
    }

    def "should respect time to live"() {
        given: "a cache with short TTL and cleanup interval"
        def cache = new ThreadSafeLruCache<String, String>(10, 100L, 50L) // 100ms TTL, 50ms cleanup
        cache.put("key1", "value1")

        expect: "value is present immediately"
        cache.get("key1") == "value1"

        when: "waiting for expiration"
        Thread.sleep(200)

        then: "entry should be expired and cleaned up"
        cache.get("key1") == null
    }

    def "should handle multiple restarts of the global executor"() {
        when: "creating cache and putting item"
        def cache1 = new ThreadSafeLruCache<String, String>(10)
        cache1.put("key", "val")
        
        then: "retrieval works"
        cache1.get("key") == "val"

        when: "shutting down the global cache system"
        ThreadSafeLruCache.shutdown()

        and: "creating a new cache instance after shutdown"
        def cache2 = new ThreadSafeLruCache<String, String>(10)
        cache2.put("key2", "val2")

        then: "it recreates the executor and works perfectly"
        cache2.get("key2") == "val2"
    }
}
