package kingsk.grails.lsp.services

import com.google.gson.JsonObject
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.providersWorkspace.GrailsWorkspaceSymbolProvider
import kingsk.grails.lsp.utils.GrailsUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
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
                reportingService.info("[WORKSPACE] Grails LSP configuration updated successfully")

                if (grailsService.config.shouldRecompileOnConfigChange && grailsService.project) {
                    log.info "[WORKSPACE] Recompiling project due to configuration change"
                    // Invalidate and async compile
                    grailsService.refreshAndReindexWorkspace(
                        grailsService.project.rootDirectory.toURI().toString(),
                        "Configuration Change Refresh"
                    )
                } else {
                    log.warn "[WORKSPACE] No '${GrailsUtils.GRAILS_LSP}' configuration found"
                }
            }
        } catch (Exception e) {
            log.error "[WORKSPACE] Failed to apply configuration changes", e
            reportingService.error("[WORKSPACE] Failed to apply Grails LSP configuration", e)
        }
    }

    @Override
    void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
        log.info "[WORKSPACE] Watched files changed: ${params.changes}"

        // If key Gradle files changed, clear caches and rebuild project
        boolean rebuild = params.changes.any { event ->
            event.uri.endsWith("build.gradle") || event.uri.endsWith("settings.gradle")
        }

        if (!rebuild) return

        log.info "[WORKSPACE] Gradle build files changed - invalidating cache and rebuilding workspace"
        grailsService.gradle.invalidateCache()
        // Re-setup workspace asynchronously to recompile and refresh caches
        grailsService.refreshAndReindexWorkspace(
            grailsService.project.rootDirectory.toURI().toString(),
            "Build Files Change Refresh"
        )
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
        }
        return CompletableFuture.completedFuture(null)
    }
}
