package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.position.RangeHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsRenameProvider extends BaseProvider {

    GrailsRenameProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<WorkspaceEdit> provideRename(RenameParams params) {
        Map<String, List<TextEdit>> textEditChanges = [:]
        List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = []
        def workspaceEdit = new WorkspaceEdit(documentChanges)

        String newName = params?.newName
        if (!newName?.trim()) {
            log.warn("[RENAME] Missing newName.")
            return CompletableFuture.completedFuture(workspaceEdit)
        }

        if (!visitor) {
            log.warn("[RENAME] AST is null, returning null.")
            return CompletableFuture.completedFuture(workspaceEdit)
        }

        def documentURI = params.textDocument.uri
        def offsetNode = visitor.getNodeAtPosition(documentURI, params.position)
        if (!offsetNode) {
            log.warn("[RENAME] No ASTNode found at the specified position.")
            return CompletableFuture.completedFuture(workspaceEdit)
        }

        def references = GrailsASTHelper.getReferences(offsetNode, visitor, params.position)
        references.each { node ->
            def uri = visitor.getURI(node) ?: documentURI.toString()
            def contents = getPartialNodeText(uri, node)
            if (!contents) return 
            def range = ASTUtils.astNodeToRange(node)
            if (!range) return 
            
            Position start = range.start
            Position end = range.end
            end.line = start.line
            end.character = start.character + contents.length()

            TextEdit textEdit = null
            if (node instanceof ClassNode) {
                textEdit = createTextEditToRenameClassNode(node as ClassNode, newName, contents, range)
                if (textEdit && !visitor.getParent(node)) {
                    String newURI = uri.toString()
                    int slashIndex = newURI.lastIndexOf("/")
                    int dotIndex = newURI.lastIndexOf(".")
                    newURI = newURI.substring(0, slashIndex + 1) + newName + newURI.substring(dotIndex)

                    documentChanges << Either.<TextDocumentEdit, ResourceOperation>forRight(new RenameFile(oldUri: uri, newUri: newURI))
                }
            } else if (node instanceof MethodNode) {
                textEdit = createTextEditToRenameMethodNode(node as MethodNode, newName, contents, range)
            } else if (node instanceof PropertyNode) {
                textEdit = createTextEditToRenamePropertyNode(node as PropertyNode, newName, contents, range)
            } else if (node instanceof ConstantExpression || node instanceof VariableExpression) {
                textEdit = new TextEdit(range, newName)
            }
            if (!textEdit) return

            String uriKey = uri.toString()
            textEditChanges.computeIfAbsent(uriKey) { [] } << textEdit
        }

        textEditChanges.each { uri, textEdits ->
            def versionedIdentifier = new VersionedTextDocumentIdentifier(uri, null)
            def textDocumentEdit = new TextDocumentEdit(versionedIdentifier, textEdits)
            documentChanges.add(0, Either.forLeft(textDocumentEdit))
        }

        CompletableFuture.completedFuture(workspaceEdit)
    }

    String getPartialNodeText(String uri, ASTNode node) {
        Range range = ASTUtils.astNodeToRange(node)
        if (!range) return null

        String contents = fileTracker.getContent(uri.toString())
        if (contents == null) return null

        RangeHelper.getSubstring(contents, range, 1)
    }

static TextEdit createTextEditToRenameClassNode(ClassNode classNode, String newName, String text, Range range) {
        String className = classNode.nameWithoutPackage
        int dollarIndex = className.indexOf('$')
        if (dollarIndex >= 0) {
            className = className.substring(dollarIndex + 1)
        }

        def classMatcher = (text =~ /class\s+$className\b/)
        if (!classMatcher.find()) return null

        Position start = range.start
        Position end = range.end
        end.character = start.character + classMatcher.end()
        start.character = start.character + classMatcher.start()
        
        new TextEdit(range, newName)
    }

    static TextEdit createTextEditToRenameMethodNode(MethodNode methodNode, String newName, String text, Range range) {
        def methodMatcher = (text =~ /\b${methodNode.name}\b(?=\s*\()/)
        if (!methodMatcher.find()) return null

        Position start = range.start
        Position end = range.end
        end.character = start.character + methodMatcher.end()
        start.character = start.character + methodMatcher.start()
        new TextEdit(range, newName)
    }

    static TextEdit createTextEditToRenamePropertyNode(PropertyNode node, String newName, String text, Range range) {
        def propMatcher = (text =~ /\b${node.name}\b/)
        if (!propMatcher.find()) return null

        Position start = range.start
        Position end = range.end
        end.character = start.character + propMatcher.end()
        start.character = start.character + propMatcher.start()

        new TextEdit(range, newName)
    }
}