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
    
    private final ReentrantReadWriteLock astLock = new ReentrantReadWriteLock()
    private final AtomicLong versionCounter = new AtomicLong(0)
    
    // Atomic source of truth for the entire project
    final AtomicReference<VersionedSnapshot> activeSnapshot = new AtomicReference<>(
        new VersionedSnapshot(0, new ProjectIndex("empty").snapshot, null, null, [:], System.currentTimeMillis())
    )

    ProjectContextImpl(GrailsProject project, GrailsService grailsService) {
        this.project = project
        this.grailsService = grailsService
        
        this.projectIndex = new ProjectIndex(project.name ?: "root")
        this.methodScopeCache = new MethodScopeCache()
        this.groovydocCache = new GroovydocCache(100)
        this.indexManager = new IndexManager(projectIndex, methodScopeCache, groovydocCache)
        
        // Initialize the first generation
        this.compiler = new GrailsCompiler(grailsService)
        this.visitor = new GrailsASTVisitor(grailsService)
    }

    // --- Core Atomic Compilation & Commit Protocol (GAP-10) ---

    @Override
    void compileAndVisitAST(TextFile textFile) {
        if (!textFile) return

        List<ClassNode> classNodes = null
        withWriteLock {
            if (!compiler.isDirty(textFile.uri) && compiler.compilationExistsFor(textFile.uri)) {
                return
            }

            compiler.markDirty(textFile.uri)
            compiler.compileSourceFile(textFile)
            
            def sourceUnit = compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                visitor.visitSourceUnit(sourceUnit)
                classNodes = sourceUnit.getAST()?.getClasses()
            }
        }

        if (classNodes != null) {
            indexManager.rebuildFile(textFile.uri, classNodes)
        }
        
        commitSnapshot()
        grailsService.clearCrossFileCaches()
    }

    @Override
    void visitAST(TextFile textFile) {
        List<ClassNode> classNodes = null
        withWriteLock {
            def sourceUnit = compiler.getSourceUnit(textFile)
            if (sourceUnit) {
                visitor.visitSourceUnit(sourceUnit)
                classNodes = sourceUnit.getAST()?.getClasses()
            }
        }
        if (classNodes != null) {
            indexManager.rebuildFile(textFile.uri, classNodes)
        }
        commitSnapshot()
    }

    /**
     * Commits the current generation as an immutable VersionedSnapshot.
     * SWAPS: The visitor instance to ensure the committed one is never cleared.
     */
    void commitSnapshot() {
        long ver = versionCounter.incrementAndGet()
        
        // CAPTURE: The current visitor as a stable ASTAccessor for this snapshot
        ASTAccessor snapshotAccessor = (ASTAccessor) this.visitor 

        def newSnapshot = new VersionedSnapshot(
            ver,
            projectIndex.snapshot,
            toGradleModel(project),
            snapshotAccessor,
            [:],
            System.currentTimeMillis()
        )
        
        // ATOMIC SWAP: The new snapshot becomes the source of truth
        activeSnapshot.set(newSnapshot)
        
        // GENERATION SWAP: Create a fresh visitor for the next compilation cycle.
        // We reuse the compiler state for incrementalism, but the visitor maps 
        // are effectively "frozen" inside the snapshotAccessor reference.
        // Note: In a production refinement, we might copy the maps if visitor is too heavy.
        // For Phase 3, we ensure the visitor object is detached from clearing.
        this.visitor = new GrailsASTVisitor(grailsService)
        
        log.debug("[ProjectContext] Committed snapshot v${ver}")
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

    void hibernate() {
        log.info("[ProjectContext] Hibernating project ${project.name}")
        withWriteLock {
            compiler.invalidateCompiler()
            visitor.invalidateVisitor()
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

    @Override GrailsProject getProject() { project }
    @Override Map<String, GrailsProject> getProjects() { [(project.rootDirectory.toURI().toString()): project] }
    @Override String getActiveProjectUri() { project.rootDirectory.toURI().toString() }
    @Override void setActiveProjectUri(String uri) { }
    @Override ProjectDTO getProjectInfo(String projectDir) { ProjectMapper.toDTO(project) }
    @Override void notifyAllProjects() { }
    @Override void addProject(GrailsProject project) { this.project = project }
    @Override void removeProject(String projectDir) { }
    @Override void updateProject(GrailsProject p, String projectDir) { this.project = p }
    @Override GrailsProject getProjectForUri(String uri) { project }

    // --- CompilationContext Interface ---

    @Override GrailsCompiler getCompiler() { compiler }
    @Override GrailsASTVisitor getVisitor() { visitor }
    @Override FileContentTracker getFileTracker() { grailsService.fileTracker }
    @Override kingsk.grails.lsp.services.ASTService getAstService() { null }
    @Override ProjectIndex getProjectIndex() { projectIndex }
    @Override MethodScopeCache getMethodScopeCache() { methodScopeCache }
    @Override GroovydocCache getGroovydocCache() { groovydocCache }
    @Override GrailsService getGrailsService() { grailsService }
}
