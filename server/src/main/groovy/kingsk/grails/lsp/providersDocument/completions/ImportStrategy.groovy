package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.DynamicDiscoveryUtil
import kingsk.grails.lsp.utils.GroovyHelperIntegration
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ImportNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles completions for import statements
 */
@CompileStatic
class ImportStrategy extends BaseCompletionStrategy {
	
	ImportStrategy(CompletionRequest request) { super(request) }
	
	@Override
	int getPriority() { return 95 }
	
	@Override
	CompletionTarget target() { return CompletionTarget.OFFSET }
	
	@Override
	boolean canHandle(ASTNode node) { return node instanceof ImportNode }
	
	/**
	 * Inside import statements like <code>import java.ut</code>
	 * @param node The ImportNode node to complete
	 */
	@Override
	void provideCompletions(ASTNode node) {
		io.github.classgraph.ScanResult scanResult = DynamicDiscoveryUtil.getClassGraphScanResult(request.file.uri)
		if (scanResult) {
			String prefix = request.prefix ?: ""
			int pkgCount = 0
			for (io.github.classgraph.PackageInfo pkgInfo : scanResult.packageInfo) {
				if (pkgCount >= 50) break
				if (pkgInfo.name.startsWith(prefix)) {
					CompletionItem item = new CompletionItem(pkgInfo.name)
					item.kind = CompletionItemKind.Module
					request.addCompletion(item)
					pkgCount++
				}
			}
			
			int clsCount = 0
			for (io.github.classgraph.ClassInfo classInfo : scanResult.allClasses) {
				if (clsCount >= 100) break
				String className = classInfo.name
				String simpleName = classInfo.simpleName
				if (className.startsWith(prefix) || simpleName.startsWith(prefix)) {
					CompletionItem item = new CompletionItem(className)
					item.kind = classInfo.isInterface() ? CompletionItemKind.Interface : 
								(classInfo.isEnum() ? CompletionItemKind.Enum : CompletionItemKind.Class)
					item.detail = classInfo.packageName
					item.insertText = className
					request.addCompletion(item)
					clsCount++
				}
			}
		}

		addPackageCompletions(request)
		addClassCompletions(request)
		if (request.isGrailsProject) {
			addGrailsImportCompletions(request)
		}
	}
	
	private void addPackageCompletions(CompletionRequest request) {
		List<String> commonPackages = [
				'java.lang', 'java.util', 'java.io', 'java.net',
				'groovy.lang', 'groovy.util', 'groovy.transform'
		]
		
		if (request.isGrailsProject) {
			commonPackages.addAll([
					'grails.', 'org.grails.', 'grails.web.', 'grails.gorm.',
					'grails.artefact.', 'grails.plugin.'
			])
		}
		
		commonPackages.each { pkg ->
			CompletionItem item = new CompletionItem(pkg)
			item.kind = CompletionItemKind.Module
			item.detail = 'Package'
			request.addCompletion(item)
		}
	}
	
	private void addClassCompletions(CompletionRequest request) {
		DynamicDiscoveryUtil.getJavaLangTypes().each { cls ->
			CompletionItem item = new CompletionItem(cls)
			item.kind = CompletionItemKind.Class
			item.detail = 'Java Lang Class'
			request.addCompletion(item)
		}
		
		DynamicDiscoveryUtil.getJavaUtilTypes().each { cls ->
			CompletionItem item = new CompletionItem(cls)
			item.kind = CompletionItemKind.Class
			item.detail = 'Java Util Class'
			request.addCompletion(item)
		}
		
		DynamicDiscoveryUtil.getGroovyTypeNodes().each { clsNode ->
			CompletionItem item = new CompletionItem(clsNode.nameWithoutPackage)
			item.kind = CompletionItemKind.Class
			item.detail = 'Groovy Class'
			request.addCompletion(item)
		}
	}
	
	private void addGrailsImportCompletions(CompletionRequest request) {
		List<String> grailsImports = [
				'grails.validation.Validateable',
				'grails.artefact.Controller',
				'grails.artefact.Service',
				'grails.artefact.DomainClass',
				'grails.artefact.TagLib',
				'grails.gorm.transactions.Transactional',
				'grails.util.GrailsNameUtils',
				'grails.core.GrailsApplication',
				'grails.web.api.WebAttributes'
		]
		
		grailsImports.each { cls ->
			CompletionItem item = new CompletionItem(cls)
			item.kind = CompletionItemKind.Class
			item.detail = 'Grails class'
			request.addCompletion(item)
		}
	}
}