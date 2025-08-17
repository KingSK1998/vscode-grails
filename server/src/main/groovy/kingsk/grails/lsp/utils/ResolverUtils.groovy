package kingsk.grails.lsp.utils

import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.SourceUnit

import java.util.jar.JarFile

class ResolverUtils {
	
	/**
	 * Resolve all matching classes from explicit and star imports that match the given prefix (e.g., "F").
	 */
	static Set<ClassNode> resolveImportedClassNodes(SourceUnit sourceUnit, String prefix) {
		Set<ClassNode> candidates = new HashSet<>()
		ModuleNode module = sourceUnit.AST
		
		def allImports = (module.imports ?: []) + (module.starImports ?: [])
		
		allImports.each { imp ->
			if (imp.className) {
				def simple = imp.className.tokenize('.')[-1]
				if (simple.startsWith(prefix)) {
					def classNode = resolveClassNode(imp.className, sourceUnit.classLoader)
					if (classNode) candidates << classNode
				}
			} else if (imp.packageName) {
				def classNames = listClassNamesInPackage(imp.packageName)
				classNames.each { name ->
					if (name.startsWith(prefix)) {
						def fqcn = "${imp.packageName}.$name"
						def classNode = resolveClassNode(fqcn, sourceUnit.classLoader)
						if (classNode) candidates << classNode
					}
				}
			}
		}
		
		return candidates
	}
	
	private static ClassNode resolveClassNode(String fqcn, ClassLoader classLoader) {
		try {
			def clazz = Class.forName(fqcn, false, classLoader)
			return ClassHelper.make(clazz)
		} catch (Throwable ignored) {
			return null
		}
	}
	
	private static List<String> listClassNamesInPackage(String pkgName) {
		def result = []
		def path = pkgName.replace('.', '/')
		def resources = Thread.currentThread().contextClassLoader.getResources(path)
		
		while (resources.hasMoreElements()) {
			def resource = resources.nextElement()
			def protocol = resource.protocol
			
			if (protocol == 'file') {
				def dir = new File(URLDecoder.decode(resource.file, "UTF-8"))
				if (dir.exists()) {
					dir.listFiles({ d, name -> name.endsWith('.class') } as FilenameFilter)?.each {
						result << it.name.replace('.class', '')
					}
				}
			} else if (protocol == 'jar') {
				def jarPath = resource.path.substring(5, resource.path.indexOf("!"))
				def jar = new JarFile(URLDecoder.decode(jarPath, "UTF-8"))
				jar.entries().each { entry ->
					def entryName = entry.name
					if (entryName.startsWith(path) && entryName.endsWith(".class")) {
						def className = entryName.substring(path.length() + 1).replace('/', '.').replace('.class', '')
						if (!className.contains('.')) {
							result << className
						}
					}
				}
			}
		}
		
		return result
	}
}
