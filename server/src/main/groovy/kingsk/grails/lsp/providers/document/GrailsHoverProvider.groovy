package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.DocumentationType
import kingsk.grails.lsp.utils.diagnostics.DocumentationHelper
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import kingsk.grails.lsp.index.LocalSymbolInfo
import kingsk.grails.lsp.index.SymbolInfo

import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.diagnostics.DocumentationHelper
import kingsk.grails.lsp.model.enums.DocumentationType

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsHoverProvider extends BaseProvider {

    GrailsHoverProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    CompletableFuture<Hover> provideHover(TextDocumentIdentifier textDocument, Position position) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            try {
                checkCancellation(token)
                if (getConfig().hoverUsesIndex) {
                    def uri = textDocument.uri
                    
                    LocalSymbolInfo local = compilationContext.methodScopeCache.getLocalAt(uri, position)
                    if (local) {
                        log.info("[HOVER] path=index tier=0 kind=local")
                        return new Hover(buildLocalHover(local))
                    }
                    
                    checkCancellation(token)
                    SymbolInfo symbol = compilationContext.projectIndex.snapshot.getSymbolAt(uri, position)
                    if (symbol) {
                        def docs = compilationContext.groovydocCache.getGroovydoc(symbol.descriptor, uri) { (String) null }
                        log.info("[HOVER] path=index tier=${docs ? 0 : 1} kind=symbol")
                        return new Hover(buildSymbolHover(symbol, docs))
                    }
                }

                checkCancellation(token)
                // Fallback to live AST
                log.info("[HOVER] path=ast tier=3 kind=fallback")
                def offsetNode = getNodeAtPosition(textDocument, position)
                if (!offsetNode) {
                    log.debug("[HOVER] No ASTNode found at the specified position.")
                    return new Hover(new MarkupContent(org.eclipse.lsp4j.MarkupKind.MARKDOWN, ""))
                }

                def definitionNode = getDefinitionNode(offsetNode, false) ?: offsetNode
                checkCancellation(token)

                // Use DocumentationHelper for consistent documentation generation
                def documentation = DocumentationHelper.getDocumentation(
                    definitionNode,
                    project?.isGrailsProject ?: false,
                    visitor,
                    DocumentationType.HOVER
                )

                if (!documentation?.value) {
                    log.debug("[HOVER] No hover content found for node type: ${definitionNode.class.simpleName}")
                    return new Hover(new MarkupContent(org.eclipse.lsp4j.MarkupKind.MARKDOWN, ""))
                }

                return new Hover(documentation)
            } finally {
                recordHealth("hover", System.currentTimeMillis() - startTime, true)
            }
        }
    }

    private MarkupContent buildLocalHover(LocalSymbolInfo local) {
        MarkupContent content = new MarkupContent()
        content.kind = org.eclipse.lsp4j.MarkupKind.MARKDOWN
        content.value = "```groovy\n${local.returnType ?: 'def'} ${local.name}\n```"
        return content
    }

    private MarkupContent buildSymbolHover(SymbolInfo symbol, String groovydoc) {
        MarkupContent content = new MarkupContent()
        content.kind = org.eclipse.lsp4j.MarkupKind.MARKDOWN
        StringBuilder sb = new StringBuilder()
        sb.append("```groovy\n")
        if (symbol.modifierFlags > 0) {
            sb.append(java.lang.reflect.Modifier.toString(symbol.modifierFlags)).append(" ")
        }
        
        def type = symbol.fieldType ?: symbol.returnType ?: "def"
        sb.append(type).append(" ")
        sb.append(symbol.name)
        if (symbol.signature) sb.append(symbol.signature)
        sb.append("\n```")

        if (groovydoc) {
            sb.append("\n---\n").append(groovydoc)
        }
        
        content.value = sb.toString()
        return content
    }
}
