package kingsk.grails.lsp.cache

import spock.lang.Specification
import groovy.transform.CompileStatic

@CompileStatic
class LRUCacheSpec extends Specification {

    def "should evict entries when maxSize exceeded"() {
        given: "an LRU cache with max size of 2"
        def cache = new LRUCache<String, String>(2)

        when: "adding 3 entries"
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        then: "first entry should be evicted"
        cache.get("key1") == null
        cache.get("key2") == "value2"
        cache.get("key3") == "value3"
    }

    def "should respect time to live"() {
        given: "an LRU cache with short TTL"
        def cache = new LRUCache<String, String>(10, 100L) // 100ms TTL
        cache.put("key1", "value1")

        when: "waiting for expiration"
        Thread.sleep(150) // Sleep longer than TTL

        then: "entry should be expired"
        cache.get("key1") == null
    }

    def "should update access order on get"() {
        given: "an LRU cache with 3 entries"
        def cache = new LRUCache<String, String>(3)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.put("key3", "value3")

        when: "accessing first entry"
        cache.get("key1")
        // Add another entry to trigger eviction
        cache.put("key4", "value4")

        then: "least recently used entry should be evicted"
        cache.get("key1") != null // Should still be there because we accessed it
        cache.get("key2") == null // Should be evicted (least recently used)
        cache.get("key3") != null
        cache.get("key4") != null
    }

    def "should handle null values"() {
        given: "an LRU cache"
        def cache = new LRUCache<String, String>(2)

        when: "putting null value"
        cache.put("key1", null)

        then: "should store null"
        cache.get("key1") == null
    }

    def "should clear all entries"() {
        given: "an LRU cache with entries"
        def cache = new LRUCache<String, String>(2)
        cache.put("key1", "value1")
        cache.put("key2", "value2")

        when: "clearing cache"
        cache.clear()

        then: "all entries should be removed"
        cache.size() == 0
        cache.get("key1") == null
        cache.get("key2") == null
    }
}