package kingsk.grails.lsp.services

import com.google.gson.JsonObject
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.enums.ErrorSeverity
import kingsk.grails.lsp.model.enums.ErrorSource
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.services.WorkspaceService

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.HashSet
import java.util.Set

@Slf4j
@CompileStatic
class GrailsWorkspaceService implements WorkspaceService {
    private final GrailsService grailsService
    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor()
    private ScheduledFuture<?> debounceFuture
    private final Object lock = new Object()

    ScheduledExecutorService getOrCreateScheduler() {
        synchronized (lock) {
            if (scheduler == null || scheduler.isShutdown() || scheduler.isTerminated()) {
                scheduler = Executors.newSingleThreadScheduledExecutor()
            }
            return scheduler
        }
    }

    private final Set<String> pendingBuildChangeUris = Collections.synchronizedSet(new HashSet<String>())

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
        if (params?.changes == null) return

        Set<String> buildChanges = new HashSet<>()
        for (FileEvent event : params.changes) {
            if (event?.uri && isBuildConfigurationFile(event.uri)) {
                buildChanges.add(event.uri)
            } else if (event?.type == FileChangeType.Deleted && event?.uri) {
                grailsService.fileTracker.didDeleteFile(event.uri)
                def ctx = grailsService.workspaceManager?.getProjectForUri(event.uri)
                if (ctx != null) {
                    ctx.deleteDocument(event.uri)
                }
                grailsService.diagnostics?.clearDiagnosticsForFile(event.uri)
            }
        }

        if (buildChanges.isEmpty()) return

        synchronized (lock) {
            pendingBuildChangeUris.addAll(buildChanges)
            if (debounceFuture != null && !debounceFuture.isDone()) {
                debounceFuture.cancel(false)
            }
            debounceFuture = getOrCreateScheduler().schedule({ ->
                processBuildConfigurationChanges()
            } as Runnable, 2, TimeUnit.SECONDS)
        }
    }

    void processBuildConfigurationChanges() {
        Set<String> urisToProcess
        synchronized (lock) {
            urisToProcess = new HashSet<>(pendingBuildChangeUris)
            pendingBuildChangeUris.clear()
        }
        if (urisToProcess.isEmpty()) return

        log.info "[WORKSPACE] Build configuration files changed (debounced ${urisToProcess.size()} file(s)) - invalidating cache and syncing affected projects"
        grailsService.gradle?.invalidateCache()

        def workspaceManager = grailsService.workspaceManager
        if (workspaceManager != null) {
            Set<ProjectContextImpl> affected = new HashSet<>()
            for (String uri : urisToProcess) {
                def ctx = workspaceManager.getProjectForUri(uri)
                if (ctx != null) {
                    affected.add(ctx)
                } else {
                    String lower = uri.toLowerCase()
                    if (lower.contains("settings.gradle") || lower.contains(".versions.toml")) {
                        affected.addAll(workspaceManager.allContexts)
                    }
                }
            }

            for (ProjectContextImpl ctx : affected) {
                if (ctx.state == ProjectState.FAILED) {
                    ctx.resetFailedState()
                }
                ctx.triggerGradleSync()
            }
        }
    }

    @Override
    void didChangeWorkspaceFolders(DidChangeWorkspaceFoldersParams params) {
        if (params?.event == null) return
        def workspaceManager = grailsService.workspaceManager
        if (workspaceManager == null) return

        if (params.event.removed != null) {
            for (WorkspaceFolder folder : params.event.removed) {
                if (folder?.uri) {
                    log.info "[WORKSPACE] Workspace folder removed: ${folder.uri}"
                    workspaceManager.removeRoot(folder.uri)
                }
            }
        }

        if (params.event.added != null) {
            for (WorkspaceFolder folder : params.event.added) {
                if (folder?.uri) {
                    log.info "[WORKSPACE] Workspace folder added: ${folder.uri}"
                    workspaceManager.startRootDiscovery(folder.uri)
                }
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

    static boolean isBuildConfigurationFile(String uri) {
        if (!uri) return false
        String cleanUri = uri.toLowerCase()
        if (cleanUri.endsWith("build.gradle") ||
            cleanUri.endsWith("build.gradle.kts") ||
            cleanUri.endsWith("settings.gradle") ||
            cleanUri.endsWith("settings.gradle.kts") ||
            cleanUri.endsWith("gradle.properties") ||
            cleanUri.endsWith(".versions.toml") ||
            cleanUri.endsWith("libs.versions.toml")) {
            return true
        }
        if (cleanUri.contains("/gradle/") && cleanUri.endsWith(".toml")) {
            return true
        }
        return false
    }

    void shutdown() {
        log.info("[WORKSPACE] Shutting down workspace service scheduler...")
        ScheduledExecutorService toShutdown = null
        synchronized (lock) {
            toShutdown = scheduler
            scheduler = null
        }
        if (toShutdown != null && !toShutdown.isShutdown()) {
            toShutdown.shutdown()
            try {
                if (!toShutdown.awaitTermination(3, TimeUnit.SECONDS)) {
                    toShutdown.shutdownNow()
                }
            } catch (InterruptedException e) {
                toShutdown.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
}
