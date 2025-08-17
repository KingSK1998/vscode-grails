package kingsk.grails.lsp

import groovy.util.logging.Slf4j
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.Launcher
import org.eclipse.lsp4j.jsonrpc.ResponseErrorException
import org.eclipse.lsp4j.jsonrpc.messages.ResponseError
import org.eclipse.lsp4j.jsonrpc.messages.ResponseErrorCode
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.*

import java.util.concurrent.CompletableFuture

@Slf4j
class GrailsLanguageServer implements LanguageServer, LanguageClientAware {
	private final CompletableFuture<ClientCapabilities> clientCapabilities
	private GrailsService grailsService
	private CompletableFuture<Void> shutdownFuture
	
	GrailsLanguageServer() {
		this.clientCapabilities = new CompletableFuture<ClientCapabilities>()
		this.shutdownFuture = new CompletableFuture<Void>()
		this.grailsService = new GrailsService()
	}
	
	@Override
	CompletableFuture<InitializeResult> initialize(InitializeParams params) {
		log.info "[GrailsLanguageServer] Initializing Grails Language Server..."
		
		if (!params.workspaceFolders || params.workspaceFolders.isEmpty()) {
			def errorMessage = "[GrailsLanguageServer] Grails Language Server requires a workspace folder to function."
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, errorMessage, null))
		}
		
		String projectDir = params.workspaceFolders[0].uri
		if (!projectDir) {
			def errorMessage = "[GrailsLanguageServer] Not a valid gradle project"
			throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, errorMessage, null))
		}
		
		grailsService.reportingService.sendProgressBegin()
		
		// Setup the workspace with async compile
		grailsService.setupWorkspace(projectDir, true)
		
		// Store client capabilities for later use
		clientCapabilities.complete(params.getCapabilities())
		
		// Configure server capabilities
		ServerCapabilities capabilities = new ServerCapabilities().tap {
			textDocumentSync = TextDocumentSyncKind.Incremental
			hoverProvider = true
			completionProvider = new CompletionOptions().tap {
				resolveProvider = true
				triggerCharacters = ['.', '@', '"', '\''] // Add more as needed
			}
			// Signature Help (method call signature tooltips)
			signatureHelpProvider = new SignatureHelpOptions(['(', ','])
			definitionProvider = true
			implementationProvider = true
			referencesProvider = true
			//			documentHighlightProvider = true
			documentSymbolProvider = true
			workspaceSymbolProvider = true
			//			codeActionProvider = true
			codeLensProvider = new CodeLensOptions(true)
			//			renameProvider = true
			inlayHintProvider = true
			//			semanticTokensProvider = new SemanticTokensWithRegistrationOptions().tap {
			//				legend = new SemanticTokensLegend(
			//						['namespace', 'type', 'class', 'enum', 'interface', 'struct', 'typeParameter', 'parameter', 'variable', 'property', 'enumMember', 'event', 'function', 'method', 'macro', 'keyword', 'modifier', 'comment', 'string', 'number', 'regexp', 'operator'],
			//						['declaration', 'definition', 'readonly', 'static', 'deprecated', 'abstract', 'async', 'modification', 'documentation', 'defaultLibrary']
			//				)
			//				full = true
			//				range = true
			//			}
			diagnosticProvider = new DiagnosticRegistrationOptions().tap {
				identifier = 'grails-diagnostics'
				interFileDependencies = true
				workspaceDiagnostics = true
			}
			
			// documentFormattingProvider = true
			// foldingRangeProvider = true
			// documentLinkProvider = new DocumentLinkOptions(true)
			// executeCommandProvider = new ExecuteCommandOptions(['grails.downloadSource'])
			
			//			workspace = new WorkspaceServerCapabilities().tap {
			//				workspaceFolders = new WorkspaceFoldersOptions().tap {
			//					supported = true
			//					changeNotifications = true
			//				}
			//				fileOperations = new FileOperationsServerCapabilities()
			//			}
		}
		
		grailsService.reportingService.sendProgressEnd()
		
		return CompletableFuture.completedFuture(new InitializeResult(capabilities))
	}
	
	@Override
	CompletableFuture<Object> shutdown() {
		log.info "[GrailsLanguageServer] Shutting down Grails Language Server..."
		shutdownFuture.complete(null)
		return CompletableFuture.completedFuture(null)
	}
	
	@Override
	void exit() {
		log.info "[GrailsLanguageServer] Exiting Grails Language Server..."
		System.exit(shutdownFuture?.isDone() ? 0 : 1)
	}
	
	@Override
	TextDocumentService getTextDocumentService() {
		return grailsService.document
	}
	
	@Override
	WorkspaceService getWorkspaceService() {
		return grailsService.workspace
	}
	
	@Override
	void connect(LanguageClient client) {
		grailsService.connect(client)
	}
	
	CompletableFuture<ClientCapabilities> getClientCapabilities() {
		return clientCapabilities
	}
	
	static void main(String[] args) {
		boolean DEBUG_ON_REMOTE = Boolean.getBoolean("grails.lsp.debug.remote")
		log.info "[GrailsLanguageServer] Starting Grails Language Server in ${DEBUG_ON_REMOTE ? "remote" : "local"} mode"
		
		if (DEBUG_ON_REMOTE) {
			final int MAX_RETRIES = 5
			final int RETRY_DELAY = 3000
			int attempts = 0
			
			ServerSocket serverSocket = new ServerSocket(5007)
			while (attempts < MAX_RETRIES) {
				log.info "[GrailsLanguageServer] Waiting for VS Code to connect to port 5007..."
				Socket socket = serverSocket.accept()
				attempts++
				
				try {
					log.info "[GrailsLanguageServer] Accepted debug connection from ${socket.inetAddress}:${socket.port}"
					InputStream inputStream = socket.getInputStream()
					OutputStream outputStream = socket.getOutputStream()
					boolean clientShutdownRequest = startGrailsLanguageServer(inputStream, outputStream)
					
					if (clientShutdownRequest) {
						log.info "[GrailsLanguageServer] VS Code client requested shutdown. Exiting debug loop"
						break
					}
					
					log.info "[GrailsLanguageServer] VS Code client disconnected. Restarting server for next connection..."
				} catch (Exception e) {
					log.error("[GrailsLanguageServer] Error while handling debug connection: ${e.message}", e)
				} finally {
					socket.close()
				}
				
				if (attempts < MAX_RETRIES) {
					log.info "[GrailsLanguageServer] Retrying in ${RETRY_DELAY / 1000} seconds..."
					Thread.sleep(RETRY_DELAY)
				}
			}
			
			log.info "[GrailsLanguageServer] Max retries reached. Shutting down remote debug server..."
			serverSocket.close()
		} else {
			try {
				startGrailsLanguageServer(System.in, System.out)
			} catch (Exception e) {
				log.error("[GrailsLanguageServer] Failed to start Grails Language Server: ${e.message}", e)
			}
		}
	}
	
	private static boolean startGrailsLanguageServer(InputStream inputStream, OutputStream outputStream) throws Exception {
		GrailsLanguageServer server = new GrailsLanguageServer()
		Launcher<LanguageClient> launcher = LSPLauncher.createServerLauncher(server, inputStream, outputStream)
		server.connect(launcher.remoteProxy)
		
		// Start listening and wait until done
		def listeningFuture = launcher.startListening()
		
		// Wait for shutdownFuture or disconnect
		CompletableFuture<Void> combined = CompletableFuture.runAsync {
			try {
				listeningFuture.get()
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt()
			} catch (Exception e) {
				log.warn("[GrailsLanguageServer] Language client disconnected: ${e.message}")
			}
		}
		
		// Wait for either client shutdown or stream disconnect
		CompletableFuture.anyOf(combined, server.shutdownFuture).get()
		
		// true if shutdown requested by client
		return server.shutdownFuture.isDone()
	}
}
