package kingsk.grails.lsp.providersWorkspace

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import java.nio.file.Files

@Slf4j
@CompileStatic
class GrailsDependencyProvider {
    private final GrailsService service

    private static final String GRAPH_EXTRACTOR_SCRIPT = """
initscript {
    repositories {
        mavenCentral()
    }
}
allprojects {
    task extractDependencyGraph {
        doLast {
            def result = [:]
            project.configurations.findAll { it.canBeResolved }.each { config ->
                def configGraph = [:]
                try {
                    def resolutionResult = config.incoming.resolutionResult
                    resolutionResult.allComponents.each { component ->
                        def id = component.id.displayName
                        def deps = component.dependencies
                            .findAll { it instanceof org.gradle.api.artifacts.result.ResolvedDependencyResult }
                            .collect { ((org.gradle.api.artifacts.result.ResolvedDependencyResult)it).selected.id.displayName }
                        configGraph[id] = deps
                    }
                } catch (Exception e) {
                    // Skip configurations that can't be resolved
                }
                if (configGraph) {
                    result[config.name] = configGraph
                }
            }
            println "---DEPENDENCY_GRAPH_JSON_START---"
            println groovy.json.JsonOutput.toJson(result)
            println "---DEPENDENCY_GRAPH_JSON_END---"
        }
    }
}
"""

    GrailsDependencyProvider(GrailsService service) {
        this.service = service
    }

    String getDependencyGraphJson(String projectDir) {
        File rootDir = new File(projectDir)
        if (!rootDir.exists()) return "{}"

        File initScript = File.createTempFile("grails-lsp-graph-", ".gradle")
        try {
            initScript.text = GRAPH_EXTRACTOR_SCRIPT
            
            try (ProjectConnection connection = GradleConnector.newConnector()
                    .useBuildDistribution()
                    .forProjectDirectory(rootDir)
                    .connect()) {
                
                ByteArrayOutputStream output = new ByteArrayOutputStream()
                connection.newBuild()
                        .forTasks("extractDependencyGraph")
                        .withArguments("--init-script", initScript.absolutePath, "--quiet")
                        .setStandardOutput(output)
                        .run()
                
                String fullOutput = output.toString("UTF-8")
                int start = fullOutput.indexOf("---DEPENDENCY_GRAPH_JSON_START---")
                int end = fullOutput.indexOf("---DEPENDENCY_GRAPH_JSON_END---")
                
                if (start != -1 && end != -1) {
                    return fullOutput.substring(start + "---DEPENDENCY_GRAPH_JSON_START---".length(), end).trim()
                }
            }
        } catch (Exception e) {
            log.error("Failed to extract dependency graph", e)
        } finally {
            Files.deleteIfExists(initScript.toPath())
        }
        return "{}"
    }
}
