package kingsk.grails.lsp.providersWorkspace

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode

@Slf4j
@CompileStatic
class GrailsTestDiscoveryProvider {
    private final GrailsService service

    GrailsTestDiscoveryProvider(GrailsService service) {
        this.service = service
    }

    List<Map<String, Object>> discoverTests(String projectUri) {
        def project = service.getProjectForUri(projectUri)
        if (!project) return []

        List<Map<String, Object>> tests = []

        // Search in src/test/groovy
        service.visitor.allClassNodes.each { String uri, Set<ClassNode> classNodes ->
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
