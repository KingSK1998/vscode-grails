package kingsk.grails.lsp.providers.workspace

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

@Slf4j
@CompileStatic
class GrailsTestDiscoveryProvider {
    private final kingsk.grails.lsp.context.ProviderContext providerContext
    private final kingsk.grails.lsp.context.CompilationContext compilationContext
    private final kingsk.grails.lsp.context.ProjectContext projectContext

    GrailsTestDiscoveryProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        this.providerContext = providerContext
        this.compilationContext = compilationContext
        this.projectContext = projectContext
    }

    List<Map<String, Object>> discoverTests(String projectUri) {
        def project = projectContext.getProjectForUri(projectUri)
        if (!project) return []

        List<Map<String, Object>> tests = []

        // Search in src/test/groovy
        compilationContext.visitor.allClassNodes.each { String uri, Set<ClassNode> classNodes ->
            if (GrailsUtils.isTestSpec(uri) || GrailsUtils.isTestClass(uri)) {
                classNodes.each { ClassNode classNode ->
                    Map<String, Object> testMap = new HashMap<>()
                    testMap.put("uri", uri)
                    testMap.put("className", classNode.name)
                    testMap.put("methods", classNode.methods.findAll { MethodNode m -> isTestMethod(m) }.collect { MethodNode m -> m.name })
                    tests.add(testMap)
                }
            }
        }
        
        return tests
    }

    List<Map<String, Object>> discoverTestsBatch(List<String> projectUris) {
        List<Map<String, Object>> allTests = []
        
        for (String projectUri : projectUris) {
            def project = projectContext.getProjectForUri(projectUri)
            if (!project) continue

            // Search in src/test/groovy for this project
            compilationContext.visitor.allClassNodes.each { String uri, Set<ClassNode> classNodes ->
                // Only include tests that belong to this project
                if (!uri.startsWith(project.rootDirectory.absolutePath)) return
                if (GrailsUtils.isTestSpec(uri) || GrailsUtils.isTestClass(uri)) {
                    classNodes.each { ClassNode classNode ->
                        Map<String, Object> testMap = new HashMap<>()
                        testMap.put("uri", uri)
                        testMap.put("className", classNode.name)
                        testMap.put("projectPath", project.rootDirectory.absolutePath)
                        testMap.put("methods", classNode.methods.findAll { MethodNode m -> isTestMethod(m) }.collect { MethodNode m -> m.name })
                        allTests.add(testMap)
                    }
                }
            }
        }
        
        return allTests
    }

    private boolean isTestMethod(MethodNode method) {
        if (!method.isPublic() || method.isStatic()) return false
        // Spock specs: any method that's not a lifecycle method
        // JUnit: @Test annotation (though Spock is more common in Grails)
        String name = method.name
        if (name in ['setup', 'cleanup', 'setupSpec', 'cleanupSpec']) return false
        if (name.contains('$')) return false
        return true
    }
}
