package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.ServiceUtils
import org.apache.groovy.ast.tools.ClassNodeUtils
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.DeclarationExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat

import java.util.jar.JarFile

@Slf4j
@CompileStatic
class ClassNodeStrategy extends BaseCompletionStrategy {
	
	ClassNodeStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 80 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) {
		// Direct type references (e.g., return types, field types)
		if (node instanceof ClassNode) return true
		return isLikelyTypeReference(node)
	}
	
	private boolean isLikelyTypeReference(ASTNode node) {
		if (!Character.isLetter(request.prefix.charAt(0))) return false
		if (!Character.isUpperCase(request.prefix.charAt(0))) return false
		if (node instanceof ExpressionStatement) {
			return isLikelyTypeReference(node.expression)
		}
		if (node instanceof VariableExpression && node.accessedVariable instanceof DynamicVariable) {
			return isLikelyTypeReferenceContext(getParentOf(node))
		}
		return false
	}
	
	private static boolean isLikelyTypeReferenceContext(ASTNode parent) {
		if (!parent) return false
		return parent instanceof BlockStatement || // method body
				parent instanceof ExpressionStatement || // lone expression (e.g. `Completio`)
				parent instanceof ReturnStatement || // return type usage
				parent instanceof MethodNode || // method return/param types
				parent instanceof ConstructorNode || // constructor signature
				parent instanceof ClassNode || // field or superclass
				parent instanceof DeclarationExpression // variable declaration (e.g., `Completion foo`)
	}
	
	/**
	 * Type/class declarations or references
	 * @param node The ClassNode node to complete
	 */
	@Override
	void provideCompletions(ASTNode node) {
		if (isLikelyTypeReference(node)) {
			// Only suggest for meaningful prefixes
			if (!request.prefix || request.prefix.length() < 1) return
			addClassNamesFromProjectSource()
			addClassNamesFromDependencies()
		}
		
	}
	
	private void addClassNamesFromProjectSource() {
		ClassNodeUtils
		request.service.fileTracker.getFQCNIndex().keySet().each { fqcn ->
			def name = ServiceUtils.getSimpleNameFromFQCN(fqcn)
			def pkg = ServiceUtils.getPackageNameFromFQCN(fqcn)
			addClassNameCompletion(name, pkg)
		}
	}
	
	private void addClassNamesFromDependencies() {
		def uri = request.service?.project?.rootDirectory?.toURI()?.toString()
		def scanResult = kingsk.grails.lsp.utils.DynamicDiscoveryUtil.getClassGraphScanResult(uri)
		if (scanResult) {
			int count = 0
			// Search classes using ClassGraph which already scanned JDK and project dependencies
			for (def classInfo : scanResult.allClasses) {
				if (count >= 150) break // Prevent overloading completion list
				
				String simpleName = classInfo.simpleName
				if (simpleName.toLowerCase().startsWith(request.prefix.toLowerCase())) {
					addClassNameCompletion(simpleName, classInfo.packageName)
					count++
				}
			}
		} else {
			// Fallback placeholder when ClassGraph isn't ready
			log.debug("ClassGraph scan result not available yet.")
		}
	}
	
	
	private void addClassNameCompletion(String name, String packageName = null) {
		CompletionItem item = new CompletionItem(name)
		item.kind = CompletionItemKind.Class
		item.insertText = name
		item.insertTextFormat = InsertTextFormat.PlainText
		if (packageName) {
			item.detail = packageName
			item.data = [
					fqcn      : "${packageName}.${name}",
					uri       : request.file.uri, // Needed for import insert
					autoImport: true,
					isResolved: false
			]
		}
		request.addCompletion(item)
	}
}
