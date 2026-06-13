package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.types.TextFile
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import java.util.concurrent.CompletableFuture
import java.util.regex.Pattern

/**
 * Provides semantic highlighting for Grails variables, GSP tags, and i18n keys.
 */
@Slf4j
@CompileStatic
class GrailsSemanticTokensProvider extends BaseProvider {

    private static final List<String> GRAILS_INJECTED_VARS = [
        "params", "request", "response", "session", "flash", 
        "actionName", "controllerName", "viewName", "model", "args",
        "grailsApplication", "applicationContext"
    ]

    GrailsSemanticTokensProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<SemanticTokens> provideSemanticTokens(SemanticTokensParams params) {
        if (!params?.textDocument?.uri) return nullResult()

        String uri = params.textDocument.uri
        def textFile = fileTracker.getTextFile(uri)
        if (!textFile) return nullResult()

        List<Integer> data = []
        int lastLine = 0
        int lastChar = 0

        // Local helper for delta encoding
        def addToken = { int line, int col, int len, int type, int modifiers = 0 ->
            int deltaLine = line - lastLine
            int deltaChar = deltaLine == 0 ? col - lastChar : col

            data << deltaLine
            data << deltaChar
            data << len
            data << type
            data << modifiers

            lastLine = line
            lastChar = col
        }

        List<Map<String, Integer>> tokensToAdd = []

        // 1. i18n Properties (Purple Keys)
        if (uri.endsWith(".properties") && uri.contains("/i18n/")) {
            def lines = textFile.text.split("\\r?\\n", -1)
            lines.eachWithIndex { String line, int i ->
                def trimmed = line.trim()
                if (trimmed && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                    int eqIdx = line.indexOf("=")
                    if (eqIdx != -1) {
                        def key = line.substring(0, eqIdx).trim()
                        int start = line.indexOf(key)
                        if (start != -1 && key.length() > 0) {
                            tokensToAdd << [line: i, col: start, len: key.length(), type: 2]
                        }
                    }
                }
            }
        }

        // 2. GSP Tags and Groovy Expressions
        if (uri.endsWith(".gsp")) {
             def lines = textFile.text.split("\\r?\\n", -1)
             lines.eachWithIndex { String line, int i ->
                 def matcher = Pattern.compile("<(g|asset):([a-zA-Z0-9_]+)").matcher(line)
                 while (matcher.find()) {
                     int start = matcher.start() + 1
                     int len = matcher.group(0).length() - 1
                     tokensToAdd << [line: i, col: start, len: len, type: 1]
                 }
             }
        }

        // 3. Groovy Variables (params, flash, etc.)
        def nodes = visitor.getNodes(TextFile.normalizePath(uri))
        nodes.each { ASTNode node ->
            if (node instanceof VariableExpression) {
                def ve = (VariableExpression) node
                if (GRAILS_INJECTED_VARS.contains(ve.name)) {
                    if (ve.lineNumber > 0 && ve.columnNumber > 0) {
                        tokensToAdd << [line: ve.lineNumber - 1, col: ve.columnNumber - 1, len: ve.name.length(), type: 0]
                    }
                }
            }
        }

        if (tokensToAdd.empty) return CompletableFuture.completedFuture(new SemanticTokens([]))

        // Sort tokens by line then column
        tokensToAdd.sort { Map<String, Integer> a, Map<String, Integer> b ->
            if (a.line != b.line) return a.line <=> b.line
            a.col <=> b.col
        }

        tokensToAdd.each { Map<String, Integer> t ->
            addToken.call(t.line, t.col, t.len, t.type, 0)
        }

        CompletableFuture.completedFuture(new SemanticTokens(data))
    }
}
