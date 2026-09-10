package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.services.WorkspaceManager
import kingsk.grails.lsp.context.ProviderContext
import kingsk.grails.lsp.context.RequestContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.DocumentationType
import kingsk.grails.lsp.utils.diagnostics.DocumentationHelper
import kingsk.grails.lsp.model.types.TextFile
import org.codehaus.groovy.ast.ASTNode
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.TextDocumentIdentifier
import kingsk.grails.lsp.index.LocalSymbolInfo
import kingsk.grails.lsp.index.SymbolInfo
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsHoverProvider extends BaseProvider {

    GrailsHoverProvider(ProviderContext providerContext, WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<Hover> provideHover(TextDocumentIdentifier textDocument, Position position) {
        def token = createCancellationToken(textDocument.uri)
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync {
            RequestContext ctx = null
            try {
                checkCancellation(token)
                ctx = createRequestContext(textDocument.uri)

                if (getConfig().hoverUsesIndex || providerContext.isTier2()) {
                    def uri = textDocument.uri
                    def local = ctx.methodScopeCache()?.getLocalAt(uri, position)
                    if (local) {
                        log.info("[HOVER] path=index tier=0 kind=local")
                        return new Hover(buildLocalHover(local))
                    }
                    
                    checkCancellation(token)
                    SymbolInfo symbol = ctx.snapshot().index().getSymbolAt(uri, position)
                    if (symbol) {
                        def docs = ctx.groovydocCache()?.getGroovydoc(symbol.descriptor, uri) { (String) null }
                        log.info("[HOVER] path=index tier=${docs ? 0 : 1} kind=symbol")
                        return new Hover(buildSymbolHover(symbol, docs))
                    }
                }

                if (providerContext.isTier2()) {
                    log.warn("[HOVER] AST fallback bypassed in Tier 2")
                    return null
                }

                checkCancellation(token)
                log.info("[HOVER] path=ast tier=3 kind=fallback")
                def offsetNode = getNodeAtPosition(ctx, position)
                if (!offsetNode) {
                    log.debug("[HOVER] No ASTNode found at the specified position.")
                    return null
                }

                def definitionNode = getDefinitionNode(offsetNode, ctx, false) ?: offsetNode
                checkCancellation(token)

                def documentation = DocumentationHelper.getDocumentation(
                    definitionNode,
                    ctx.grailsProject()?.isGrailsProject ?: false,
                    (GrailsASTVisitor) ctx.ast(),
                    DocumentationType.HOVER
                )

                if (!documentation || !documentation.value) {
                    log.debug("[HOVER] No hover content found for node type: ${definitionNode.class.simpleName}")
                    return null
                }

                return new Hover(documentation)
            } finally {
                ctx?.close()
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
