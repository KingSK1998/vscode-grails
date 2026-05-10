package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

import kingsk.grails.lsp.model.ErrorSource
import kingsk.grails.lsp.model.ErrorSeverity

@Slf4j
@CompileStatic
class GrailsYamlIntelligenceProvider extends BaseProvider {

    // Common Grails properties
    private static final Map<String, String> COMMON_PROPERTIES = [
        "grails.mongodb.url": "MongoDB connection URL",
        "grails.mongodb.databaseName": "MongoDB database name",
        "grails.mongodb.connectionString": "Full MongoDB connection string",
        "dataSource.url": "JDBC connection URL",
        "dataSource.driverClassName": "JDBC driver class name",
        "dataSource.username": "Database username",
        "dataSource.password": "Database password",
        "dataSource.dbCreate": "Database auto-creation strategy (create, create-drop, update, validate)",
        "grails.server.port": "Server port",
        "grails.server.host": "Server host",
        "environments.development": "Development environment configuration",
        "environments.test": "Test environment configuration",
        "environments.production": "Production environment configuration",
        "grails.cache.config": "Cache configuration path",
        "grails.mail.host": "Mail server host",
        "grails.mail.port": "Mail server port"
    ]

    private static final List<String> SENSITIVE_KEYS = [
        "password", "secret", "apiKey", "api_key", "token", "credential", "private_key"
    ]

    GrailsYamlIntelligenceProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<Either<List<CompletionItem>, CompletionList>> provideCompletions(TextFile file, Position position) {
        CompletableFuture.supplyAsync {
            if (!isYamlFile(file.uri)) return Either.forLeft([])

            String lineText = file.textAtLine(position.line) ?: ""
            String prefix = ""
            int character = position.character
            if (character > 0 && character <= lineText.length()) {
                def sb = new StringBuilder()
                for (int i = character - 1; i >= 0; i--) {
                    char c = lineText.charAt(i)
                    if (Character.isWhitespace(c) || c == (char) ':' || c == (char) '-') break
                    sb.append(c)
                }
                prefix = sb.reverse().toString()
            }

            List<CompletionItem> items = []
            COMMON_PROPERTIES.each { String key, String desc ->
                if (key.startsWith(prefix) || prefix == "") {
                    def item = new CompletionItem(key)
                    item.kind = CompletionItemKind.Property
                    item.detail = "Grails Property"
                    item.documentation = Either.forLeft(desc)
                    items << item
                }
            }
            Either.forLeft(items)
        }
    }

    CompletableFuture<Hover> provideHover(TextFile file, Position position) {
        CompletableFuture.supplyAsync {
            if (!isYamlFile(file.uri)) return null

            String lineText = file.textAtLine(position.line) ?: ""
            String key = findKeyAtPosition(lineText, position.character)
            if (key && COMMON_PROPERTIES.containsKey(key)) {
                new Hover().with {
                    it.contents = new MarkupContent(MarkupKind.MARKDOWN, "**Grails Property**: `${key}`\n\n${COMMON_PROPERTIES[key]}")
                    it
                }
            } else {
                null
            }
        }
    }

    CompletableFuture<List<? extends Location>> provideDefinition(TextFile file, Position position) {
        CompletableFuture.supplyAsync {
            [] as List<Location>
        }
    }

    /**
     * Flags sensitive information that should be in .env
     */
    void analyzeSensitiveInfo(TextFile file) {
        if (!isYamlFile(file.uri)) return

        List<Diagnostic> diagnostics = []
        try {
            def lines = file.text.readLines()
            lines.eachWithIndex { String line, int idx ->
                SENSITIVE_KEYS.each { String sensitiveKey ->
                    if (line.toLowerCase().contains(sensitiveKey) && line.contains(":")) {
                        def parts = line.split(":", 2)
                        if (parts.length > 1) {
                            String value = parts[1].trim()
                            if (value && !value.startsWith('${') && !value.startsWith('$')) {
                                def d = new Diagnostic().with {
                                    it.range = new Range(new Position(idx, 0), new Position(idx, line.length()))
                                    it.severity = DiagnosticSeverity.Warning
                                    it.message = "Sensitive information '${sensitiveKey}' detected. Consider moving this to a .env file and using \${VARIABLE_NAME}."
                                    it.source = "Grails LSP"
                                    it
                                }
                                diagnostics << d
                            }
                        }
                    }
                }
            }
            _service.diagnostics.publishDiagnostics(file.uri, diagnostics)
        } catch (Exception e) {
            _service.errorService.handleError("Failed to analyze YAML for sensitive info", e, ErrorSource.LANGUAGE_SERVER, ErrorSeverity.WARNING)
        }
    }

    private boolean isYamlFile(String uri) {
        uri.endsWith(".yml") || uri.endsWith(".yaml")
    }

    private String findKeyAtPosition(String line, int character) {
        int colonIdx = line.indexOf(":")
        if (colonIdx != -1 && character <= colonIdx) {
            line.substring(0, colonIdx).trim()
        } else {
            null
        }
    }
}
