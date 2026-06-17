package kingsk.grails.lsp.providers.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.ASTAccessor
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.ast.MemberExtractor
import kingsk.grails.lsp.utils.ast.ScopeHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.Expression
import org.eclipse.lsp4j.CompletionItem
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils

/**
 * Base implementation for completion strategies providing common utilities.
 */
@Slf4j
@CompileStatic
abstract class BaseCompletionStrategy implements CompletionStrategy {
	
	@Override
	int getPriority() { return 50 }

	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	abstract boolean canHandle(CompletionRequest request, RequestContext ctx)
	
	abstract List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx)
	
	// ===== Core AST Helper Methods =====
	
	protected ASTNode getParentOf(ASTNode node, RequestContext ctx) {
		if (!node) return null
		return ctx.ast().getParent(node)
	}
	
	protected ClassNode getTypeOf(ASTNode node, RequestContext ctx) {
		if (!node) return null
		return GrailsASTHelper.getTypeOfNode(node, ctx.compilationContext().visitor)
	}
	
	// ===== Member Completion Methods =====
	
	protected void addMemberCompletions(Expression expression, RequestContext ctx, List<CompletionItem> items) {
		if (!expression) return
		def visitor = ctx.compilationContext().visitor
		def members = MemberExtractor.collectMembers(expression, visitor)
		
		members.properties.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		members.fields.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		members.methods.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		
		ClassNode expressionType = GrailsASTHelper.getTypeOfNode(expression, visitor)
		if (expressionType) {
			ctx.compilationContext().grailsService.discoveryService.getMethodsForType(expressionType).each { String dgmMethod ->
				if (!members.methods.any { it.name == dgmMethod }) {
					CompletionItem item = new CompletionItem(dgmMethod)
					item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
					item.detail = 'Groovy Default Method'
					items.add(item)
				}
			}
		}
	}
	
	protected void addStaticMemberCompletions(ClassNode classType, RequestContext ctx, List<CompletionItem> items) {
		if (!classType) return
		def visitor = ctx.compilationContext().visitor
		def members = MemberExtractor.collectMembers(classType, true, null)
		
		members.properties.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		members.fields.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		members.methods.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		
		if (ctx.grailsProject()?.isGrailsProject) {
			GrailsArtifactType type = GrailsArtefactUtils.getGrailsArtifactType(classType)
			if (type == GrailsArtifactType.DOMAIN) {
				kingsk.grails.lsp.utils.grails.GrailsHelperIntegration.getGormStaticMethods().each { String m ->
					CompletionItem item = new CompletionItem(m)
					item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
					item.detail = 'GORM Static Method'
					items.add(item)
				}
			}
		}
	}

	protected void addScopeCompletions(ASTNode offsetNode, RequestContext ctx, List<CompletionItem> items) {
		if (!offsetNode) return
		def visitor = ctx.compilationContext().visitor
		def scopeItems = ScopeHelper.collectScopeItems(offsetNode, visitor, ScopeHelper.CollectionType.ALL)
		
		scopeItems.variables.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it as ASTNode)) }
		scopeItems.parameters.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		scopeItems.members.properties.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		scopeItems.members.fields.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
		scopeItems.members.methods.each { items.add(kingsk.grails.lsp.utils.completion.CompletionUtil.buildCompletionItem(it)) }
	}

	protected void logDebug(String message, Object... args) {
		if (log.isDebugEnabled()) {
			log.debug("[COMPLETION] ${this.class.simpleName}: ${String.format(message, args)}")
		}
	}
}
