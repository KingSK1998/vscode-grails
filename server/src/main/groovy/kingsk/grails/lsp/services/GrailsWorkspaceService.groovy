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
	private final ProgressReportService reportingService
	
	GrailsWorkspaceService(GrailsService grailsService) {
		this.grailsService = grailsService
		this.reportingService = grailsService.reportingService
	}
	
	@Override
	void didChangeConfiguration(DidChangeConfigurationParams params) {
		log.info "[WORKSPACE] Configuration changed: ${params.settings}"
		
		try {
			def settings = params.settings
			if (settings instanceof JsonObject && settings.has(GrailsUtils.GRAILS_LSP)) {
				def config = settings.getAsJsonObject(GrailsUtils.GRAILS_LSP)
				grailsService.config.updateFromClient(config)
				reportingService.notifyMessage("[WORKSPACE] Grails LSP configuration updated successfully")
				
				if (grailsService.config.shouldRecompileOnConfigChange) {
					log.info "[WORKSPACE] Recompiling project due to configuration change"
					// Invalidate and async compile
					grailsService.invalidateAll()  // Coordinated invalidation
					CompletableFuture.runAsync({
						grailsService.compiler.compileProject()
					})
				} else {
					log.warn "[WORKSPACE] No '${GrailsUtils.GRAILS_LSP}' configuration found"
				}
			}
		} catch (Exception e) {
			log.error "[WORKSPACE] Failed to apply configuration changes", e
			reportingService.notifyError("[WORKSPACE] Failed to apply Grails LSP configuration", e)
		}
	}
	
	@Override
	void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
		log.info "[WORKSPACE] Watched files changed: ${params.changes}"
		
		// If key Gradle files changed, clear caches and rebuild project
		boolean requiresRebuild = params.changes.any { event ->
			event.uri.endsWith("build.gradle") || event.uri.endsWith("settings.gradle")
		}
		
		if (requiresRebuild) {
			log.info "[WORKSPACE] Gradle build files changed - invalidating cache and rebuilding workspace"
			grailsService.gradle.invalidateCache()
			grailsService.invalidateAll()  // Coordinated invalidation
			// Re-setup workspace asynchronously to recompile and refresh caches
			CompletableFuture.runAsync {
				grailsService.setupWorkspace(grailsService.project.rootDirectory.toURI().toString(), true)
			}
		}
		
		// Handle source file events individually for incremental compilation
		//		params.changes.each { fileEvent ->
		//			try {
		//				switch (fileEvent.type) {
		//					case FileChangeType.Created: return handleFileCreated(fileEvent.uri)
		//					case FileChangeType.Changed: return handleFileChanged(fileEvent.uri)
		//					case FileChangeType.Deleted: return handleFileDeleted(fileEvent.uri)
		//				}
		//			} catch (Exception e) {
		//				log.error "Error handling file change for ${fileEvent.uri}", e
		//				reportingService.notifyError("File change handling failed", e)
		//			}
		//		}
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
		super.didChangeWorkspaceFolders(params)
	}
	
	@Override
	CompletableFuture<WorkspaceDiagnosticReport> diagnostic(WorkspaceDiagnosticParams params) {
		return grailsService.diagnostics.provideWorkspaceDiagnostics(params.identifier, params.previousResultIds)
	}
	
	// Individual file event handlers trigger incremental compilation actions
	
	private void handleFileCreated(String uri) {
		log.info "[WORKSPACE] File created: ${uri}"
		//		def file = grailsService.fileTracker.getTextFile(uri)
		//		file.markOpened()
		
		// Queue incremental compilation of the new file
		//		CompletableFuture.runAsync {
		//			grailsService.grailsCompiler.compileSourceFile(file)
		//		}
	}
	
	private void handleFileChanged(String uri) {
		log.info "[WORKSPACE] File changed: ${uri}"
		//		def file = grailsService.fileTracker.getTextFile(uri)
		//		file.markChanged()
		
		// Queue incremental compilation of the new file
		//		CompletableFuture.runAsync {
		//			grailsService.grailsCompiler.compileSourceFile(file)
		//		}
	}
	
	private void handleFileDeleted(String uri) {
		log.info "[WORKSPACE] File deleted: ${uri}"
		//		def file = grailsService.fileTracker.getTextFile(uri)
		//		file.markClosed()
		
		// Queue incremental compilation of the new file
		//		CompletableFuture.runAsync {
		//			grailsService.grailsCompiler.compileSourceFile(file)
		//		}
	}
}
