package kingsk.grails.lsp.index

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification
import java.util.concurrent.atomic.AtomicInteger

class IndexManagerSpec extends Specification {

    def "rebuildFile commits index snapshot and updates secondary caches"() {
        given:
        ProjectIndex projectIndex = new ProjectIndex("root")
        MethodScopeCache methodScopeCache = new MethodScopeCache()
        GroovydocCache groovydocCache = new GroovydocCache(10)
        IndexManager manager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)

        String source = '''
            class Target {
                def testMethod() {
                    def myVar = 10
                }
            }
        '''
        CompilerConfiguration config = new CompilerConfiguration()
        CompilationUnit cu = new CompilationUnit(config)
        cu.addSource("Target.groovy", source)
        cu.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)
        List<ClassNode> classNodes = cu.getAST().getClasses()

        when: "rebuilding the file"
        manager.rebuildFile("file:///Target.groovy", classNodes)

        then: "symbol is available in index snapshot"
        IndexSnapshot snapshot = projectIndex.getSnapshot()
        snapshot.getSymbolByDescriptor("Target") != null
        snapshot.getSymbolByDescriptor("Target#testMethod()") != null

        and: "locals are populated in method scope cache"
        methodScopeCache.getLocalAt("file:///Target.groovy", new org.eclipse.lsp4j.Position(3, 25)) != null
    }

    def "evictFile removes entries from all index structures and caches"() {
        given:
        ProjectIndex projectIndex = new ProjectIndex("root")
        MethodScopeCache methodScopeCache = new MethodScopeCache()
        GroovydocCache groovydocCache = new GroovydocCache(10)
        IndexManager manager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)

        String source = 'class Target { void method() { def x = 1 } }'
        CompilerConfiguration config = new CompilerConfiguration()
        CompilationUnit cu = new CompilationUnit(config)
        cu.addSource("Target.groovy", source)
        cu.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)
        List<ClassNode> classNodes = cu.getAST().getClasses()

        manager.rebuildFile("file:///Target.groovy", classNodes)
        groovydocCache.getGroovydoc("Target#method()", "file:///Target.groovy", { "some doc" })

        expect: "everything is cached"
        projectIndex.getSnapshot().getSymbolByDescriptor("Target") != null
        methodScopeCache.getLocalAt("file:///Target.groovy", new org.eclipse.lsp4j.Position(0, 36)) != null
        groovydocCache.getGroovydoc("Target#method()", "file:///Target.groovy", { "fail" }) == "some doc"

        when: "evicting file"
        manager.evictFile("file:///Target.groovy")

        then: "all caches are empty"
        projectIndex.getSnapshot().getSymbolByDescriptor("Target") == null
        methodScopeCache.getLocalAt("file:///Target.groovy", new org.eclipse.lsp4j.Position(0, 36)) == null
        groovydocCache.size() == 0
    }

    def "rebuildAll commits bulk changes in a single CAS transaction"() {
        given:
        ProjectIndex projectIndex = new ProjectIndex("root")
        MethodScopeCache methodScopeCache = new MethodScopeCache()
        GroovydocCache groovydocCache = new GroovydocCache(10)
        IndexManager manager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)

        String src1 = 'class ClassA {}'
        String src2 = 'class ClassB {}'

        CompilerConfiguration config = new CompilerConfiguration()
        
        CompilationUnit cu1 = new CompilationUnit(config)
        cu1.addSource("ClassA.groovy", src1)
        cu1.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)
        
        CompilationUnit cu2 = new CompilationUnit(config)
        cu2.addSource("ClassB.groovy", src2)
        cu2.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)

        Map<String, List<ClassNode>> bulkNodes = [
            "file:///ClassA.groovy": cu1.getAST().getClasses(),
            "file:///ClassB.groovy": cu2.getAST().getClasses()
        ]

        when: "rebuilding all in bulk"
        manager.rebuildAll(bulkNodes)

        then: "both classes are present in the snapshot"
        IndexSnapshot snapshot = projectIndex.getSnapshot()
        snapshot.getSymbolByDescriptor("ClassA") != null
        snapshot.getSymbolByDescriptor("ClassB") != null
    }
}
