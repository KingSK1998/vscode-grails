package kingsk.grails.lsp.index

import spock.lang.Specification

class GroovydocCacheSpec extends Specification {

    def "inserts and retrieves cached groovydocs"() {
        given:
        GroovydocCache cache = new GroovydocCache(10)
        int loadCount = 0
        Closure<String> loader = {
            loadCount++
            return "Loaded Doc"
        }

        when: "retrieving a doc for the first time"
        String doc1 = cache.getGroovydoc("com.example.Class#method", "file:///test.groovy", loader)

        then: "loader was invoked"
        doc1 == "Loaded Doc"
        loadCount == 1
        cache.size() == 1

        when: "retrieving the doc again"
        String doc2 = cache.getGroovydoc("com.example.Class#method", "file:///test.groovy", loader)

        then: "retrieved from cache without loader invocation"
        doc2 == "Loaded Doc"
        loadCount == 1
        cache.size() == 1
    }

    def "evicts all descriptors associated with a URI"() {
        given:
        GroovydocCache cache = new GroovydocCache(10)
        cache.getGroovydoc("desc1", "file:///test1.groovy", { "doc1" })
        cache.getGroovydoc("desc2", "file:///test1.groovy", { "doc2" })
        cache.getGroovydoc("desc3", "file:///test2.groovy", { "doc3" })

        expect:
        cache.size() == 3

        when: "evicting file1"
        cache.evictFile("file:///test1.groovy")

        then: "descriptors from file1 are evicted, but file2 remains"
        cache.size() == 1
        cache.getGroovydoc("desc3", "file:///test2.groovy", { "fail" }) == "doc3"
        
        and: "file1 descriptors are reloaded if requested"
        cache.getGroovydoc("desc1", "file:///test1.groovy", { "reloaded1" }) == "reloaded1"
    }
}
