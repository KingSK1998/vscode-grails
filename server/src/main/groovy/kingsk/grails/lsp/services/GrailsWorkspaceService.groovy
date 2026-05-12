package kingsk.grails.lsp.services

import com.google.gson.JsonObject
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.protocol.dto.ProjectDTO
import kingsk.grails.lsp.providers.workspace.GrailsWorkspaceSymbolProvider
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest
import org.eclipse.lsp4j.services.WorkspaceService

import java.util.concurrent.CompletableFuture

/**
 * Handles workspace-related operations for the Grails Language Server.
 * Implements the LSP WorkspaceService interface.
 */
@Slf4j
class GrailsWorkspaceService implements WorkspaceService {
    private final GrailsService grailsService
    private final ProgressService reportingService

    GrailsWorkspaceService(GrailsService grailsService) {
        this.grailsService = grailsService
        this.reportingService = grailsService.progressService
    }

    @Override
    void didChangeConfiguration(DidChangeConfigurationParams params) {
        log.info "[WORKSPACE] Configuration changed: ${params.settings}"

        try {
            def settings = params.settings
            if (settings instanceof JsonObject && settings.has(GrailsUtils.GRAILS_LSP)) {
                def config = settings.getAsJsonObject(GrailsUtils.GRAILS_LSP)
                grailsService.config.updateFromClient(config)
                grailsService.errorService.handleError("Grails LSP configuration updated successfully", null, ErrorSource.CONFIGURATION, ErrorSeverity.INFO)

                if (grailsService.config.shouldRecompileOnConfigChange && grailsService.project) {
                    log.info "[WORKSPACE] Recompiling project due to configuration change"
                    // Invalidate and async compile
                    grailsService.refreshAndReindexWorkspace(
                        grailsService.project.rootDirectory.toURI().toString(),
                        "Configuration Change Refresh"
                    )
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
        log.info "[WORKSPACE] Watched files changed: ${params.changes}"

        boolean rebuild = params.changes.any { event ->
            isBuildConfigurationFile(event.uri)
        }

        if (!rebuild) return

        log.info "[WORKSPACE] Build configuration files changed - invalidating cache and rebuilding workspace"
        grailsService.gradle.invalidateCache()
        grailsService.refreshAndReindexWorkspace(
            grailsService.project.rootDirectory.toURI().toString(),
            "Build Files Change Refresh"
        )
    }

    private static boolean isBuildConfigurationFile(String uri) {
        if (!uri) return false
        uri = uri.toLowerCase()
        return uri.endsWith("build.gradle") ||
               uri.endsWith("build.gradle.kts") ||
               uri.endsWith("settings.gradle") ||
               uri.endsWith("settings.gradle.kts") ||
               uri.endsWith("gradle.properties") ||
               uri.endsWith("plugins.groovy") ||
               uri.endsWith("application.yml") ||
               uri.endsWith("application.yaml") ||
               uri.endsWith("application.groovy") ||
               uri.endsWith("grails-app/conf/application.yml") ||
               uri.endsWith("grails-app/conf/application.yaml") ||
               uri.endsWith("grails-app/conf/application.groovy")
    }

    @Override
    CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params) {
        return new GrailsWorkspaceSymbolProvider(grailsService).provideWorkspaceSymbols(params.query)
    }

    @Override
    CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
        return new GrailsWorkspaceSymbolProvider(grailsService).resolveWorkspaceSymbol(workspaceSymbol)
    }

    @Override
    void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        params.event.added.each { folder ->
            String uri = folder.uri
            if (grailsService.project?.rootDirectory?.toURI()?.toString() != uri) {
                grailsService.setupWorkspace(uri, true)
            }
        }
    }

    @Override
    CompletableFuture<Object> executeCommand(ExecuteCommandParams params) {
        log.info "[WORKSPACE] Executing command: ${params.command}"
        switch (params.command) {
            case "grails.getDependencyGraph":
                String projectUri = params.arguments[0]?.toString() ?: grailsService.activeProjectUri
                return CompletableFuture.supplyAsync({ ->
                    return grailsService.dependencyProvider.getDependencyGraphJson(new URI(projectUri).path)
                })
            case "grails.getGormSql":
                String uri = params.arguments[0]?.toString()
                return CompletableFuture.supplyAsync({ ->
                    return grailsService.gormSqlProvider.generateSql(uri)
                })
            case "grails.discoverTests":
                String projectUri = params.arguments[0]?.toString() ?: grailsService.activeProjectUri
                return CompletableFuture.supplyAsync({ ->
                    return grailsService.testDiscoveryProvider.discoverTests(projectUri)
                })
            case "grails.discoverTestsBatch":
                List<String> projectUris = params.arguments[0] as List<String>
                return CompletableFuture.supplyAsync({ ->
                    return grailsService.testDiscoveryProvider.discoverTestsBatch(projectUris)
                })
        }
        return CompletableFuture.completedFuture(null)
    }

    @JsonRequest("grails/projectInfo")
    CompletableFuture<ProjectDTO> getProjectInfo(Map<String, Object> params) {
        String projectDir = params.get("projectDir") as String

        return CompletableFuture.supplyAsync({
            grailsService.getProjectInfo(projectDir)
        })
    }

    @JsonRequest("grails/projects")
    CompletableFuture<List<ProjectDTO>> getAllProjects() {
        return CompletableFuture.supplyAsync({
            grailsService.projects.values().collect {
                grailsService.getProjectInfo(it.rootDirectory.absolutePath)
            }
        })
    }
}
