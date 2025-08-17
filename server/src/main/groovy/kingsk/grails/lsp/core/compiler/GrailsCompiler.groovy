package kingsk.grails.lsp.core.compiler


import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.compiler.unit.GrailsCU
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.GrailsUtils
import kingsk.grails.lsp.utils.ServiceUtils
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.control.*
import org.codehaus.groovy.control.io.ReaderSource
import org.codehaus.groovy.control.io.StringReaderSource
import org.codehaus.groovy.syntax.SyntaxException

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

/**
 * GrailsCompiler is the main API for compiling Grails project sources programmatically.
 * <p>
 * This class manages the Groovy compilation unit, configuration, and classloader,
 * and provides methods to set up, configure, and execute compilation for a Grails project.
 * <p>
 * Thread Safety: This class is not guaranteed to be thread-safe.
 * <p>
 * Usage Example:
 * <pre>
 *     GrailsCompiler compiler = new GrailsCompiler(service)
 *     compiler.setupCompiler(project, reportingService)
 *     compiler.compile()
 * </pre>
 */
@Slf4j
class GrailsCompiler {
	// Thread-safety lock
	private final ReentrantLock compileLock = new ReentrantLock()
	
	// Compiler Configurations
	private CompilerConfiguration compilerConfig
	private GroovyClassLoader classLoader
	private GrailsCU compilationUnit
	
	// Source unit caches
	private final Map<String, SourceUnit> sourceUnitsCache = new ConcurrentHashMap<>()
	private String previousContext
	
	// Cache last classpath to reuse classloader when unchanged
	private volatile int lastClasspathHash = 0
	
	// Cached error collector for latest compilation state
	private volatile ErrorCollector cachedErrorCollector
	private volatile long lastErrorCollectorUpdate = 0
	
	private final GrailsService grailsService
	
	GrailsCompiler(GrailsService service) {
		this.grailsService = service
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
			if (!grailsService.project) {
				log.warn("[COMPILER] Cannot compile project: GrailsProject is not set")
				return
			}
			
			log.info("[COMPILER] Starting full project compilation for: ${grailsService.project.name}")
			
			// Reset incremental context
			previousContext = null
			invalidateCompiler()
			updateCompilerOptions()
			grailsService.reportingService?.sendProgressReport("Compiler Initialized", 40)
			updateClassLoader()
			grailsService.reportingService?.sendProgressReport("Dependencies Added", 60)
			
			// Add all source files from the project
			def allSources = ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)
			if (allSources) {
				allSources.each { src -> compilationUnit.addSource(src) }
				log.info("[COMPILER] Added ${allSources.size()} source files to compilation unit")
			} else {
				log.warn("[COMPILER] No source files found in project")
			}
			grailsService.reportingService?.sendProgressReport("Sources Files Added", 80)
			
			// Compile with appropriate phase for workspace analysis
			boolean success = compileDefaultOrTillPhase(determineProjectAnalysisPhase())
			log.info("[COMPILER] Project compilation ${success ? 'successful' : 'completed with errors'}")
			// grailsService.reportingService?.sendProgressReport("Project Compilation Complete", 100)
		} catch (Exception e) {
			log.error("[COMPILER] Project compilation failed", e)
			// grailsService.reportingService?.sendProgressReport("Compilation Failed", 100)
		} finally {
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
		
		// optimization options
		def buildDir = grailsService.project.excludeDirectories
				?.find { it.name == "build" }
		if (buildDir?.exists()) compilerConfig.targetDirectory = buildDir
		else compilerConfig.targetDirectory = option.TARGET_DIRECTORY
		
		if (option.SOURCE_ENCODING) compilerConfig.sourceEncoding = option.SOURCE_ENCODING
		if (option.VERBOSE) compilerConfig.verbose = option.VERBOSE
		if (option.DEBUG) compilerConfig.debug = option.DEBUG
		// if (option.TOLERANCE) compilerConfig.tolerance = option.TOLERANCE
		if (option.TARGET_BYTECODE) compilerConfig.targetBytecode = option.TARGET_BYTECODE
	}
	
	/**
	 * Updates the classpath with the dependencies of the Grails project. <br>
	 * CONSTRAINT: Must be called when build.gradle is changed or necessary <br>
	 * NOTE: Resets the compilation unit
	 */
	void updateClassLoader() {
		def urls = grailsService.project.dependencies
				.collect { ServiceUtils.validateClasspathEntry(it.jarFileClasspath) }
				.findAll()
		
		def paths = urls.collect { it.path }
		if (paths.hashCode() == lastClasspathHash && classLoader) {
			log.info("[COMPILER] Classpath unchanged, reusing existing classloader")
			return
		}
		lastClasspathHash = paths.hashCode()
		
		List<String> merged = (compilerConfig.classpath ?: []) + paths
		compilerConfig.setClasspathList(merged.unique())
		
		def urlCl = new URLClassLoader(urls as URL[], this.class.classLoader)
		classLoader = new GroovyClassLoader(urlCl, compilerConfig, true)
		log.info("[COMPILER] Classloader created with ${urls.size()} entries")
		refreshCompilationUnit()
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
			log.info("[COMPILER] Incremental compilation: ${textFile.name}")
			// Ensure compiler is properly initialized
			if (!compilerConfig) updateCompilerOptions()
			if (!classLoader) updateClassLoader()
			if (!compilationUnit) invalidateCompiler()
			
			if (previousContext == textFile.uri) {
				// Same file -> no need to update dependencies
				removeSource(textFile)
				compilationUnit.addSource(textFile.uri, textFile.text)
			} else {
				// Add all dependent files to the compilation unit
				refreshCompilationUnit()
				Set<TextFile> dependentFiles = grailsService.fileTracker?.getFileAndItsDependencies(textFile)
				if (dependentFiles) {
					log.info("[COMPILER] Total ${dependentFiles.size()} files found")
					dependentFiles.each { depFile ->
						if (depFile?.uri && depFile?.text) {
							compilationUnit.addSource(depFile.uri, depFile.text)
							log.debug("[COMPILER] Source file added: ${depFile.name}")
						}
					}
				} else {
					// Fallback: compile just the single file
					compilationUnit.addSource(textFile.uri, textFile.text)
				}
				previousContext = textFile.uri
			}
			
			compileDefaultOrTillPhase()
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
			
			// Clear all cached state first
			sourceUnitsCache.clear()
			cachedErrorCollector = null
			lastErrorCollectorUpdate = 0
			previousContext = null
			
			// Clear compilation unit errors before refresh
			compilationUnit?.clearErrors()
			
			// Create fresh compilation unit
			refreshCompilationUnit()
			
			// Reset classpath hash to force classloader refresh if needed
			lastClasspathHash = 0
			
			log.info("[COMPILER] Compiler state invalidated and reset")
		} finally {
			compileLock.unlock()
		}
	}
	
	/**
	 * Returns the SourceUnit for a given TextFile, or null.
	 */
	SourceUnit getSourceUnit(TextFile textFile) {
		return sourceUnitsCache.get(textFile?.uri)
	}
	
	String getPatchedSourceUnitText(TextFile textFile) {
		return getSourceUnit(textFile)?.source?.reader?.text ?: textFile.text
	}
	
	TextFile getPatchedSourceUnitTextFile(TextFile textFile) {
		return TextFile.create(textFile.uri, getPatchedSourceUnitText(textFile))
	}
	
	List<SourceUnit> getSourceUnits() {
		return sourceUnitsCache.values() as List<SourceUnit>
	}
	
	/**
	 * Gets the compilation errors if there are any
	 * @return ErrorCollector
	 */
	/**
	 * Gets the compilation errors if there are any
	 * Returns cached error collector, updating when compilation unit changes
	 */
	ErrorCollector getErrorCollectorOrNull() {
		if (compilationUnit?.errorCollector != cachedErrorCollector) {
			cachedErrorCollector = compilationUnit?.errorCollector
			lastErrorCollectorUpdate = System.currentTimeMillis()
		}
		return cachedErrorCollector
	}
	
	/**
	 * Determines and compiles to the correct phase.
	 */
	boolean compileDefaultOrTillPhase(int phase = grailsService.config.compilerPhase) {
		if (phase == GrailsUtils.DEFAULT_COMPILATION_PHASE) {
			phase = grailsService.project.isGrailsProject
					? Phases.CLASS_GENERATION : Phases.CANONICALIZATION
		}
		
		log.info("[COMPILER] Compiling till phase: ${Phases.getDescription(phase).toUpperCase()}")
		
		compile(phase)
		return !compilationUnit?.errorCollector?.hasErrors()
	}
	
	//========================= Private Methods =========================//
	
	private void refreshCompilationUnit() {
		compilationUnit = new GrailsCU(compilerConfig, null, classLoader)
		log.info("[COMPILER] Compilation unit initialized")
	}
	
	private void removeSource(TextFile textFile) {
		// if cache is empty, then there is nothing to remove
		if (!sourceUnitsCache.containsKey(textFile.uri)) return
		
		SourceUnit old = sourceUnitsCache.remove(textFile.uri)
		compilationUnit.removeSourceUnit(old)
		log.info("[COMPILER] Removed source unit ${textFile.name}")
	}
	
	private void compile(int phase) {
		if (phase <= 0) return
		compilationUnit.clearErrors()
		try {
			compilationUnit.compile(phase)
			log.info("[COMPILER] Successfully compiled")
		} catch (CompilationFailedException e) {
			log.warn("[COMPILER] Compilation failed with message: ${e.message}")
		} catch (GroovyBugError e) {
			log.warn("[COMPILER] Compilation failed with Groovy bug: ${e.message}")
		} catch (Exception e) {
			log.warn("[COMPILER] Compilation failed with unknown exception: ${e}")
			// Fallback: force build AST if missing
			def preserveError = compilationUnit.errorCollector
			
			boolean patchedAny = false
			// for (sourceUnit in compilationUnit.sourceUnits.toList()) {
			compilationUnit?.sourceUnits?.toList()?.each { sourceUnit ->
				if (sourceUnit.AST != null) return
				
				String original = sourceUnit.source.reader.text
				int lineNumber = 0
				
				def cause = e.getCause()
				if (cause instanceof MultipleCompilationErrorsException) {
					def errorCollector = sourceUnit.errorCollector
					def syntaxErrors = errorCollector.errors.findAll { it instanceof SyntaxException }
					
					if (!syntaxErrors.isEmpty()) {
						def syntaxException = syntaxErrors[0] as SyntaxException
						lineNumber = Math.max(syntaxException.line - 1, 0)
					}
				} else if (e instanceof groovyjarjarantlr4.v4.runtime.InputMismatchException) {
					def token = e.offendingToken
					lineNumber = Math.max(token.line - 1, 0)
				} else {
					return
				}
				
				if (lineNumber < 0) return
				
				// Split into lines and insert patch at end of affected line
				List<String> lines = original.readLines()
				if (lineNumber >= lines.size()) return
				
				String patchText = GrailsUtils.PATTERN_CONSTRUCTOR_CALL.matcher(lines[lineNumber]).matches() ?
						GrailsUtils.DUMMY_COMPLETION_CONSTRUCTOR : GrailsUtils.DUMMY_COMPLETION_IDENTIFIER
				
				lines[lineNumber] += patchText
				String patchedSource = lines.join("\n")
				
				ReaderSource newSource = new StringReaderSource(patchedSource, sourceUnit.configuration)
				sourceUnit.setSource(newSource)
				compilationUnit.removeSourceUnit(sourceUnit)
				compilationUnit.addSource(sourceUnit)
				patchedAny = true
			}
			if (patchedAny) {
				log.warn("[COMPILER] Patch applied for EOL error, recompiling")
				compilationUnit.compile(phase)
			} else {
				log.warn("[COMPILER] No patch applied, cannot recover from compile failure")
			}
			compilationUnit.errorCollector.addCollectorContents(preserveError)
		} finally {
			updateSourceUnitCache()
		}
	}
	
	private void updateSourceUnitCache() {
		if (!compilationUnit) return
		
		// Clear and rebuild cache to prevent memory leaks
		sourceUnitsCache.clear()
		compilationUnit.iterator().forEachRemaining { sourceUnit ->
			def uri = TextFile.normalizePath(sourceUnit.name)
			sourceUnitsCache[uri] = sourceUnit
		}
		log.info("[COMPILER] Source unit cache updated")
	}
	
	// Smart analysis phase determination based on project size and user preference.
	private int determineProjectAnalysisPhase() {
		int phase = grailsService.config.compilerPhase
		// if some phase is set by user then use that phase
		if (phase != GrailsUtils.DEFAULT_COMPILATION_PHASE) return phase
		// if no phase and is big project then use semantic analysis
		if (grailsService.project.sourceFileCount > 100) return Phases.SEMANTIC_ANALYSIS
		// if small project then can use more detailed analysis
		return Phases.INSTRUCTION_SELECTION
	}
	
}