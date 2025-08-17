package kingsk.grails.lsp.services

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.PositionHelper
import kingsk.grails.lsp.utils.ServiceUtils
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class FileContentTracker {
	private final GrailsService grailsService
	private final Map<String, TextFile> trackedFiles = new ConcurrentHashMap<>()
	private final Map<String, TextFile> fQCNToTextFile = new ConcurrentHashMap<>()
	private final Map<String, Set<TextFile>> fileDependencies = new ConcurrentHashMap<>()
	private final Set<String> tempFiles = ConcurrentHashMap.newKeySet()
	// FQCN initialization state
	private volatile boolean isFQCNInitialized = false
	
	FileContentTracker(GrailsService service) {
		grailsService = service
	}
	
	//==========================================================//
	//                     File Tracking                        //
	//==========================================================//
	
	// Handles file open events from LSP client
	TextFile didOpenFile(DidOpenTextDocumentParams params) {
		if (!params?.textDocument?.uri || !params?.textDocument?.text) {
			log.debug("[FILE_TRACKER] Invalid open file parameters")
			return null
		}
		
		TextFile tracked = TextFile.create(params.textDocument.uri, params.textDocument.text)
		tracked.markOpened()
		tracked.version = params.textDocument.version
		trackedFiles[tracked.uri] = tracked
		
		updateFileDependenciesForSourceFile(tracked)
		
		return tracked
	}
	
	// Handles file change events from LSP client
	TextFile didChangeFile(DidChangeTextDocumentParams params) {
		if (!params?.textDocument?.uri || !params?.contentChanges) {
			log.debug("[FILE_TRACKER] Invalid change file parameters")
			return null
		}
		
		String uri = TextFile.normalizePath(params.textDocument.uri)
		TextFile tracked = trackedFiles[uri]
		
		if (!tracked) {
			log.warn "[FILE_TRACKER] File not found for URI: ${params.textDocument.uri}"
			tracked = trackedFiles.computeIfAbsent(uri) {
				TextFile.create(params.textDocument.uri, null)
			}
		}
		
		def oldText = tracked.text ?: ""
		def stringBuilder = new StringBuilder(oldText)
		
		// Sort changes descending by range.start if both have range, so applying from end to start
		params.contentChanges.sort { a, b ->
			if (!a.range || !b.range) return 0
			PositionHelper.COMPARATOR.compare(b.range.start, a.range.start)
		}.each { change ->
			if (change.range) {
				// Incremental change - apply range-based edit
				String currentText = stringBuilder.toString()
				int startOffset = PositionHelper.getOffset(currentText, change.range.start)
				int endOffset = PositionHelper.getOffset(currentText, change.range.end)
				
				if (startOffset >= 0 && endOffset >= 0) {
					stringBuilder.replace(startOffset, endOffset, change.text)
				} else {
					log.warn("[FILE_TRACKER] Invalid offsets for change range: start=$startOffset, end=$endOffset")
				}
			} else {
				// Full document replacement
				stringBuilder.setLength(0)
				stringBuilder.append(change.text)
			}
		}
		
		tracked.text = stringBuilder.toString()
		tracked.markChanged()
		tracked.version = params.textDocument.version
		updateFileDependenciesForSourceFile(tracked)
		
		return tracked
	}
	
	// Handles file close events from LSP client
	TextFile didCloseFile(DidCloseTextDocumentParams params) {
		if (!params?.textDocument?.uri) {
			log.debug("[FILE_TRACKER] Invalid close file parameters")
			return null
		}
		
		String uri = TextFile.normalizePath(params.textDocument.uri)
		TextFile tracked = trackedFiles[uri]
		
		if (!tracked) {
			log.warn("[FILE_TRACKER] File not found for URI: ${params.textDocument.uri}")
			return null
		}
		
		// Mark as close and clean up resources
		tracked.markClosed()
		trackedFiles.remove(tracked.uri)
		fileDependencies.remove(tracked.uri)
		
		// Remove temporary file entries
		if (tempFiles.remove(uri)) {
			fQCNToTextFile.removeAll { it.value.uri == uri }
		}
		
		return tracked
	}
	
	/**
	 * Handles file deletion events.
	 * Unlike closing a file, deletion removes the file from both tracking and the FQCN map.
	 *
	 * @param uri The URI of the deleted file
	 * @return true if the file was successfully removed
	 */
	boolean didDeleteFile(String uri) {
		if (!uri) {
			log.debug("[FILE_TRACKER] Invalid delete file parameter")
			return false
		}
		
		String normalizedUri = TextFile.normalizePath(uri)
		
		// Remove from tracked files if still open
		trackedFiles.remove(normalizedUri)
		fileDependencies.remove(normalizedUri)
		tempFiles.remove(normalizedUri)
		fQCNToTextFile.removeAll { it.value.uri == normalizedUri }
		
		return false
	}
	
	//==========================================================//
	//                    Content Access API                    //
	//==========================================================//
	
	/** Gets a text file by URI or null */
	TextFile getTextFile(String uri) {
		if (!uri) return null
		String normalizedUri = TextFile.normalizePath(uri)
		return trackedFiles[normalizedUri]
	}
	
	String getContent(String uri) {
		if (!uri) return null
		return getTextFile(uri).text
	}
	
	int getNextVersion(String uri) {
		if (!uri) return 0
		int currentVersion = getTextFile(uri).version
		return currentVersion + 1
	}
	
	String getTextAtLine(String uri, int lineNumber) {
		String content = getContent(uri)
		if (!content) return null
		def lines = content.readLines()
		if (lineNumber < 0 || lineNumber >= lines.size()) return ""
		return lines[lineNumber]
	}
	
	/** Gets a file and all its dependencies */
	Set<TextFile> getFileAndItsDependencies(TextFile sourceFile) {
		if (!sourceFile) return Collections.emptySet()
		return fileDependencies.getOrDefault(sourceFile.uri, Collections.emptySet())
	}
	
	/** Gets all currently active (open) text files */
	List<TextFile> getActiveTextFiles() {
		return new ArrayList<>(trackedFiles.values())
	}
	
	/** Gets all project files (both open and from project) */
	List<TextFile> getAllProjectFiles() {
		initializeFQCNIfRequired()
		return new ArrayList<>(fQCNToTextFile.values())
	}
	
	TextFile getFileFromFQCN(String fqcn) {
		if (!fqcn) return null
		return fQCNToTextFile[fqcn] ?: null
	}
	
	/**
	 * Resets all FQCN dependencies and caches.
	 * This should be called when:
	 * 1. Project structure changes (build.gradle changes)
	 * 2. Dependencies change
	 * 3. Source directories change
	 */
	void resetFQCNDependencies() {
		log.info("[FILE_TRACKER] Resetting FQCN dependencies")
		fileDependencies.clear()
		fQCNToTextFile.clear()
		isFQCNInitialized = false
	}
	
	//==========================================================//
	//                    Private Methods                       //
	//==========================================================//
	
	/**
	 * Initializes the FQCN map if required
	 * Uses a read-write lock for thread safety and lazy loading
	 */
	private void initializeFQCNIfRequired() {
		if (isFQCNInitialized) return
		
		try {
			log.info("[FILE_TRACKER] Initializing FQCN map...")
			def sourceFiles = ServiceUtils.getAllGroovySourceFilesFromProject(grailsService.project)
			def fqcnMap = ServiceUtils.generateFQCNFromSourceFiles(sourceFiles)
			fQCNToTextFile.putAll(fqcnMap)
			isFQCNInitialized = true
			log.info("[FILE_TRACKER] FQCN map initialized with ${fQCNToTextFile.size()} entries")
		} catch (Exception e) {
			log.error("[FILE_TRACKER] Failed to initialize FQCN map: ${e.message}", e)
			isFQCNInitialized = false
		}
	}
	
	/**
	 * Updates file dependencies for a source file
	 * @param sourceFile The source file to update dependencies for
	 */
	void updateFileDependenciesForSourceFile(TextFile sourceFile) {
		if (!sourceFile) return
		
		try {
			initializeFQCNIfRequired()
			
			Set<TextFile> dependencies = ServiceUtils.tryToResolveDeepDependentFiles(sourceFile, fQCNToTextFile).toSet()
			if (!fQCNToTextFile.containsValue(sourceFile)) {
				isFQCNInitialized = false
				throw new FileNotFoundException("[FILE_TRACKER] File not found in Project Sources")
			}
			fileDependencies[sourceFile.uri] = dependencies
		} catch (Exception e) {
			log.debug("[FILE_TRACKER] Failed to get all file dependencies for source file ${e.message}")
			handleNonSavedFile(sourceFile)
		}
	}
	
	Map<String, TextFile> getFQCNIndex() {
		return fQCNToTextFile
	}
	
	/**
	 * Handles non-saved files by creating temporary entries
	 * @param sourceFile The non-saved source file
	 */
	@CompileDynamic
	private void handleNonSavedFile(TextFile sourceFile) {
		if (!sourceFile) return
		
		// Extract package and create FQCN
		def matcher = sourceFile.text =~ /(?m)^package\s+([\w.]+)/
		def pkg = matcher.find() ? matcher[0][1] : ""
		def fqcn = pkg ? "${pkg}.${sourceFile.nameWithoutExtension}" : sourceFile.nameWithoutExtension
		
		// Add to FQCN map and mark as temporary
		fQCNToTextFile[fqcn] = sourceFile
		tempFiles << sourceFile.uri
		
		// Resolve dependencies
		Set<TextFile> dependencies = ServiceUtils.tryToResolveDeepDependentFiles(sourceFile, fQCNToTextFile).toSet()
		fileDependencies[sourceFile.uri] = dependencies
		log.debug("[FILE_TRACKER] Added temporary file ${sourceFile.uri} with FQCN ${fqcn}")
	}
}
