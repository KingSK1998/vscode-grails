package kingsk.grails.lsp.providers.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.ast.MemberExtractor
import kingsk.grails.lsp.utils.ast.ScopeHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression

/**
 * Base implementation for completion strategies providing common utilities and automatic request lifecycle management.
 */
@Slf4j
@CompileStatic
abstract class BaseCompletionStrategy {
	
	// Request injected via constructor (immutable, thread-safe)
	protected final CompletionRequest request
	
	BaseCompletionStrategy(CompletionRequest request) { this.request = request }
	
	/**
	 * Get the priority of this strategy (higher = more specific)
	 * @return priority value (0-100, where 100 is highest priority)
	 */
	int getPriority() { return 50 }
	
	/**
	 * Get the target of this strategy (OFFSET, PARENT or BOTH)
	 */
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	/**
	 * Check if this strategy can handle the given AST node
	 * @param node The AST node at completion position
	 * @return true if this strategy can provide completions for this node
	 */
	abstract boolean canHandle(ASTNode node)
	
	/**
	 * Provide completions for the given node
	 * @param node The AST node at completion position
	 */
	abstract void provideCompletions(ASTNode node)
	
	// ===== Core AST Helper Methods =====
	
	/**
	 * Get the parent of an AST node safely
	 * @return The parent node, or null if not found
	 */
	protected ASTNode getParentOf(ASTNode node) {
		if (!node) return null
		return request.visitor.getParent(node)
	}
	
	/**
	 * Get the type of an AST node safely
	 * @return ClassNode of the AST node, or null if not found
	 */
	protected ClassNode getTypeOf(ASTNode node) {
		if (!node) return null
		return GrailsASTHelper.getTypeOfNode(node, request.visitor)
	}
	
	// ===== Member Completion Methods =====
	
	/**
	 * Add completions for members of an expression (most common use case)
	 * Automatically determines static vs instance based on expression type
	 */
	protected void addMemberCompletions(Expression expression) {
		if (!expression) return
		def items = MemberExtractor.collectMembers(expression, request.visitor)
		request.addAllCompletions(items)
		
		// Add Groovy dynamic extension methods (e.g. DefaultGroovyMethods)
		ClassNode expressionType = GrailsASTHelper.getTypeOfNode(expression, request.visitor)
		if (expressionType) {
			request.service.discoveryService.getMethodsForType(expressionType).each { String dgmMethod ->
				// don't add if already collected from MemberExtractor
				if (!items.methods.any { it.name == dgmMethod }) {
					org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(dgmMethod)
					item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
					item.detail = 'Groovy Default Method'
					request.addCompletion(item)
				}
			}
		}
	}
	
	/**
	 * Add static class completions for a given type
	 */
	protected void addStaticMemberCompletions(ClassNode classType) {
		if (!classType) return
		def items = MemberExtractor.collectMembers(classType, true, request.getCurrentClass())
		request.addAllCompletions(items)
		
		if (request.isGrailsProject) {
			kingsk.grails.lsp.model.enums.GrailsArtifactType type = kingsk.grails.lsp.utils.grails.GrailsArtefactUtils.getGrailsArtifactType(classType)
			if (type == kingsk.grails.lsp.model.enums.GrailsArtifactType.DOMAIN) {
				kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getGormStaticMethods().each { String m ->
					org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(m)
					item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
					item.detail = 'GORM Static Method'
					request.addCompletion(item)
				}
				
				// Generate basic dynamic finders for each property
				classType.properties.each { org.codehaus.groovy.ast.PropertyNode p ->
					String capitalized = p.name.capitalize()
					['findBy', 'findAllBy', 'countBy', 'existsBy', 'findOrCreateBy'].each { prefix ->
						org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(prefix + capitalized)
						item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
						item.detail = 'GORM Dynamic Finder'
						request.addCompletion(item)
					}
				}
			}
		}
	}
	
	/**
	 * Add instance class completions for a given type
	 */
	protected void addInstanceMemberCompletions(ClassNode classType) {
		if (!classType) return
		def items = MemberExtractor.collectMembers(classType, false, request.getCurrentClass())
		request.addAllCompletions(items)
		
		if (request.isGrailsProject) {
			kingsk.grails.lsp.model.enums.GrailsArtifactType type = kingsk.grails.lsp.utils.grails.GrailsArtefactUtils.getGrailsArtifactType(classType)
			if (type == kingsk.grails.lsp.model.enums.GrailsArtifactType.DOMAIN) {
				kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getGormInstanceMethods().each { String m ->
					org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(m)
					item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
					item.detail = 'GORM Instance Method'
					request.addCompletion(item)
				}
			}
		}
	}
	
	/**
	 * Add only field completions for a type
	 */
	protected void addFieldCompletions(ClassNode classType, boolean statics = false) {
		if (!classType) return
		def fields = MemberExtractor.collectMembers(classType, statics, request.getCurrentClass(),
				MemberExtractor.CollectionType.FIELDS_ONLY)
		request.addAllCompletions(fields)
	}
	
	/**
	 * Add only method completions for a type
	 */
	protected void addMethodCompletions(ClassNode classType, boolean statics = false) {
		if (!classType) return
		def methods = MemberExtractor.collectMembers(classType, statics, request.getCurrentClass(),
				MemberExtractor.CollectionType.METHODS_ONLY)
		request.addAllCompletions(methods)
	}
	
	// ===== Scope Completion Methods =====
	
	/**
	 * Add scope-based completions (variables, parameters, fields in scope)
	 * This is the fallback for most strategies
	 */
	protected void addScopeCompletions(ASTNode offsetNode) {
		if (!offsetNode) return
		def scopeItems = ScopeHelper.collectScopeItems(offsetNode, request.visitor, ScopeHelper.CollectionType.ALL)
		request.addAllCompletions(scopeItems)
	}
	
	/**
	 * Add only local variable completions from scope
	 */
	protected void addLocalVariableCompletions(ASTNode offsetNode) {
		if (!offsetNode) return
		def scopeItems = ScopeHelper.collectScopeItems(offsetNode, request.visitor, ScopeHelper.CollectionType.VARIABLES_ONLY)
		// Only add variables, not fields/methods
		request.addVariableListCompletions(scopeItems.variables)
	}
	
	// ===== Utility Methods =====
	
	/**
	 * Get class members as AST nodes for further processing
	 */
	protected static List<ASTNode> getClassMembersAsASTNodes(ClassNode classType) {
		if (!classType) return []
		def fields = classType.fields as List<ASTNode>
		def methods = classType.methods as List<ASTNode>
		def properties = classType.properties as List<ASTNode>
		return fields + methods + properties
	}
	
	/**
	 * Log debug information about completion strategy execution
	 */
	protected void logDebug(String message, Object... args) {
		if (log.isDebugEnabled()) {
			log.debug("[COMPLETION] ${this.class.simpleName}: ${String.format(message, args)}")
		}
	}
}