package kingsk.grails.lsp.core.visitor

import spock.lang.Specification
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.model.types.TextFile
import java.util.concurrent.ConcurrentHashMap

class GrailsASTVisitorSpec extends Specification {

    def "should copy AST visitor data structures during generation clone"() {
        given: "An AST visitor with populated nodes"
        def visitor1 = new GrailsASTVisitor()
        String uri = "file:///sample/Sample.groovy"
        org.codehaus.groovy.ast.ClassNode sampleClass = new org.codehaus.groovy.ast.ClassNode("sample.Sample", 0, org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE)
        visitor1.classNodesByURI.computeIfAbsent(uri, { [] as Set }).add(sampleClass)
        visitor1.nodesByURI.computeIfAbsent(uri, { [] as Set }).add(sampleClass)

        when: "cloning into a new generation visitor"
        def visitor2 = new GrailsASTVisitor()
        visitor2.copyFrom(visitor1)

        then: "all nodes and class mappings are preserved in the clone"
        visitor2.getClassNodes(uri).contains(sampleClass)
        visitor2.getNodes(uri).contains(sampleClass)
    }

    def "should evict file and its dependencies on file removal"() {
        given: "An AST visitor with a file's nodes"
        def visitor = new GrailsASTVisitor()
        String uri = "file:///sample/ToEvict.groovy"
        org.codehaus.groovy.ast.ClassNode sampleClass = new org.codehaus.groovy.ast.ClassNode("sample.ToEvict", 0, org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE)
        visitor.classNodesByURI.computeIfAbsent(uri, { [] as Set }).add(sampleClass)
        visitor.nodesByURI.computeIfAbsent(uri, { [] as Set }).add(sampleClass)

        when: "removing the file"
        visitor.removeFileWithDependencies(uri)

        then: "file nodes are evicted"
        visitor.getClassNodes(uri).isEmpty()
        visitor.getNodes(uri).isEmpty()
    }

    def "should handle null package and null AST nodes safely without throwing NPE"() {
        given: "An AST visitor"
        def visitor = new GrailsASTVisitor()

        when: "visiting null package or pushing null node"
        visitor.visitPackage((org.codehaus.groovy.ast.PackageNode) null)

        then: "no exception is thrown and visitor remains empty"
        notThrown(NullPointerException)
        visitor.isEmpty()
    }
}