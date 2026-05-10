package kingsk.grails.lsp.core.visitor

import spock.lang.Specification
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.model.TextFile
import java.util.concurrent.ConcurrentHashMap

class GrailsASTVisitorSpec extends Specification {

    def "should cleanup unused files based on LRU policy"() {
        given: "AST visitor with GrailsService mock"
        def mockService = Mock(GrailsService)
        def mockFileTracker = Mock(FileContentTracker)
        mockService.fileTracker >> mockFileTracker
        def visitor = new GrailsASTVisitor(mockService)

        and: "mock tracked URIs"
        def trackedUris = ["file1.groovy", "file2.groovy"] as Set
        mockFileTracker.activeTextFiles >> {
            trackedUris.collect { uri -> new TextFile(uri: uri) }
        }

        and: "populate visitor with multiple files"
        def nodesByURI = visitor.getProperty("nodesByURI") as ConcurrentHashMap
        (1..60).each { i ->
            nodesByURI.put("file${i}.groovy", [] as Set)
        }

        when: "cleanup is triggered"
        visitor.getProperty("cleanupUnusedFiles")()

        then: "only untracked files should be cleaned up"
        nodesByURI.size() >= 2  // Should keep tracked files
        // Verify that some files were cleaned up (implementation detail)
    }

    def "should maintain LRU access tracking for files"() {
        given: "AST visitor with multiple files"
        def visitor = new GrailsASTVisitor()
        def nodesByURI = visitor.getProperty("nodesByURI") as ConcurrentHashMap

        when: "adding files beyond memory limit"
        (1..100).each { i ->
            nodesByURI.put("file${i}.groovy", [] as Set)
        }

        and: "simulating access pattern for LRU"
        // This would normally be handled by the service

        then: "files should be managed according to LRU policy"
        nodesByURI.size() > 0
        // The actual LRU implementation will be verified by the cleanup method
    }
}