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
            grailsService.progressService.error(errorMessage)
            throw new ResponseErrorException(new ResponseError(ResponseErrorCode.InvalidParams, errorMessage, null))
        }

        // Setup initial workspace folders
        params.workspaceFolders?.each { folder ->
            grailsService.setupWorkspace(folder.uri, true)
        }

        // Store client capabilities for later use
        clientCapabilities.complete(params.getCapabilities())

        // Configure server capabilities
        ServerCapabilities capabilities = new ServerCapabilities().tap {
            textDocumentSync = new TextDocumentSyncOptions().tap {
                openClose = true
                change = TextDocumentSyncKind.Incremental
                save = new SaveOptions(includeText: true)
            }
            hoverProvider = true
            completionProvider = new CompletionOptions().tap {
                resolveProvider = true
                triggerCharacters = ['.', '@', '"', '\'', '<']
            }
            signatureHelpProvider = new SignatureHelpOptions(['(', ','])
            definitionProvider = true
            implementationProvider = true
            referencesProvider = true
            documentSymbolProvider = true
            workspaceSymbolProvider = true
            codeActionProvider = new CodeActionOptions([CodeActionKind.QuickFix, CodeActionKind.Refactor])
            codeLensProvider = new CodeLensOptions(true)
            renameProvider = new RenameOptions(false)
            executeCommandProvider = new ExecuteCommandOptions(["grails.getDependencyGraph", "grails.getGormSql", "grails.discoverTests", "grails.discoverTestsBatch"])
            inlayHintProvider = new InlayHintRegistrationOptions().tap {
                resolveProvider = false
            }
            documentFormattingProvider = true
            foldingRangeProvider = true
            semanticTokensProvider = new SemanticTokensWithRegistrationOptions().tap {
                legend = new SemanticTokensLegend(
                        ['grailsVariable', 'gspTag', 'i18nKey', 'class', 'method', 'property', 'string'],
                        ['injected', 'declaration', 'definition']
                )
                full = true
                range = false
            }

            workspace = new WorkspaceServerCapabilities().tap {
                workspaceFolders = new WorkspaceFoldersOptions().tap {
                    supported = true
                    changeNotifications = true
                }
            }
        }

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
            log.info "[GrailsLanguageServer] Waiting for VS Code to connect to port 5007..."
            try (Socket socket = new ServerSocket(5007).accept()) {
                log.info "[GrailsLanguageServer] Accepted debug connection from ${socket.inetAddress}:${socket.port}"

                InputStream inputStream = socket.getInputStream()
                OutputStream outputStream = socket.getOutputStream()

                // Start the language server; this blocks until server exit or client disconnect
                startGrailsLanguageServer(inputStream, outputStream)
            }
            log.info "[GrailsLanguageServer] Server shutdown gracefully"
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
