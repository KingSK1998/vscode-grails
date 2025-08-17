package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.compiler.GrailsCompiler
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.DiagnosticUtils
import org.codehaus.groovy.control.ErrorCollector
import org.eclipse.lsp4j.*

import java.util.concurrent.CompletableFuture

/** Provides diagnostics for workspace and documents using the Grails compiler. */
@Slf4j
@CompileStatic
class GrailsDiagnosticService {
	private final GrailsCompiler compiler
	private final GrailsService grailsService
	
	private Map<String, Set<Diagnostic>> currentDiagnostics = [:]
	
	GrailsDiagnosticService(GrailsService service) {
		this.compiler = service.compiler
		this.grailsService = service
	}
	
	/**
	 * Provides incremental workspace diagnostics for the entire workspace.
	 * @param identifier The additional identifier provided during registration.
	 * @param previousResultIds The currently known diagnostic reports with their previous result ids.
	 * @return A WorkspaceDiagnosticReport containing updated or unchanged diagnostic reports.
	 */
	CompletableFuture<WorkspaceDiagnosticReport> provideWorkspaceDiagnostics(String identifier, List<PreviousResultId> previousResultIds) {
		if (!compiler.errorCollectorOrNull) {
			return CompletableFuture.completedFuture(new WorkspaceDiagnosticReport([]))
		}
		
		log.debug("[DIAGNOSTICS] Generating workspace diagnostics for $identifier")
		Map<String, Set<Diagnostic>> newDiagnostics = extractDiagnostics(compiler.errorCollectorOrNull)
		
		if (newDiagnostics.isEmpty()) {
			log.info "[DIAGNOSTICS] No diagnostics found for $identifier"
			clearAllDiagnostics()
			return CompletableFuture.completedFuture(new WorkspaceDiagnosticReport([]))
		}
		
		List<WorkspaceDocumentDiagnosticReport> reports = newDiagnostics.collect { uri, diagnosticSet ->
			String newResultId = DiagnosticUtils.computeResultId(diagnosticSet) ?: "empty"
			String oldResultId = DiagnosticUtils.computeResultId(currentDiagnostics[uri]) ?: "empty"
			
			if (!newResultId || diagnosticSet?.empty) {
				currentDiagnostics.remove(uri)
				return null
			}
			
			if (newResultId == oldResultId) {
				// Diagnostics haven't changed from previous report
				return new WorkspaceDocumentDiagnosticReport(
						new WorkspaceUnchangedDocumentDiagnosticReport(newResultId, uri, 1)
				)
			}
			
			log.debug "[DIAGNOSTICS] Diagnostics created for $uri"
			currentDiagnostics[uri] = diagnosticSet
			def fullReport = new WorkspaceFullDocumentDiagnosticReport(diagnosticSet.toList(), uri, 1)
			fullReport.resultId = newResultId
			return new WorkspaceDocumentDiagnosticReport(fullReport)
		}.findAll()
		
		log.debug "[DIAGNOSTICS] Workspace diagnostics completed with ${reports.size()} reports"
		return CompletableFuture.completedFuture(new WorkspaceDiagnosticReport(items: reports))
	}
	
	/**
	 * Provides file-level diagnostics for the given text document.
	 * @param textDocument The text document.
	 * @param identifier The additional identifier provided during registration.
	 * @param previousResultIds The result id of a previous response if provided.
	 * @return A DocumentDiagnosticReport containing updated or unchanged diagnostic reports.
	 */
	CompletableFuture<DocumentDiagnosticReport> provideDocumentDiagnostics(TextDocumentIdentifier textDocument, String identifier, String previousResultId) {
		if (!compiler.errorCollectorOrNull) {
			return CompletableFuture.completedFuture(new DocumentDiagnosticReport(
					new RelatedFullDocumentDiagnosticReport(resultId: "empty", items: [])
			))
		}
		
		String uri = TextFile.normalizePath(textDocument.uri)
		log.debug "[DIAGNOSTICS] Running diagnostics for document: $uri"
		
		// Extract fresh diagnostics
		log.debug("[DIAGNOSTICS] Generating diagnostics for $uri")
		Set<Diagnostic> newDiagnostics = extractDiagnostics(compiler.errorCollectorOrNull).getOrDefault(uri, [] as Set)
		String newResultId = DiagnosticUtils.computeResultId(newDiagnostics) ?: "empty"
		String oldResultId = DiagnosticUtils.computeResultId(currentDiagnostics[uri] ?: [] as Set) ?: "empty"
		
		if (!newResultId || newDiagnostics?.empty) {
			currentDiagnostics.remove(uri)
			def emptyReport = new RelatedFullDocumentDiagnosticReport(resultId: "empty", items: [])
			return CompletableFuture.completedFuture(new DocumentDiagnosticReport(emptyReport))
		}
		
		// Check if diagnostics have changed
		if (newResultId == oldResultId) {
			def unchanged = new RelatedUnchangedDocumentDiagnosticReport(resultId: newResultId)
			return CompletableFuture.completedFuture(new DocumentDiagnosticReport(unchanged))
		}
		
		// Update diagnostics
		currentDiagnostics[uri] = newDiagnostics
		log.debug "[DIAGNOSTICS] Diagnostics updated for $uri"
		def fullReport = new RelatedFullDocumentDiagnosticReport(resultId: newResultId, items: newDiagnostics.toList())
		return CompletableFuture.completedFuture(new DocumentDiagnosticReport(fullReport))
	}
	
	/**
	 * Publishes diagnostics for a specific file immediately
	 * @param uri The URI of the file to publish diagnostics for
	 */
	void publishDiagnosticsForFile(String uri) {
		if (!grailsService.client) {
			log.debug "[DIAGNOSTICS] No client connected, skipping diagnostic publishing for ${uri}"
			return
		}
		
		try {
			def diagnosticReport = provideDocumentDiagnostics(
					new TextDocumentIdentifier(uri), null, null
			).get()
			
			if (diagnosticReport.getLeft()?.items) {
				grailsService.client.publishDiagnostics(new PublishDiagnosticsParams(uri, diagnosticReport.getLeft().items))
				log.debug "[DIAGNOSTICS] Published ${diagnosticReport.getLeft().items.size()} diagnostics for ${uri}"
			} else {
				// Clear diagnostics if no issues found
				grailsService.client.publishDiagnostics(new PublishDiagnosticsParams(uri, []))
				log.debug "[DIAGNOSTICS] Cleared diagnostics for ${uri}"
			}
		} catch (Exception e) {
			log.warn "[DIAGNOSTICS] Failed to publish diagnostics for ${uri}: ${e.message}"
		}
	}
	
	/**
	 * Clears diagnostics for a specific file
	 * @param uri The URI of the file to clear diagnostics for
	 */
	void clearDiagnosticsForFile(String uri) {
		currentDiagnostics.remove(uri)
		if (grailsService.client) {
			grailsService.client.publishDiagnostics(new PublishDiagnosticsParams(uri, []))
			log.debug "[DIAGNOSTICS] Cleared diagnostics for ${uri}"
		}
	}
	
	/**
	 * Clears all diagnostics cache
	 */
	void clearAllDiagnostics() {
		log.debug "[DIAGNOSTICS] Clearing all diagnostics"
		currentDiagnostics.clear()
	}
	
	/** Extract diagnostics from compiler's errorCollector and maps them by URI */
	static Map<String, Set<Diagnostic>> extractDiagnostics(ErrorCollector errorCollector) {
		// Process errors
		Map<String, Set<Diagnostic>> diagnostics = [:]
		errorCollector.errors?.each { error ->
			Diagnostic diagnostic = DiagnosticUtils.errorToDiagnostic(error)
			if (!diagnostic) return
			diagnostics.computeIfAbsent(diagnostic.source, { new HashSet<Diagnostic>() }) << diagnostic
		}
		
		// Process warnings
		errorCollector.warnings?.each { warning ->
			Diagnostic diagnostic = DiagnosticUtils.warningToDiagnostic(warning)
			if (!diagnostic) return
			diagnostics.computeIfAbsent(diagnostic.source, { new HashSet<Diagnostic>() }) << diagnostic
		}
		
		log.debug("[DIAGNOSTICS] Extracted diagnostics across ${diagnostics.size()} files")
		return diagnostics
	}
}
