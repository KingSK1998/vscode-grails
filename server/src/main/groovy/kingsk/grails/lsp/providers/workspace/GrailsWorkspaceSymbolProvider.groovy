package kingsk.grails.lsp.providers.workspace

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.document.BaseProvider
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.*
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
@CompileStatic
class GrailsWorkspaceSymbolProvider extends BaseProvider {
	
	GrailsWorkspaceSymbolProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
	
	CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> provideWorkspaceSymbols(String query) {
		log.info("[WORKSPACE SYMBOLS] Providing workspace symbols for query: $query")
		if (!query) return CompletableFuture.completedFuture(Either.forRight([]))
		
		final int LIMIT = 30
		boolean isClassQuery = Character.isUpperCase(query.charAt(0))
		List<WorkspaceSymbol> results = []
		
		try {
			withReadLock {
				visitor.getNodes().each { ASTNode node ->
					if (results.size() >= LIMIT) return
					
					String name = null
					boolean allowed = false
					
					if (node instanceof ClassNode && isClassQuery) {
						// Match only ClassNodes if query starts with a capital letter
						name = ((ClassNode) node).nameWithoutPackage
						allowed = true
					} else if (!isClassQuery) {
						if (node instanceof MethodNode) {
							name = ((MethodNode) node).name
							allowed = true
						} else if (node instanceof FieldNode) {
							name = ((FieldNode) node).name
							allowed = true
						} else if (node instanceof PropertyNode) {
							name = ((PropertyNode) node).name
							allowed = true
						}
					}
					
					if (!allowed || !name) return
					
					if (matchesCamelCasePrefix(name, query)) {
						String fullURI = visitor.getURI(node)
						if (!fullURI) return
						
						// Normalize to project-relative path
						String relPath = TextFile.normalizePath(project.rootDirectory.toURI().relativize(new File(new URI(fullURI)).toURI()).toString())
						
						ClassNode parentClassNode = GrailsASTHelper.getEnclosingClassNode(node, visitor)
						WorkspaceSymbol symbol = ASTUtils.astNodeToWorkspaceSymbol(node, relPath, parentClassNode)
						if (symbol) results << symbol
					}
				}
			}
			log.info("[WORKSPACE SYMBOLS] Workspace symbols provided for query: ${query}")
			return CompletableFuture.completedFuture(Either.forRight(results))
		} catch (Exception e) {
			log.error("[WORKSPACE SYMBOLS] Failed to provide workspace symbols", e)
			return CompletableFuture.completedFuture(Either.forRight([]))
		}
	}
	
	CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
		if (!workspaceSymbol) {
			log.info "[WORKSPACE SYMBOLS] No workspace symbol to resolve ${workspaceSymbol}"
			return CompletableFuture.completedFuture(null)
		}
		log.debug "[WORKSPACE SYMBOLS] Resolving workspace symbol: ${workspaceSymbol.name}"
		
		def eitherLocation = workspaceSymbol.location
		String uri = null
		if (eitherLocation != null) {
			if (eitherLocation.isLeft() && eitherLocation.getLeft() != null) {
				uri = eitherLocation.getLeft().uri
			} else if (eitherLocation.isRight() && eitherLocation.getRight() != null) {
				uri = eitherLocation.getRight().getUri()
			}
		}
		if (!uri) {
			log.info "[WORKSPACE SYMBOLS] No uri found for workspace symbol: ${workspaceSymbol.name}"
			return CompletableFuture.completedFuture(null)
		}
		
		final String finalUri = uri
		return withReadLock({
			// Find matching node again, just to confirm or enrich data
			Set<ASTNode> nodes = visitor.getNodes(finalUri)
			ASTNode targetNode = nodes.find { ASTNode node ->
				String name = node instanceof ClassNode ? ((ClassNode) node).nameWithoutPackage : (node instanceof MethodNode ? ((MethodNode) node).name : (node instanceof FieldNode ? ((FieldNode) node).name : (node instanceof PropertyNode ? ((PropertyNode) node).name : null)))
				return name == workspaceSymbol.name
			}
			
			if (targetNode) {
				// Normalize URI to project-relative path
				String relPath = TextFile.normalizePath(project.rootDirectory.toURI().relativize(new File(new URI(finalUri)).toURI()).toString())
				ClassNode parentClassNode = GrailsASTHelper.getEnclosingClassNode(targetNode, visitor)
				WorkspaceSymbol resolvedSymbol = ASTUtils.astNodeToWorkspaceSymbol(targetNode, relPath, parentClassNode)
				if (resolvedSymbol) return CompletableFuture.completedFuture(resolvedSymbol)
			}
			
			// Fallback: return as-is
			return CompletableFuture.completedFuture(workspaceSymbol)
		} as groovy.lang.Closure<CompletableFuture<WorkspaceSymbol>>)
	}
	
	static boolean isMethodOrFieldOrProperty(ASTNode node) {
		if (node instanceof MethodNode) return true
		if (node instanceof FieldNode) return true
		if (node instanceof PropertyNode) return true
		return false
	}
	
	static boolean matchesCamelCasePrefix(String name, String query) {
		if (!name || !query) return false
		
		int qi = 0, ni = 0
		while (qi < query.length() && ni < name.length()) {
			char qc = query.charAt(qi)
			char nc = name.charAt(ni)
			
			// Requires exact match
			if (qc == nc) {
				qi++
			} else if (Character.isUpperCase(qc)) {
				// If query wants uppercase and we don't match exactly, break
				while (ni < name.length() && !Character.isUpperCase(name.charAt(ni))) {
					ni++
				}
				if (ni >= name.length() || name.charAt(ni) != qc) return false
				qi++
			}
			ni++
		}
		return qi == query.length()
	}
}
