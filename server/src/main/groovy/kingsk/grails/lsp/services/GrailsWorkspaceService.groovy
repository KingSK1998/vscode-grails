package kingsk.grails.lsp.services

import com.google.gson.JsonObject
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.enums.ErrorSeverity
import kingsk.grails.lsp.model.enums.ErrorSource
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsWorkspaceService implements WorkspaceService {
    private final GrailsService grailsService

    GrailsWorkspaceService(GrailsService grailsService) {
        this.grailsService = grailsService
    }

    @Override
    void didChangeConfiguration(DidChangeConfigurationParams params) {
        try {
            JsonObject settings = (JsonObject) params.settings
            if (settings.has(GrailsUtils.GRAILS_LSP)) {
                def config = settings.getAsJsonObject(GrailsUtils.GRAILS_LSP)
                grailsService.config.updateFromClient(config)
                grailsService.errorService.handleError("Grails LSP configuration updated successfully", null, ErrorSource.CONFIGURATION, ErrorSeverity.INFO)

                if (grailsService.config.shouldRecompileOnConfigChange && grailsService.getProject()) {
                    log.info "[WORKSPACE] Recompiling project due to configuration change"
                    def uri = grailsService.getProject().rootDirectory.toURI().toString()
                    grailsService.workspaceManager.getProjectForUri(uri)?.hibernate()
                }
            } else {
                log.warn "[WORKSPACE] No '${GrailsUtils.GRAILS_LSP}' configuration found"
            }
        } catch (Exception e) {
            grailsService.errorService.handleError("Failed to apply configuration changes", e, ErrorSource.CONFIGURATION)
        }
    }

    @Override
    void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        boolean rebuild = false
        params.changes.each { FileEvent event ->
            if (isBuildConfigurationFile(event.uri)) {
                rebuild = true
            }
        }

        if (!rebuild) return

        log.info "[WORKSPACE] Build configuration files changed - invalidating cache and rebuilding workspace"
        grailsService.gradle.invalidateCache()
    }

    @Override
    void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        params.event.added.each { folder ->
            String uri = folder.uri
            if (grailsService.getProject()?.rootDirectory?.toURI()?.toString() != uri) {
                // Trigger project discovery and setup
            }
        }
    }

    @Override
    CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        log.info "[WORKSPACE] Executing command: ${params.command}"
        switch (params.command) {
            case "grails.getDependencyGraph":
                if (params.arguments && !params.arguments.empty) {
                    return CompletableFuture.completedFuture((Object) grailsService.dependencyProvider.getDependencyGraphJson(params.arguments[0] as String))
                }
                break
            case "grails.discoverTests":
                 if (params.arguments && !params.arguments.empty) {
                    return CompletableFuture.completedFuture((Object) grailsService.testDiscoveryProvider.discoverTests(params.arguments[0] as String))
                }
                break
        }
        return CompletableFuture.completedFuture(null)
    }

    private static boolean isBuildConfigurationFile(String uri) {
        if (!uri) return false
        uri = uri.toLowerCase()
        return uri.endsWith("build.gradle") ||
               uri.endsWith("build.gradle.kts") ||
               uri.endsWith("settings.gradle") ||
               uri.endsWith("gradle.properties")
    }
}
