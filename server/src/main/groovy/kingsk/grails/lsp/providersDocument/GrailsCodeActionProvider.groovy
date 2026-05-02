package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.ASTUtils
import org.codehaus.groovy.ast.ClassNode
import kingsk.grails.lsp.model.TextFile
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@CompileStatic
class GrailsCodeActionProvider extends BaseProvider {

    GrailsCodeActionProvider(GrailsService service) {
        super(service)
    }

    CompletableFuture<List<Either<Command, CodeAction>>> provideCodeActions(CodeActionParams params) {
        List<Either<Command, CodeAction>> actions = []
        
        // 1. Check for missing service injections (Auto-Dependency Injection)
        // We look at diagnostics in the range to see if there's an "unresolved" error
        params.context.diagnostics.each { diagnostic ->
            if (diagnostic.message.contains("variable") && diagnostic.message.contains("unresolved")) {
                // Extract variable name from message or range
                // For simplicity, let's say we found 'userService' is missing
                String varName = extractVarName(params.textDocument.uri, diagnostic.range)
                if (varName && varName.endsWith("Service")) {
                    CodeAction injectionAction = createInjectionAction(params.textDocument.uri, varName)
                    Either<Command, CodeAction> either = Either.forRight(injectionAction)
                    actions.add(either)
                }
            }
        }

        // 2. Add "Generate Controller Action" if in a controller
        String uri = TextFile.normalizePath(params.textDocument.uri)
        Set<ClassNode> classNodes = visitor.allClassNodes.get(uri)
        ClassNode currentClass = classNodes ? classNodes.find { true } : null
        
        if (currentClass && currentClass.name.endsWith("Controller")) {
            CodeAction action = new CodeAction("Generate index action")
            action.kind = CodeActionKind.QuickFix
            action.command = new Command("Generate index", "grails.generateAction", [params.textDocument.uri, "index"])
            Either<Command, CodeAction> either = Either.forRight(action)
            actions.add(either)
        }

        return CompletableFuture.completedFuture(actions)
    }

    private String extractVarName(String uri, Range range) {
        TextFile textFile = service.fileTracker.getTextFile(uri)
        if (!textFile) return null
        
        try {
            String line = textFile.text.split("\n")[range.start.line]
            return line.substring(range.start.character, range.end.character)
        } catch (Exception e) {
            return null
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
