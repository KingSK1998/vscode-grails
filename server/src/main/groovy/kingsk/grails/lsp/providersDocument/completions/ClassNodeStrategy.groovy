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
		List<File> allJars = new ArrayList<>()
		
		// 1. Add all project dependency jars
		request.service.project.dependencies.each { dep ->
			if (ServiceUtils.getValidURL(dep.jarFileClasspath)) {
				allJars << dep.jarFileClasspath
			}
		}
		
		// 2. Add JDK jars (from JAVA_HOME/lib)
		def javaHome = request.service.project.javaHome
		if (javaHome?.exists()) {
			def libDir = new File(javaHome, 'lib')
			def classlist = new File(libDir, 'classlist')
			if (classlist.exists()) {
				log.debug("Using classlist!!!")
				
				classlist.eachLine { line ->
					if (line.startsWithAny("@", "#", "jdk/internal/")) return
					if (line.contains('$')) return
					int lastIndex = line.lastIndexOf('/')
					String className = line.substring(lastIndex + 1)
					String packageName = line.substring(0, lastIndex).replace('/', '.')
					addClassNameCompletion(className, packageName)
				}
			} else {
				// 🔁 Fallback to JMODs (Java 9+)
				def jmodsDir = new File(javaHome, 'jmods')
				if (jmodsDir.exists()) {
					log.debug("classlist not found. Falling back to jmods in: ${jmodsDir}")
					jmodsDir.listFiles()?.findAll { it.name.endsWith('.jmod') }?.each { jmodFile ->
						try (def jf = new JarFile(jmodFile)) {
							def entries = jf.entries()
							while (entries.hasMoreElements()) {
								def entry = entries.nextElement()
								def name = entry.name
								if (isSkippableClass(name)) continue
								def className = name.replace('/', '.').replace('.class', '')
								if (className ==~ /.+\$\d+$/) continue
								
								def simpleName = ServiceUtils.getSimpleNameFromFQCN(className)
								if (!simpleName.toLowerCase().startsWith(request.prefix.toLowerCase())) continue
								
								def pkg = ServiceUtils.getPackageNameFromFQCN(className)
								addClassNameCompletion(simpleName, pkg)
							}
						} catch (Exception e) {
							log.warn("Failed to process jmod: ${jmodFile}", e)
						}
					}
				} else {
					log.warn("No classlist or jmods found under JAVA_HOME: ${javaHome}")
				}
			}
		}
		
		// 3. Extract class names
		allJars.findAll { ServiceUtils.getValidURL(it) }.each { jarFile ->
			try (def jar = new JarFile(jarFile)) {
				def entries = jar.entries()
				while (entries.hasMoreElements()) {
					def entry = entries.nextElement()
					def name = entry.name
					if (isSkippableClass(name)) continue
					def className = name.replace('/', '.').replace('.class', '')
					if (className ==~ /.+\$\d+$/) continue // skip anonymous inner classes
					def simpleName = ServiceUtils.getSimpleNameFromFQCN(className)
					if (!simpleName.toLowerCase().startsWith(request.prefix.toLowerCase())) continue
					def pkg = ServiceUtils.getPackageNameFromFQCN(className)
					addClassNameCompletion(simpleName, pkg)
				}
			} catch (Exception e) {
				log.warn("Failed to process jar: ${jarFile}", e)
			}
		}
	}
	
	private static boolean isSkippableClass(String name) {
		return !name.endsWith('.class') || name.contains('$$') ||
				name.endsWith('package-info.class') || name ==~ /.+\$\d+$/
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
