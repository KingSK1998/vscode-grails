package kingsk.grails.lsp.providersWorkspace

import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.*
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
class GrailsWorkspaceSymbolProvider {
	private final GrailsASTVisitor visitor
	private final GrailsProject project
	
	GrailsWorkspaceSymbolProvider(GrailsService service) {
		this.visitor = service.visitor
		this.project = service.project
	}
	
	CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> provideWorkspaceSymbols(String query) {
		log.info("[WORKSPACE SYMBOLS] Providing workspace symbols for query: $query")
		if (!query) return CompletableFuture.completedFuture(Either.forRight([]))
		
		final int LIMIT = 30
		boolean isClassQuery = Character.isUpperCase(query.charAt(0))
		List<WorkspaceSymbol> results = []
		
		visitor.getNodes().each { node ->
			if (results.size() >= LIMIT) return
			
			String name = null
			boolean allowed = false
			
			if (node instanceof ClassNode && isClassQuery) {
				// Match only ClassNodes if query starts with a capital letter
				name = node.nameWithoutPackage
				allowed = true
			} else if (!isClassQuery && isMethodOrFieldOrProperty(node)) {
				// Match only MethodNodes if query starts with a lowercase letter
				name = node.name
				allowed = true
			}
			
			if (!allowed || !name) return
			
			if (matchesCamelCasePrefix(name, query)) {
				String fullURI = visitor.getURI(node)
				if (!fullURI) return
				
				// Normalize to project-relative path
				String relPath = TextFile.normalizePath(project.rootDirectory.toURI().relativize(new File(fullURI).toURI()).toString())
				
				def parentClassNode = GrailsASTHelper.getEnclosingClassNode(node, visitor)
				def symbol = ASTUtils.astNodeToWorkspaceSymbol(node, relPath, parentClassNode)
				if (symbol) results << symbol
			}
		}
		
		log.info("[WORKSPACE SYMBOLS] Workspace symbols provided for query: ${query}")
		return CompletableFuture.completedFuture(Either.forRight(results))
	}
	
	CompletableFuture<WorkspaceSymbol> resolveWorkspaceSymbol(WorkspaceSymbol workspaceSymbol) {
		if (!workspaceSymbol) {
			log.info "[WORKSPACE SYMBOLS] No workspace symbol to resolve ${workspaceSymbol}"
			return CompletableFuture.completedFuture(null)
		}
		log.debug "[WORKSPACE SYMBOLS] Resolving workspace symbol: ${workspaceSymbol.name}"
		
		def eitherLocation = workspaceSymbol.location
		// Get uri either from left or right if it exists
		def uri = eitherLocation?.left?.uri ?: eitherLocation?.right?.uri
		if (!uri) {
			log.info "[WORKSPACE SYMBOLS] No uri found for workspace symbol: ${workspaceSymbol.name}"
			return CompletableFuture.completedFuture(null)
		}
		
		// Find matching node again, just to confirm or enrich data
		def nodes = visitor.getNodes(uri)
		def targetNode = nodes.find { node ->
			def name = node instanceof ClassNode ? node.nameWithoutPackage : node.name
			return name == workspaceSymbol.name
		}
		
		if (targetNode) {
			// Normalize URI to project-relative path
			String relPath = TextFile.normalizePath(project.rootDirectory.toURI().relativize(new File(uri).toURI()).toString())
			def parentClassNode = GrailsASTHelper.getEnclosingClassNode(targetNode, visitor)
			def resolvedSymbol = ASTUtils.astNodeToWorkspaceSymbol(targetNode, relPath, parentClassNode)
			if (resolvedSymbol) return CompletableFuture.completedFuture(resolvedSymbol)
		}
		
		// Fallback: return as-is
		return CompletableFuture.completedFuture(workspaceSymbol)
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
