package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import kingsk.grails.lsp.utils.ast.ASTUtils
import org.codehaus.groovy.ast.ClassNode
import kingsk.grails.lsp.model.types.TextFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsCodeActionProvider extends BaseProvider {

    GrailsCodeActionProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<List<Either<Command, CodeAction>>> provideCodeActions(CodeActionParams params) {
        List<Either<Command, CodeAction>> actions = []

        // 1. Check for missing service injections (Auto-Dependency Injection)
        params.context.diagnostics.each { diagnostic ->
            if (diagnostic.message.contains("variable") && diagnostic.message.contains("unresolved")) {
                String varName = extractVarName(params.textDocument.uri, diagnostic.range)
                if (varName?.endsWith("Service")) {
                    actions << Either.forRight(createInjectionAction(params.textDocument.uri, varName))
                }
            }
        }

        // 2. Add "Generate Controller Action" if in a controller
        String uri = TextFile.normalizePath(params.textDocument.uri)
        def classNodes = visitor.allClassNodes[uri]
        def currentClass = classNodes?.find { true }

        if (currentClass?.name?.endsWith("Controller")) {
            def action = new CodeAction("Generate index action")
            action.kind = CodeActionKind.QuickFix
            action.command = new Command("Generate index", "grails.generateAction", [params.textDocument.uri, "index"])
            actions << Either.forRight(action)
        }

        CompletableFuture.completedFuture(actions)
    }

    private String extractVarName(String uri, Range range) {
        def textFile = fileTracker.getTextFile(uri)
        if (!textFile) return null

        try {
            def lines = textFile.text.readLines()
            lines[range.start.line].substring(range.start.character, range.end.character)
        } catch (Exception e) {
            null
        }
    }

    private CodeAction createInjectionAction(String uri, String serviceName) {
        CodeAction action = new CodeAction("Inject ${serviceName}")
        action.kind = CodeActionKind.QuickFix
        
        // Create an edit that inserts 'def userService' at the top of the class
        WorkspaceEdit workspaceEdit = new WorkspaceEdit()
        TextEdit textEdit = new TextEdit()
        textEdit.range = new Range(new Position(1, 0), new Position(1, 0)) // Naive: line 1
        textEdit.newText = "    def ${serviceName}\n"
        workspaceEdit.changes = [(uri): [textEdit]]
        
        action.edit = workspaceEdit
        return action
    }
}
