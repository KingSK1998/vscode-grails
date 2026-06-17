package kingsk.grails.lsp.providers.workspace

import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.services.WorkspaceManager

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

@Slf4j
@CompileStatic
class GrailsTestDiscoveryProvider {
    private final ProviderContext providerContext
    private final WorkspaceManager workspaceManager

    GrailsTestDiscoveryProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        this.providerContext = providerContext
        this.workspaceManager = workspaceManager
    }

    List<Map<String, Object>> discoverTests(String projectUri) {
        def projectCtx = workspaceManager.getProjectForUri(projectUri)
        if (!projectCtx) return []

        List<Map<String, Object>> tests = []

        // Search in src/test/groovy
        projectCtx.withReadLock {
            projectCtx.visitor.allClassNodes.each { String uri, Set<ClassNode> classNodes ->
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
        }
        
        return tests
    }

    List<Map<String, Object>> discoverTestsBatch(List<String> projectUris) {
        List<Map<String, Object>> allTests = []
        
        for (String projectUri : projectUris) {
            def projectCtx = workspaceManager.getProjectForUri(projectUri)
            if (!projectCtx) continue

            // Search in src/test/groovy for this project
            projectCtx.withReadLock {
                projectCtx.visitor.allClassNodes.each { String uri, Set<ClassNode> classNodes ->
                    // Only include tests that belong to this project
                    if (!uri.startsWith(projectCtx.project.rootDirectory.absolutePath)) return
                    if (GrailsUtils.isTestSpec(uri) || GrailsUtils.isTestClass(uri)) {
                        classNodes.each { ClassNode classNode ->
                            Map<String, Object> testMap = new HashMap<>()
                            testMap.put("uri", uri)
                            testMap.put("className", classNode.name)
                            testMap.put("projectPath", projectCtx.project.rootDirectory.absolutePath)
                            testMap.put("methods", classNode.methods.findAll { MethodNode m -> isTestMethod(m) }.collect { MethodNode m -> m.name })
                            allTests.add(testMap)
                        }
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
