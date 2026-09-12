package kingsk.grails.lsp.core.compiler


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.core.compiler.GrailsCU
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.SourceSetModel
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.grails.GrailsUtils
import kingsk.grails.lsp.utils.services.ServiceUtils
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.control.*
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.StringReaderSource
import org.codehaus.groovy.syntax.SyntaxException

import io.github.classgraph.ScanResult
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * GrailsCompiler is the main API for compiling Grails project sources programmatically.
 * <p>
 * This class manages the Groovy compilation unit, configuration, and classloader,
 * and provides methods to set up, configure, and execute compilation for a Grails project.
 * <p>
 * Thread Safety: Coordinates with ProjectContext and SourceSetCompilationState.
 */
@Slf4j
@CompileStatic
class GrailsCompiler {
    // Thread-safety lock
    private final ReentrantLock compileLock = new ReentrantLock()

    // Compiler Configurations
    private CompilerConfiguration compilerConfig
    private final Map<String, SourceSetCompilationState> sourceSetStates = new ConcurrentHashMap<>()
    private boolean isFullCompilation = true
    private String previousContext

    // Cached error collector for latest compilation state
    private volatile ErrorCollector cachedErrorCollector
    private volatile long lastErrorCollectorUpdate = 0

    private final GrailsService grailsService
    private final ProjectContext projectContext

    GrailsCompiler(GrailsService service, ProjectContext projectContext = null) {
        this.grailsService = service
        this.projectContext = projectContext
    }

    GrailsProject getProject() {
        return projectContext?.getProject() ?: grailsService.project
    }

    String getSourceSetNameForUri(String uri) {
        GrailsProject project = getProject()
        if (project != null && uri != null) {
            SourceSetModel model = project.getSourceSetForUri(uri)
            if (model != null) return model.sourceSetName
            if (project.sourceSets != null && !project.sourceSets.isEmpty()) {
                return "unknown"
            }
            if (project.isTestFile(uri)) return "test"
        }
        return "main"
    }

    SourceSetCompilationState getStateForUri(String uri) {
        String sourceSetName = getSourceSetNameForUri(uri)
        return getOrCreateState(sourceSetName)
    }

    SourceSetCompilationState getMainState() {
        return getOrCreateState("main")
    }

    SourceSetCompilationState getOrCreateState(String sourceSetName) {
        String name = sourceSetName ?: "main"
        return sourceSetStates.computeIfAbsent(name) { String ssn ->
            GrailsProject project = getProject()
            List<URL> classpathUrls
            if ("test".equalsIgnoreCase(ssn)) {
                classpathUrls = project?.getTestClasspathUrls() ?: []
            } else if (project?.sourceSets?.containsKey(ssn)) {
                SourceSetModel ss = project.sourceSets.get(ssn)
                classpathUrls = ss.compileClasspath.collect { ServiceUtils.validateClasspathEntry(it) }.findAll { it != null }
            } else if (project?.sourceSets != null && !project.sourceSets.isEmpty()) {
                classpathUrls = []
            } else {
                classpathUrls = project?.getMainClasspathUrls() ?: []
            }
            if (!compilerConfig) updateCompilerOptions()
            return new SourceSetCompilationState(ssn, compilerConfig, classpathUrls, this.class.classLoader)
        }
    }

    //==============================================================//
    //                            API                               //
    //==============================================================//

    /**
     * Compiles the whole project, caches results and reports progress to client. </br>
     * REQUIRES: A GrailsProject instance with source files
     */
    void compileProject() {
        compileLock.lock()
        try {
            GrailsProject project = getProject()
            if (!project) {
                log.warn("[COMPILER] Cannot compile project: GrailsProject is not set")
                return
            }

            log.info("[COMPILER] Starting full project compilation for: ${project.name}")

            grailsService.progressService.update("Preparing compiler...", 30)

            // Reset incremental context
            previousContext = null
            invalidateCompiler()
            updateCompilerOptions()
            updateClassLoader()

            // Add all source files from the project routed to their respective source set
            def allSources = ServiceUtils.getAllGroovySourceFilesFromProject(project)
            if (allSources) {
                allSources.each { File src ->
                    String uri = src.toURI().toString()
                    SourceSetCompilationState state = getStateForUri(uri)
                    state.addSource(src)
                }
                log.info("[COMPILER] Added ${allSources.size()} source files to compilation units")
                grailsService.progressService.update("Compiling ${allSources.size()} source files...", 40)
            } else {
                log.warn("[COMPILER] No source files found in project")
            }

            isFullCompilation = true
            boolean success = compileDefaultOrTillPhase(determineProjectAnalysisPhase())
            log.info("[COMPILER] Project compilation ${success ? 'successful' : 'completed with errors'}")
            grailsService.progressService.update(success ? "Compilation completed" : "Compilation completed with errors", 60)
        } catch (Exception e) {
            log.error("[COMPILER] Project compilation failed", e)
            grailsService.progressService.error("Compilation failed", e)
        } finally {
            isFullCompilation = false
            compileLock.unlock()
        }
    }

    /**
     * Update compiler options but requires compilation
     * @see CompilerOptions Available Options
     * @param option Available options
     * @return true if options updated
     */
    void updateCompilerOptions(CompilerOptions option = CompilerOptions.DEFAULT) {
        if (!compilerConfig) {
            compilerConfig = new CompilerConfiguration(CompilerConfiguration.DEFAULT)
            log.info("[COMPILER] Compiler Configuration initialized")
        }

        GrailsProject project = getProject()
        // optimization options
        def buildDir = project?.excludeDirectories
            ?.find { it.name == "build" }
        if (buildDir?.exists()) compilerConfig.targetDirectory = buildDir
        else compilerConfig.targetDirectory = option.TARGET_DIRECTORY

        compilerConfig.optimizationOptions.put(CompilerConfiguration.GROOVYDOC, true)

        if (option.SOURCE_ENCODING) compilerConfig.sourceEncoding = option.SOURCE_ENCODING
        if (option.VERBOSE) compilerConfig.verbose = option.VERBOSE
        if (option.DEBUG) compilerConfig.debug = option.DEBUG
        // if (option.TOLERANCE) compilerConfig.tolerance = option.TOLERANCE
        if (option.TARGET_BYTECODE) compilerConfig.targetBytecode = option.TARGET_BYTECODE
    }

    /**
     * Updates the classpath with the dependencies of the Grails project. <br>
     * CONSTRAINT: Must be called when build.gradle is changed or necessary <br>
     * NOTE: Resets the compilation unit and updates ClassGraph discovery.
     */
    CompletableFuture<ScanResult> updateClassLoader() {
        compileLock.lock()
        try {
            GrailsProject project = getProject()
            if (!project) return CompletableFuture.completedFuture(null)

            log.info("[COMPILER] Updating compiler classloaders and source-set states for: ${project.name}")
            sourceSetStates.values().each { it.invalidate() }
            sourceSetStates.clear()
            cachedErrorCollector = null
            lastErrorCollectorUpdate = 0
            previousContext = null

            SourceSetCompilationState mainState = getOrCreateState("main")
            if (project.testDirectories || project.sourceSets?.containsKey("test") || project.getTestClasspathUrls()) {
                getOrCreateState("test")
            }

            try {
                def uri = project.rootDirectory?.toURI()?.toString()
                if (uri && mainState.getClassLoader() != null) {
                    return grailsService.discoveryService.updateClassGraph(uri, mainState.getClassLoader(), grailsService.errorService)
                }
            } catch (Exception e) {
                log.warn("[COMPILER] Failed to initialize ClassGraph: ${e.message}")
            }
            return CompletableFuture.completedFuture(null)
        } finally {
            compileLock.unlock()
        }
    }

    /**
     * Compiles the source file incrementally with its dependent files. </br>
     * REQUIRES: GrailsCompiler must be initialized
     * @param textFile A TextFile that needs to be compiled
     */
    void compileSourceFile(TextFile textFile) {
        if (!textFile) {
            log.warn("[COMPILER] Cannot compile null text file")
            return
        }

        compileLock.lock()
        try {
            log.info("[COMPILER] Incremental compilation trigger: ${textFile.name}")
            if (!compilerConfig) updateCompilerOptions()

            GrailsProject project = getProject()
            SourceSetCompilationState state = getStateForUri(textFile.uri)

            isFullCompilation = false

            Set<TextFile> filesToUpdate = []
            if (textFile.uri.endsWith('.gsp')) {
                filesToUpdate.add(textFile)
            } else {
                filesToUpdate = grailsService.fileTracker?.getFileAndItsDependencies(textFile) ?: [textFile] as Set
            }

            log.info("[COMPILER] Incremental update for ${filesToUpdate.size()} files in scope ${state.sourceSetName}")

            filesToUpdate.each { TextFile file ->
                // Crucial isolation rule: production state MUST NOT admit test files!
                if (state.isMain() && project != null && project.isTestFile(file.uri)) {
                    log.debug("[COMPILER] Skipping test file ${file.name} for main source set")
                    return
                }
                state.addOrUpdateSource(file)
            }

            previousContext = textFile.uri

            int targetPhase = determineProjectAnalysisPhase()
            state.compile(targetPhase)
            state.clearDirty(textFile.uri)
        } catch (Exception e) {
            log.error("[COMPILER] Incremental compilation failed for ${textFile.name}", e)
        } finally {
            compileLock.unlock()
        }
    }

    /**
     * Removes all cached compilation state and coordinates with AST visitor.
     */
    void invalidateCompiler() {
        compileLock.lock()
        try {
            log.info("[COMPILER] Invalidating compiler state")
            sourceSetStates.values().each { it.invalidate() }
            sourceSetStates.clear()
            cachedErrorCollector = null
            lastErrorCollectorUpdate = 0
            previousContext = null
            log.info("[COMPILER] Compiler state invalidated and released")
        } finally {
            compileLock.unlock()
        }
    }

    /**
     * Marks a source file as dirty (needs recompilation).
     * @param uri The URI of the source file
     */
    void markDirty(String uri) {
        if (uri) {
            getStateForUri(uri).markDirty(uri)
            log.debug("[COMPILER] Marked dirty: ${uri}")
        }
    }

    /**
     * Checks if a source file is dirty (needs recompilation).
     * @param uri The URI of the source file
     * @return true if the source is marked dirty
     */
    boolean isDirty(String uri) {
        uri ? getStateForUri(uri).isDirty(uri) : false
    }

    /**
     * Clears dirty flag for a source after successful compilation.
     * @param uri The URI of the source file
     */
    void clearDirty(String uri) {
        if (uri) {
            getStateForUri(uri).clearDirty(uri)
            log.debug("[COMPILER] Cleared dirty: ${uri}")
        }
    }

    /**
     * Clears all dirty flags.
     */
    void clearAllDirty() {
        sourceSetStates.values().each { it.clearAllDirty() }
    }

    /**
     * Gets count of dirty sources pending compilation.
     */
    int getDirtyCount() {
        int count = 0
        sourceSetStates.values().each { count += it.dirtyCount }
        return count
    }

    /**
     * Checks if a compilation unit already exists for the given URI.
     * @param uri The URI to check
     * @return true if a SourceUnit exists in the cache
     */
    boolean compilationExistsFor(String uri) {
        if (!uri) return false
        SourceSetCompilationState state = getStateForUri(uri)
        if (state.compilationExistsFor(uri)) return true
        for (SourceSetCompilationState s : sourceSetStates.values()) {
            if (s != state && s.compilationExistsFor(uri)) return true
        }
        return false
    }

    /**
     * Returns the SourceUnit for a given TextFile, or null.
     */
    SourceUnit getSourceUnit(TextFile textFile) {
        if (!textFile?.uri) return null
        SourceSetCompilationState state = getStateForUri(textFile.uri)
        SourceUnit unit = state.getSourceUnit(textFile)
        if (unit != null) return unit
        for (SourceSetCompilationState s : sourceSetStates.values()) {
            if (s != state) {
                unit = s.getSourceUnit(textFile)
                if (unit != null) return unit
            }
        }
        return null
    }

    String getPatchedSourceUnitText(TextFile textFile) {
        return getSourceUnit(textFile)?.source?.reader?.text ?: textFile.text
    }

    TextFile getPatchedSourceUnitTextFile(TextFile textFile) {
        return TextFile.create(textFile.uri, getPatchedSourceUnitText(textFile))
    }

    List<SourceUnit> getSourceUnits() {
        List<SourceUnit> list = []
        sourceSetStates.values().each { list.addAll(it.getSourceUnits()) }
        return list
    }

    /**
     * Gets the compilation errors if there are any
     * Returns cached error collector, updating when compilation unit changes
     */
    ErrorCollector getErrorCollectorOrNull() {
        for (SourceSetCompilationState state : sourceSetStates.values()) {
            ErrorCollector ec = state.getErrorCollectorOrNull()
            if (ec != null && ec.hasErrors()) {
                cachedErrorCollector = ec
                lastErrorCollectorUpdate = System.currentTimeMillis()
                return ec
            }
        }
        cachedErrorCollector = getMainState().getErrorCollectorOrNull()
        lastErrorCollectorUpdate = System.currentTimeMillis()
        return cachedErrorCollector
    }

    /**
     * Determines and compiles to the correct phase.
     */
    boolean compileDefaultOrTillPhase(int phase = grailsService.config.compilerPhase) {
        GrailsProject project = getProject()
        if (phase == GrailsUtils.DEFAULT_COMPILATION_PHASE) {
            phase = (project != null && project.isGrailsProject)
                ? Phases.CLASS_GENERATION : Phases.CANONICALIZATION
        }

        log.info("[COMPILER] Compiling till phase: ${Phases.getDescription(phase).toUpperCase()}")

        boolean allSuccess = true
        for (SourceSetCompilationState state : sourceSetStates.values()) {
            boolean ok = state.compile(phase)
            if (!ok) allSuccess = false
        }
        return allSuccess
    }

    // Smart analysis phase determination based on project size and user preference.
    private int determineProjectAnalysisPhase() {
        int phase = grailsService.config.compilerPhase
        if (phase != GrailsUtils.DEFAULT_COMPILATION_PHASE) return phase

        if (isFullCompilation) {
            return Phases.CONVERSION
        }

        GrailsProject project = getProject()
        if (project != null && project.sourceFileCount > 100) return Phases.SEMANTIC_ANALYSIS
        return Phases.INSTRUCTION_SELECTION
    }

    void removeSourceFile(String uri) {
        if (!uri) return
        sourceSetStates.values().each { it.removeSourceFile(uri) }
        log.info("[COMPILER] Removed ${uri} from cache and compilation units")
    }

    GroovyClassLoader getClassLoader() {
        return getMainState().getClassLoader()
    }

    ClassLoader getClassLoaderForUri(String uri) {
        return getStateForUri(uri)?.getClassLoader() ?: getClassLoader()
    }

    GrailsCU getCompilationUnit() {
        return getMainState().getCompilationUnit()
    }

    void setCompilationUnit(GrailsCU cu) {
        getMainState().setCompilationUnit(cu)
    }

    /**
     * Resets compilation units and source/error caches while retaining configurations and loaders.
     * The project writer must re-add and compile sources before publishing a new generation.
     * A null or empty scope refreshes all existing states, initializing main if none exist;
     * a named scope that has not been created is left uninitialized.
     */
    void refreshCompilationUnit(String sourceSetName = null) {
        compileLock.lock()
        try {
            if (sourceSetName) {
                SourceSetCompilationState state = sourceSetStates.get(sourceSetName)
                if (state == null) return
                state.refreshCompilationUnit()
            } else if (sourceSetStates.isEmpty()) {
                getMainState().refreshCompilationUnit()
            } else {
                sourceSetStates.values().each { it.refreshCompilationUnit() }
            }
            cachedErrorCollector = null
            lastErrorCollectorUpdate = 0
        } finally {
            compileLock.unlock()
        }
    }
}
