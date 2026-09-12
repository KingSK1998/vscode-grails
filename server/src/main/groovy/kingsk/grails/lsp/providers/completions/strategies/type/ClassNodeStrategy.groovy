package kingsk.grails.lsp.providers.completions.strategies.type

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.services.DiscoveryService
import kingsk.grails.lsp.utils.services.ServiceUtils
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import kingsk.grails.lsp.context.ASTAccessor

@Slf4j
@CompileStatic
class ClassNodeStrategy extends BaseCompletionStrategy {
	
	@Override
	int getPriority() { return 80 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(CompletionRequest request, RequestContext ctx) {
		ASTNode node = request.offsetNode
		if (node instanceof ClassNode) return true
		return isLikelyTypeReference(request, ctx)
	}
	
	private boolean isLikelyTypeReference(CompletionRequest request, RequestContext ctx) {
		if (!request.prefix) return false
		if (!Character.isLetter(request.prefix.charAt(0))) return false
		if (!Character.isUpperCase(request.prefix.charAt(0))) return false
		
		ASTNode node = request.offsetNode
		if (node instanceof ExpressionStatement) {
			// This is rough but covers common cases
			return true
		}
		if (node instanceof VariableExpression && node.accessedVariable instanceof DynamicVariable) {
			return isLikelyTypeReferenceContext(ctx.ast().getParent(node))
		}
		return false
	}
	
	private static boolean isLikelyTypeReferenceContext(ASTNode parent) {
		if (!parent) return false
		return parent instanceof BlockStatement ||
				parent instanceof ExpressionStatement ||
				parent instanceof ReturnStatement ||
				parent instanceof MethodNode ||
				parent instanceof ConstructorNode ||
				parent instanceof ClassNode ||
				parent instanceof DeclarationExpression
	}
	
	@Override
	List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
		List<CompletionItem> completions = []
		if (!request.prefix || request.prefix.length() < 1) return completions
		
		addClassNamesFromProjectSource(ctx, completions)
		addClassNamesFromDependencies(request, ctx, completions)
		
		return completions
	}
	
	private void addClassNamesFromProjectSource(RequestContext ctx, List<CompletionItem> completions) {
		// Use snapshot index as source of truth for project FQCNs
		ctx.snapshot().index().allFqcns.each { fqcn ->
			def name = ServiceUtils.getSimpleNameFromFQCN(fqcn)
			def pkg = ServiceUtils.getPackageNameFromFQCN(fqcn)
			addClassNameCompletion(name, pkg, ctx.uri(), completions)
		}
	}
	
	private void addClassNamesFromDependencies(CompletionRequest request, RequestContext ctx, List<CompletionItem> completions) {
		Set<String> added = new HashSet<>()
		for (CompletionItem existing : completions) {
			added.add(existing.label)
		}

		String prefixLower = request.prefix.toLowerCase()
		for (ClassNode typeNode : DiscoveryService.getAllTypeNodes(ctx.uri())) {
			String simpleName = typeNode.nameWithoutPackage
			if (simpleName.toLowerCase().startsWith(prefixLower) && added.add(simpleName)) {
				addClassNameCompletion(simpleName, typeNode.packageName, ctx.uri(), completions)
			}
		}

		def scanResult = ctx.grailsService()?.discoveryService?.getClassGraphScanResult(ctx.uri())
		if (scanResult) {
			int count = 0
			for (def classInfo : scanResult.allClasses) {
				if (count >= 150) break

				String simpleName = classInfo.simpleName
				if (simpleName.toLowerCase().startsWith(prefixLower) && added.add(simpleName)) {
					addClassNameCompletion(simpleName, classInfo.packageName, ctx.uri(), completions)
					count++
				}
			}
		}
	}
	
	private void addClassNameCompletion(String name, String packageName, String uri, List<CompletionItem> completions) {
		CompletionItem item = new CompletionItem(name)
		item.kind = CompletionItemKind.Class
		item.insertText = name
		item.insertTextFormat = InsertTextFormat.PlainText
		if (packageName) {
			item.detail = packageName
			item.data = [
					fqcn      : "${packageName}.${name}",
					uri       : uri,
					autoImport: true,
					isResolved: false
			]
		}
		completions.add(item)
	}
}
