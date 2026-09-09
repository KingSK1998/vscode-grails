package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.types.TextFile

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.Queue
import java.util.LinkedList
import java.util.Set
import java.util.HashSet

@Slf4j
@CompileStatic
class WorkspaceManager {
    private final GrailsService grailsService
    private final Map<String, ProjectContextImpl> contexts = new ConcurrentHashMap<>()
    private final Map<String, Long> rootGenerations = new ConcurrentHashMap<>()
    private final java.util.concurrent.atomic.AtomicLong generationSequence = new java.util.concurrent.atomic.AtomicLong(0)
    private final Map<String, CompletableFuture<GrailsProject>> inFlightDiscoveries = new ConcurrentHashMap<>()
    private volatile boolean stopped = false
    private String activeProjectUri
    private final int maxActiveProjects = 2 // Phase 3 memory budget default

    WorkspaceManager(GrailsService grailsService) {
        this.grailsService = grailsService
    }

    void initializeRoots(List<String> rootUris) {
        if (stopped) return
        if (rootUris == null || rootUris.isEmpty()) {
            log.info("[WORKSPACE] Initialized with 0 workspace roots")
            grailsService.notifyAllProjects()
            return
        }
        log.info("[WORKSPACE] Initializing ${rootUris.size()} workspace root(s)")
        List<CompletableFuture<?>> futures = []
        for (String rootUri : rootUris) {
            CompletableFuture<GrailsProject> future = startRootDiscovery(rootUri)
            if (future != null) {
                futures.add(future.handle({ GrailsProject p, Throwable t -> p }))
            }
        }
        CompletableFuture.allOf(futures as CompletableFuture<?>[]).whenComplete({ Void v, Throwable t ->
            if (!stopped) {
                grailsService.notifyAllProjects()
            }
        })
    }

    CompletableFuture<GrailsProject> startRootDiscovery(String rootUri) {
        if (stopped || !rootUri) return CompletableFuture.completedFuture(null)
        String normUri = TextFile.normalizePath(rootUri)
        if (!normUri) return CompletableFuture.completedFuture(null)

        long generation = generationSequence.incrementAndGet()
        rootGenerations.put(normUri, generation)
        log.info("[WORKSPACE] Starting root discovery for ${normUri} (generation ${generation})")

        CompletableFuture<GrailsProject> prior = inFlightDiscoveries.remove(normUri)
        if (prior != null && !prior.isDone()) {
            prior.cancel(true)
        }

        CompletableFuture<GrailsProject> future = grailsService.gradle.getGrailsProjectAsync(normUri)
        inFlightDiscoveries.put(normUri, future)

        future.whenComplete({ GrailsProject project, Throwable ex ->
            inFlightDiscoveries.remove(normUri, future)
            if (stopped) return

            Long currentGen = rootGenerations.get(normUri)
            if (currentGen == null || currentGen != generation) {
                log.info("[WORKSPACE] Discarding late discovery result for ${normUri} (result generation ${generation}, current generation ${currentGen})")
                return
            }

            if (ex != null) {
                if (ex instanceof java.util.concurrent.CancellationException || ex?.cause instanceof java.util.concurrent.CancellationException) {
                    log.info("[WORKSPACE] Root discovery cancelled for ${normUri}")
                } else {
                    log.error("[WORKSPACE] Root discovery failed for ${normUri}", ex)
                    grailsService.errorService?.handleError("Root discovery failed for ${normUri}: ${ex.message}", ex, kingsk.grails.lsp.model.enums.ErrorSource.GRADLE_SERVICE)
                }
                return
            }

            if (project != null) {
                log.info("[WORKSPACE] Root discovery succeeded for ${normUri} -> ${project.name}")
                addProject(project)
                grailsService.client?.projectUpdated(kingsk.grails.lsp.protocol.mapper.ProjectMapper.toDTO(project))
                grailsService.notifyAllProjects()
                replayStillOpenBuffers(normUri)
            }
        })

        return future
    }

    void removeRoot(String rootUri) {
        String normUri = TextFile.normalizePath(rootUri)
        if (!normUri) return
        log.info("[WORKSPACE] Removing root: ${normUri}")

        rootGenerations.remove(normUri)

        CompletableFuture<GrailsProject> inFlight = inFlightDiscoveries.remove(normUri)
        if (inFlight != null && !inFlight.isDone()) {
            inFlight.cancel(true)
        }

        removeProject(normUri)
        grailsService.notifyAllProjects()
    }

    private void replayStillOpenBuffers(String normRootUri) {
        if (grailsService.document == null || grailsService.fileTracker == null) return
        List<String> toReplay = []
        for (TextFile file : grailsService.fileTracker.openFiles) {
            if (file != null && !file.closed) {
                String normFileUri = TextFile.normalizePath(file.uri)
                if (isSameOrChildPath(normFileUri, normRootUri)) {
                    toReplay.add(file.uri)
                }
            }
        }
        if (!toReplay.isEmpty()) {
            log.info("[WORKSPACE] Replaying ${toReplay.size()} still-open document(s) for newly registered root: ${normRootUri}")
            grailsService.document.replayTrackedDocuments(toReplay)
        }
    }

    void addProject(GrailsProject project) {
        if (!project?.rootDirectory) return
        String uri = TextFile.normalizePath(project.rootDirectory.toURI().toString())
        if (!contexts.containsKey(uri)) {
            log.info("[WORKSPACE] Adding project context for: ${uri}")
            contexts.put(uri, new ProjectContextImpl(project, grailsService))
            if (!activeProjectUri) activeProjectUri = uri
        } else {
            contexts.get(uri).updateProject(project, uri)
        }
    }

    void removeProject(String uri) {
        String normalizedUri = TextFile.normalizePath(uri)
        def ctx = contexts.remove(normalizedUri)
        if (ctx) {
            log.info("[WORKSPACE] Removing project context: ${normalizedUri}")
            ctx.dispose()
        }
        if (activeProjectUri == normalizedUri) {
            activeProjectUri = contexts.keySet().findResult { it }
        }
    }

    static boolean isSameOrChildPath(String filePath, String rootPath) {
        if (!filePath || !rootPath) return false
        String f = filePath.replace('/', File.separator).replace('\\', File.separator)
        String r = rootPath.replace('/', File.separator).replace('\\', File.separator)
        if (f.equalsIgnoreCase(r)) return true
        if (!r.endsWith(File.separator)) {
            r = r + File.separator
        }
        return f.length() > r.length() && f.substring(0, r.length()).equalsIgnoreCase(r)
    }

    ProjectContextImpl getProjectForUri(String uri) {
        if (!uri) return null
        String normalizedUri = TextFile.normalizePath(uri)
        if (!normalizedUri) return null

        // Find the longest matching root
        ProjectContextImpl match = contexts.values()
            .findAll { isSameOrChildPath(normalizedUri, TextFile.normalizePath(it.project.rootDirectory.toURI().toString())) }
            .sort { -TextFile.normalizePath(it.project.rootDirectory.toURI().toString()).length() }
            .find { it }

        if (match != null) {
            match.markAccessed()
            enforceMemoryBudget()
            return match
        }
        return null
    }

    void enforceMemoryBudget() {
        def activeContexts = contexts.values().findAll { it.state == ProjectState.READY }
        if (activeContexts.size() > maxActiveProjects) {
            def toHibernate = activeContexts.sort { it.lastAccessedTime }.dropRight(maxActiveProjects)
            toHibernate.each { ctx ->
                log.info("[WORKSPACE] Memory budget exceeded. Hibernating LRU project: ${ctx.project.name}")
                ctx.hibernate()
            }
        }
    }

    ProjectContextImpl getDefaultProject() {
        return activeProjectUri ? contexts.get(activeProjectUri) : null
    }

    void shutdown() {
        log.info("[WORKSPACE] Shutting down WorkspaceManager...")
        stopped = true
        rootGenerations.clear()
        for (CompletableFuture<GrailsProject> future : inFlightDiscoveries.values()) {
            if (future != null && !future.isDone()) {
                future.cancel(true)
            }
        }
        inFlightDiscoveries.clear()
        for (ProjectContextImpl ctx : contexts.values()) {
            ctx.dispose()
        }
        contexts.clear()
        activeProjectUri = null
    }

    Collection<ProjectContextImpl> getAllContexts() {
        return contexts.values()
    }

    List<GrailsProject> getAllProjects() {
        return contexts.values().collect { it.project }
        return contexts.values().collect { it.project }.findAll { it != null }
    }

    /**
     * Propagates dependency invalidation downstream using BFS traversal with cycle protection.
     * Marks dependent project contexts dirty and triggers background recompilation.
     */
    void propagateInvalidation(ProjectContextImpl upstream) {
        Set<ProjectContextImpl> visited = new HashSet<>()
        Queue<ProjectContextImpl> queue = new LinkedList<>()
        queue.add(upstream)
        visited.add(upstream)

        while (!queue.isEmpty()) {
            ProjectContextImpl current = queue.poll()

            // Find all downstream projects that depend on 'current'
            for (ctx in contexts.values()) {
                if (!visited.contains(ctx) && projectDependsOn(ctx, current)) {
                    visited.add(ctx)
                    ctx.setDependencyDirty(true)
                    ctx.recompileAsync()
                    queue.add(ctx)
                }
            }
        }
    }

    /**
     * Checks if source project depends on target dependency project.
     * Evaluates dependency names and checks if classpath jar references reside within dependency root directory.
     */
    boolean projectDependsOn(ProjectContextImpl source, ProjectContextImpl dependency) {
        if (source == dependency) return false

        // 1. Compare by name in dependencies
        def depNames = source.project.dependencies.collect { it.name }.toSet()
        if (depNames.contains(dependency.project.name)) {
            return true
        }

        // 2. Check if any jar classpath of source resides inside dependency's root folder
        String depRootPath = dependency.project.rootDirectory?.absolutePath
        if (depRootPath) {
            for (dep in source.project.dependencies) {
                if (dep.jarFileClasspath != null) {
                    String jarPath = dep.jarFileClasspath.absolutePath
                    if (jarPath.startsWith(depRootPath)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}
