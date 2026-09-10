package kingsk.grails.lsp.context

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.index.GroovydocCache
import kingsk.grails.lsp.index.IndexManager
import kingsk.grails.lsp.index.MethodScopeCache
import kingsk.grails.lsp.index.ProjectIndex
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.GradleModel
import kingsk.grails.lsp.model.enums.ErrorSource
import kingsk.grails.lsp.model.state.ProjectState
import kingsk.grails.lsp.model.state.SnapshotManager
import kingsk.grails.lsp.model.state.VersionedSnapshot
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.protocol.dto.ProjectDTO
import kingsk.grails.lsp.protocol.mapper.ProjectMapper
import kingsk.grails.lsp.services.FileContentTracker
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.eclipse.lsp4j.Position

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.concurrent.CompletableFuture
import java.util.function.BooleanSupplier

/**
 * Implementation of ProjectContext that manages compilation generations.
 * ENSURES: Snapshot ↔ AST consistency by using Generation-Scoped Visitors.
 */
@Slf4j
@CompileStatic
class ProjectContextImpl implements ProjectContext, CompilationContext {

    private final GrailsService grailsService
    private GrailsProject project

    final ProjectIndex projectIndex
    final IndexManager indexManager
    final MethodScopeCache methodScopeCache
    final GroovydocCache groovydocCache

    // Live compilation state (Current Generation)
    private GrailsCompiler compiler
    private GrailsASTVisitor visitor

    // Lifecycle Tracking (Phase 3)
    private final AtomicReference<ProjectState> state = new AtomicReference<>(ProjectState.INITIALIZING)
    private final AtomicReference<CompletableFuture<Void>> activationFuture = new AtomicReference<>(null)
    private volatile long lastAccessedTime = System.currentTimeMillis()
    private volatile boolean dependencyDirty = false

    private final ReentrantReadWriteLock astLock = new ReentrantReadWriteLock()
    private final AtomicLong versionCounter = new AtomicLong(0)
    // Accessed only under astLock. These are committed editor overlays, not project source inventory.
    private final Set<String> openDocumentUris = new HashSet<>()

    // Atomic source of truth for the entire project (Lineage & LKG)
    final SnapshotManager snapshotManager

    ProjectContextImpl(GrailsProject project, GrailsService grailsService) {
        this.@project = project
        this.grailsService = grailsService

        this.projectIndex = new ProjectIndex(project.name ?: "root")
        this.methodScopeCache = new MethodScopeCache()
        this.groovydocCache = new GroovydocCache(100)
        this.indexManager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)

        // Initialize SnapshotManager
        this.snapshotManager = new SnapshotManager(
            new VersionedSnapshot(0, projectIndex.snapshot, null, null, [:], System.currentTimeMillis())
        )

        // Initialize the first generation
        this.@compiler = new GrailsCompiler(grailsService)
        this.@visitor = new GrailsASTVisitor(grailsService)
    }

    // --- Core Atomic Compilation & Commit Protocol (GAP-10) ---

    @Override
    void compileAndVisitAST(TextFile textFile) {
        compileAndVisitAST(textFile, null)
    }

    /** A queued document revision may be superseded or closed while the compiler is busy. */
    void compileAndVisitAST(TextFile textFile, BooleanSupplier currentRevision) {
        if (!textFile) return
        if (this.@state.get() == ProjectState.DISPOSING || (currentRevision != null && !currentRevision.asBoolean)) return
        if (grailsService.isTier2()) {
            log.warn("[ProjectContext] Bypassing AST compilation in Tier 2 for: ${textFile.uri}")
            return
        }
        ensureActivated()

        boolean committed = false
        withWriteLock {
            if (this.@state.get() == ProjectState.DISPOSING || this.@compiler == null) return
            if (currentRevision != null) {
                if (!currentRevision.asBoolean) return
                TextFile live = grailsService.fileTracker.getTextFile(textFile.uri)
                if (live != null && (live.closed || (live.open && live.openGeneration != textFile.openGeneration) || live.version != textFile.version)) {
                    log.debug("[ProjectContext] Stale compilation candidate rejected for {}", textFile.uri)
                    return
                }
                this.@compiler.markDirty(textFile.uri)
            }
            if (!this.@compiler.isDirty(textFile.uri) && this.@compiler.compilationExistsFor(textFile.uri)) {
                return
            }

            this.@compiler.markDirty(textFile.uri)
            this.@compiler.compileSourceFile(textFile)

            if (this.@state.get() == ProjectState.DISPOSING || (currentRevision != null && !currentRevision.asBoolean)) return
            if (currentRevision != null) {
                TextFile liveMid = grailsService.fileTracker.getTextFile(textFile.uri)
                if (liveMid != null && (liveMid.closed || (liveMid.open && liveMid.openGeneration != textFile.openGeneration) || liveMid.version != textFile.version)) {
                    log.debug("[ProjectContext] Stale candidate rejected before visit for {}", textFile.uri)
                    return
                }
            }

            def sourceUnit = this.@compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                this.@visitor.visitSourceUnit(sourceUnit)
                if (currentRevision != null && !currentRevision.asBoolean) return
                def classNodes = sourceUnit.AST?.classes
                if (classNodes != null) indexManager.rebuildFile(textFile.uri, classNodes)
            }
            if (currentRevision != null && !currentRevision.asBoolean) return
            if (currentRevision != null || grailsService.fileTracker.getOpenGeneration(textFile.uri) != null) {
                TextFile liveFinal = grailsService.fileTracker.getTextFile(textFile.uri)
                if (liveFinal != null && (liveFinal.closed || (liveFinal.openGeneration != 0L && liveFinal.openGeneration != textFile.openGeneration) || liveFinal.version != textFile.version)) {
                    log.debug("[ProjectContext] Stale candidate rejected before commit for {}", textFile.uri)
                    return
                }
                openDocumentUris.add(TextFile.normalizePath(textFile.uri))
            }
            commitSnapshotLocked()
            grailsService.clearCrossFileCaches()
            committed = true
        }
        if (!committed) return
        grailsService.workspaceManager?.propagateInvalidation(this)
        if (currentRevision != null && !currentRevision.asBoolean) return
        try {
            grailsService.diagnostics.publishDiagnosticsForFile(textFile.uri, textFile.version, textFile.openGeneration)
        } catch (Exception e) {
            log.error("[ProjectContext] Failed to publish diagnostics for {}: {}", textFile.uri, e.message)
        }
    }

    @Override
    void visitAST(TextFile textFile) {
        if (this.@state.get() == ProjectState.DISPOSING) return
        ensureActivated()
        withWriteLock {
            if (this.@state.get() == ProjectState.DISPOSING || this.@compiler == null) return
            def sourceUnit = this.@compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                this.@visitor.visitSourceUnit(sourceUnit)
                def classNodes = sourceUnit.AST?.classes
                if (classNodes != null) indexManager.rebuildFile(textFile.uri, classNodes)
            }
            commitSnapshotLocked()
        }
        grailsService.workspaceManager?.propagateInvalidation(this)
    }

    /**
     * Commits the current generation as an immutable VersionedSnapshot.
     * SWAPS: The visitor instance to ensure the committed one is never cleared.
     */
    void commitSnapshot() {
        withWriteLock { commitSnapshotLocked() }
        grailsService.workspaceManager?.propagateInvalidation(this)
    }

    private void commitSnapshotLocked() {
        if (this.@state.get() == ProjectState.DISPOSING) return
        long ver = versionCounter.incrementAndGet()

        // CAPTURE: The current visitor as a stable ASTAccessor for this snapshot
        ASTAccessor snapshotAccessor = (ASTAccessor) this.@visitor

        def newSnapshot = new VersionedSnapshot(
            ver,
            projectIndex.snapshot,
            toGradleModel(this.@project),
            snapshotAccessor,
            [:],
            System.currentTimeMillis()
        )

        // ATOMIC SWAP: Delegate to SnapshotManager to track lineage and LKG state
        boolean isSuccess = this.@compiler != null && !this.@compiler.errorCollectorOrNull?.hasErrors()
        snapshotManager.commit(newSnapshot, isSuccess)

        // GENERATION SWAP: Create a fresh visitor for the next compilation cycle.
        // We copy the visitor maps so that files not compiled in the current cycle
        // are retained, while ensuring the committed snapshot's visitor maps remain frozen/untouched.
        GrailsASTVisitor nextVisitor = new GrailsASTVisitor(grailsService)
        if (snapshotAccessor instanceof GrailsASTVisitor) {
            nextVisitor.copyFrom((GrailsASTVisitor) snapshotAccessor)
        }
        this.@visitor = nextVisitor

        log.debug("[ProjectContext] Committed snapshot v${ver}")
    }

    /** Close cleanup shares the writer boundary and never reactivates a hibernated project. */
    void closeDocument(String uri) {
        withWriteLock {
            if (this.@state.get() == ProjectState.DISPOSING) return
            if (this.@visitor == null) {
                this.@visitor = new GrailsASTVisitor(grailsService)
                def previous = snapshotManager.active?.ast
                if (previous instanceof GrailsASTVisitor) this.@visitor.copyFrom((GrailsASTVisitor) previous)
            }
            this.@visitor.removeFileWithDependencies(uri)
            indexManager.evictFile(uri)
            openDocumentUris.remove(TextFile.normalizePath(uri))
            commitSnapshotLocked()
            if (this.@state.get() == ProjectState.HIBERNATED) this.@visitor = null
            grailsService.clearCrossFileCaches()
        }
    }

    /** Permanently removes a deleted document from the project, compiler, visitor, index and snapshots. */
    void deleteDocument(String uri) {
        withWriteLock {
            if (this.@state.get() == ProjectState.DISPOSING) return
            if (this.@compiler != null) {
                this.@compiler.removeSourceFile(uri)
            }
            if (this.@visitor == null) {
                this.@visitor = new GrailsASTVisitor(grailsService)
                def previous = snapshotManager.active?.ast
                if (previous instanceof GrailsASTVisitor) this.@visitor.copyFrom((GrailsASTVisitor) previous)
            }
            this.@visitor.removeFileWithDependencies(uri)
            indexManager.evictFile(uri)
            openDocumentUris.remove(TextFile.normalizePath(uri))
            commitSnapshotLocked()
            if (this.@state.get() == ProjectState.HIBERNATED) this.@visitor = null
            grailsService.clearCrossFileCaches()
        }
    }

    /**
     * Repairs close notifications collapsed by scheduler overload. The scheduler
     * supplies the current tracker view; only previously committed overlays are
     * candidates, so ordinary project sources are never evicted.
     */
    void reconcileClosedDocuments(Set<String> currentOpenUris) {
        Set<String> normalizedOpen = (currentOpenUris ?: Collections.<String>emptySet())
            .collect { String uri -> TextFile.normalizePath(uri) } as Set<String>
        List<String> stale
        astLock.readLock().lock()
        try {
            stale = openDocumentUris.findAll { String uri -> !normalizedOpen.contains(uri) } as List<String>
        } finally {
            astLock.readLock().unlock()
        }
        for (String uri : stale) {
            closeDocument(uri)
            grailsService.fileTracker.clearClosedFileDependencies(uri)
            grailsService.diagnostics.clearDiagnosticsForFile(uri)
        }
    }

    private GradleModel toGradleModel(GrailsProject p) {
        if (!p) return null
        return new GradleModel(
            name: p.name, group: p.group, version: p.version?.toString(),
            grailsVersion: p.grailsVersion, groovyVersion: p.groovyVersion,
            isGrailsProject: p.isGrailsProject, rootDirectory: p.rootDirectory,
            sourceDirectories: p.sourceDirectories ?: [] as Set,
            testDirectories: p.testDirectories ?: [] as Set,
            dependencies: p.dependencies ?: [] as Set
        )
    }

    // --- Lifecycle Management (Phase 3) ---

    @Override
    SnapshotManager getSnapshotManager() { this.@snapshotManager }

    @Override
    void markAccessed() { this.lastAccessedTime = System.currentTimeMillis() }

    @Override
    long getLastAccessedTime() { this.lastAccessedTime }

    @Override
    ProjectState getState() { this.@state.get() }

    @Override
    CompletableFuture<Void> getActivationFuture() { this.@activationFuture.get() }

    /**
     * Wakes the project from hibernation or failure.
     * Uses atomic compare-and-set on the activationFuture to prevent race conditions
     * causing duplicate GroovyCompiler instances to spawn when multiple concurrent LSP requests arrive.
     *
     * @param activationTask The closure containing the asynchronous compilation logic.
     */
    @Override
    void reactivate(Closure<Void> activationTask) {
        if (this.@state.get() == ProjectState.DISPOSING) return

        CompletableFuture<Void> newFuture = new CompletableFuture<Void>()
        if (this.@activationFuture.compareAndSet(null, newFuture)) {
            this.@state.set(ProjectState.REACTIVATING)
            try {
                activationTask.call()
            } catch (Exception e) {
                this.@state.set(ProjectState.FAILED)
                newFuture.completeExceptionally(e)
                this.@activationFuture.set(null)
            }
        }
    }

    /**
     * Signals the project has successfully finished loading or reactivating.
     * Completing the activation future releases any concurrent LSP threads that were
     * safely blocked waiting for the compiler to become available.
     */
    @Override
    void ready() {
        if (this.@state.get() == ProjectState.DISPOSING) return
        markAccessed() // Prevent immediate LRU eviction
        this.@state.set(ProjectState.READY)
        def future = this.@activationFuture.getAndSet(null)
        if (future != null && !future.isDone()) {
            future.complete(null)
        }
        grailsService.workspaceManager?.enforceMemoryBudget()
    }

    /**
     * Releases expensive AST and compiler memory to prevent OOM errors in large workspaces.
     * Nullifies the compiler, visitor, and classloader ties so the GC can reclaim the
     * heavy Groovy ClassNode trees, while preserving the project metadata and snapshot lineage.
     */
    @Override
    void hibernate() {
        if (this.@state.get() == ProjectState.DISPOSING) return
        log.info("[ProjectContext] Hibernating project ${this.@project.name}")
        this.@state.set(ProjectState.HIBERNATED)
        withWriteLock {
            if (this.@compiler != null) this.@compiler.invalidateCompiler()
            if (this.@visitor != null) this.@visitor.invalidateVisitor()
            this.@compiler = null
            this.@visitor = null
        }
    }

    /**
     * Terminal state triggered when a workspace folder is removed.
     * Cancels any pending activation futures and clears all references to ensure no
     * background sync or compilation operations leak and run against a deleted project.
     */
    @Override
    void dispose() {
        beginDispose()
        log.info("[ProjectContext] Releasing project ${this.@project.name}")
        withWriteLock {
            if (this.@compiler != null) this.@compiler.invalidateCompiler()
            if (this.@visitor != null) this.@visitor.invalidateVisitor()
            this.@compiler = null
            this.@visitor = null
            openDocumentUris.clear()
        }
        snapshotManager.clear()
    }

    /** Makes removal terminal immediately without waiting for the compiler writer lock. */
    void beginDispose() {
        if (this.@state.getAndSet(ProjectState.DISPOSING) == ProjectState.DISPOSING) return
        log.info("[ProjectContext] Disposing project ${this.@project.name}")
        def future = this.@activationFuture.getAndSet(null)
        if (future != null && !future.isDone()) {
            future.cancel(true)
        }
    }

    /**
     * Ensures the project compiler and visitor are initialized and ready to handle requests.
     * If the project is currently reactivating, the calling thread blocks on the activation future.
     */
    private void ensureActivated() {
        ProjectState currentState = this.@state.get()
        if (currentState == ProjectState.READY) {
            return
        }
        if (currentState == ProjectState.DISPOSING) {
            throw new IllegalStateException("Cannot access compiler on a disposed project context")
        }

        CompletableFuture<Void> future = this.@activationFuture.get()
        if (future != null) {
            try {
                future.join()
            } catch (Exception e) {
                log.error("[ProjectContext] Reactivation failed while waiting", e)
                throw new IllegalStateException("Project reactivation failed", e)
            }
            return
        }

        reactivate {
            log.info("[ProjectContext] Reactivating compiler for project ${this.@project.name}")
            withWriteLock {
                this.@compiler = new GrailsCompiler(grailsService)
                this.@visitor = new GrailsASTVisitor(grailsService)
            }
            ready()
        }

        CompletableFuture<Void> postFuture = this.@activationFuture.get()
        if (postFuture != null) {
            try {
                postFuture.join()
            } catch (Exception e) {
                log.error("[ProjectContext] Reactivation failed", e)
                throw new IllegalStateException("Project reactivation failed", e)
            }
        }
    }

    // --- Locking ---

    @Override
    public <T> T withReadLock(groovy.lang.Closure<T> closure) {
        def lock = astLock.readLock()
        lock.lock()
        try { (T) closure.call() } finally { lock.unlock() }
    }

    public <T> T withWriteLock(groovy.lang.Closure<T> closure) {
        def lock = astLock.writeLock()
        lock.lock()
        try { closure.call() } finally { lock.unlock() }
    }

    // --- ProjectContext Interface ---

    @Override GrailsProject getProject() { this.@project }
    @Override Map<String, GrailsProject> getProjects() { [(this.@project.rootDirectory.toURI().toString()): this.@project] }
    @Override String getActiveProjectUri() { this.@project.rootDirectory.toURI().toString() }
    @Override void setActiveProjectUri(String uri) { }
    @Override ProjectDTO getProjectInfo(String projectDir) { ProjectMapper.toDTO(this.@project) }
    @Override void notifyAllProjects() { }
    @Override void addProject(GrailsProject project) { this.@project = project }
    @Override void removeProject(String projectDir) { }
    @Override void updateProject(GrailsProject p, String projectDir) {
        this.@project = p
        resetFailedState()
    }
    @Override GrailsProject getProjectForUri(String uri) { this.@project }

    @Override
    boolean isDependencyDirty() { dependencyDirty }

    @Override
    void setDependencyDirty(boolean dirty) { dependencyDirty = dirty }

    @Override
    void resetFailedState() {
        if (this.@state.get() == ProjectState.FAILED) {
            this.@state.set(ProjectState.HIBERNATED)
            this.@activationFuture.set(null)
            log.info("[ProjectContext] Project ${this.@project.name} reset from FAILED to HIBERNATED")
        }
    }

    @Override
    void triggerGradleSync() {
        if (this.@state.get() == ProjectState.DISPOSING) return

        CompletableFuture<Void> newFuture = new CompletableFuture<Void>()
        if (this.@activationFuture.compareAndSet(null, newFuture)) {
            this.@state.set(ProjectState.REACTIVATING)
            log.info("[ProjectContext] Triggering asynchronous Gradle sync for project: ${this.@project.name}")

            grailsService.gradle.getGrailsProjectAsync(this.@project.rootDirectory.absolutePath).whenComplete({ GrailsProject newProject, Throwable ex ->
                if (ex != null) {
                    this.@state.set(ProjectState.FAILED)
                    newFuture.completeExceptionally(ex)
                    this.@activationFuture.set(null)
                    log.error("[ProjectContext] Gradle sync failed for project: ${this.@project.name}", ex)
                    grailsService.errorService.handleError("Gradle sync failed for project ${this.@project.name}: ${ex.message}", ex, ErrorSource.GRADLE_SERVICE)
                } else if (newProject != null) {
                    this.@project = newProject
                    ready()
                }
            })
        }
    }

    @Override
    void recompileAsync() {
        CompletableFuture.runAsync({ ->
            try {
                log.info("[ProjectContext] Asynchronously recompiling project ${this.@project.name} due to dependency changes")
                ensureActivated()

                def allSources = kingsk.grails.lsp.utils.services.ServiceUtils.getAllGroovySourceFilesFromProject(this.project)
                if (allSources) {
                    withWriteLock {
                        allSources.each { file ->
                            String uri = file.toURI().toString()
                            try {
                                String text = file.text
                                TextFile textFile = TextFile.create(uri, text)
                                this.@compiler.markDirty(uri)
                                this.@compiler.compileSourceFile(textFile)
                                def sourceUnit = this.@compiler.getSourceUnit(textFile)
                                if (sourceUnit) {
                                    this.@visitor.visitSourceUnit(sourceUnit)
                                }
                            } catch (Exception fileEx) {
                                log.warn("[ProjectContext] Failed to compile source file ${uri} during async recompilation: ${fileEx.message}")
                            }
                        }
                        commitSnapshotLocked()
                        grailsService.clearCrossFileCaches()
                    }
                    grailsService.workspaceManager?.propagateInvalidation(this)
                }
                setDependencyDirty(false)
            } catch (Exception e) {
                log.error("[ProjectContext] Async recompilation failed for project ${this.@project.name}", e)
            }
        } as Runnable)
    }

    // --- CompilationContext Interface ---

    @Override GrailsCompiler getCompiler() {
        ensureActivated()
        this.@compiler
    }
    @Override GrailsASTVisitor getVisitor() {
        ensureActivated()
        this.@visitor
    }
    @Override FileContentTracker getFileTracker() { grailsService.fileTracker }
    @Override kingsk.grails.lsp.services.ASTService getAstService() { null }
    @Override ProjectIndex getProjectIndex() { projectIndex }
    @Override MethodScopeCache getMethodScopeCache() { methodScopeCache }
    @Override GroovydocCache getGroovydocCache() { groovydocCache }
    @Override GrailsService getGrailsService() { grailsService }
}
