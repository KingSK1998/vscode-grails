package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensParams
import java.util.concurrent.CompletableFuture

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
        TextFile textFile = service.fileTracker.getTextFile(uri)
        if (!textFile) return nullResult()

        List<Integer> data = []
        int lastLine = 0
        int lastChar = 0

        // Local helper for delta encoding
        def addToken = { int line, int col, int len, int type, int modifiers = 0 ->
            int deltaLine = line - lastLine
            int deltaChar = deltaLine == 0 ? col - lastChar : col
            
            data.add(deltaLine)
            data.add(deltaChar)
            data.add(len)
            data.add(type)
            data.add(modifiers)
            
            lastLine = line
            lastChar = col
        }

        List<Map<String, Integer>> tokensToAdd = []

        // 1. i18n Properties (Purple Keys)
        if (uri.endsWith(".properties") && uri.contains("/i18n/")) {
            String[] lines = textFile.text.split("\\r?\\n", -1)
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i]
                String trimmed = line.trim()
                if (trimmed && !trimmed.startsWith("#") && !trimmed.startsWith("!")) {
                    int eqIdx = line.indexOf("=")
                    if (eqIdx != -1) {
                        String key = line.substring(0, eqIdx).trim()
                        int start = line.indexOf(key)
                        if (start != -1 && key.length() > 0) {
                            Map<String, Integer> token = new HashMap<>()
                            token.put("line", i)
                            token.put("col", start)
                            token.put("len", key.length())
                            token.put("type", 2)
                            tokensToAdd.add(token)
                        }
                    }
                }
            }
        } 
        
        // 2. GSP Tags and Groovy Expressions
        if (uri.endsWith(".gsp")) {
             String[] lines = textFile.text.split("\\r?\\n", -1)
             for (int i = 0; i < lines.length; i++) {
                 String line = lines[i]
                 // Match <g:something or <asset:something
                 java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("<(g|asset):([a-zA-Z0-9_]+)").matcher(line)
                 while (matcher.find()) {
                     int start = matcher.start() + 1 // skip <
                     int len = matcher.group(0).length() - 1
                     Map<String, Integer> token = new HashMap<>()
                     token.put("line", i)
                     token.put("col", start)
                     token.put("len", len)
                     token.put("type", 1)
                     tokensToAdd.add(token)
                 }
             }
        }

        // 3. Groovy Variables (params, flash, etc.)
        Set<ASTNode> nodes = service.visitor.getNodes(kingsk.grails.lsp.model.TextFile.normalizePath(uri))
        nodes.each { ASTNode node ->
            if (node instanceof VariableExpression) {
                VariableExpression ve = (VariableExpression) node
                if (GRAILS_INJECTED_VARS.contains(ve.name)) {
                    if (ve.lineNumber > 0 && ve.columnNumber > 0) {
                        Map<String, Integer> token = new HashMap<>()
                        token.put("line", ve.lineNumber - 1)
                        token.put("col", ve.columnNumber - 1)
                        token.put("len", ve.name.length())
                        token.put("type", 0)
                        tokensToAdd.add(token)
                    }
                }
            }
        }

        if (tokensToAdd.empty) return CompletableFuture.completedFuture(new SemanticTokens([]))

        // Sort tokens by line then column (MANDATORY for delta encoding)
        tokensToAdd.sort { Map<String, Integer> a, Map<String, Integer> b ->
            if (a.get("line") != b.get("line")) return a.get("line") <=> b.get("line")
            return a.get("col") <=> b.get("col")
        }

        tokensToAdd.each { Map<String, Integer> t ->
            addToken.call(t.get("line"), t.get("col"), t.get("len"), t.get("type"), 0)
        }

        return CompletableFuture.completedFuture(new SemanticTokens(data))
    }
}
