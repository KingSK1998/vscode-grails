package kingsk.grails.lsp.core.compiler.unit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.TextFile
import org.codehaus.groovy.ast.CompileUnit
import org.codehaus.groovy.ast.ModuleNode
import org.codehaus.groovy.control.*

import java.security.CodeSource

@Slf4j
@CompileStatic
class GrailsCU extends CompilationUnit {
	
	GrailsCU(CompilerConfiguration config, CodeSource security, GroovyClassLoader classLoader) {
		super(config, security, classLoader)
		this.errorCollector = new MyErrorCollector(config)
	}
	
	/**
	 * Removes the given source unit from the compilation unit
	 * @param sourceUnit
	 * @return Map [generatedClassRemoved, sourceRemoved, astRefreshed]
	 */
	void removeSourceUnit(SourceUnit sourceUnit) {
		if (!sourceUnit) return
		
		log.info("[COMPILER] Removing source unit: ${sourceUnit.name}")
		boolean generatedClassRemoved = removeGeneratedClass(sourceUnit)
		boolean sourceRemoved = removeSource(sourceUnit)
		boolean astRefreshed = refreshAST(sourceUnit)
		
		if (generatedClassRemoved && sourceRemoved && astRefreshed) {
			log.info("[COMPILER] Successfully removed source unit: ${sourceUnit.name}")
		} else {
			log.warn("[COMPILER] Forcefully removing source unit: ${sourceUnit.name}")
			classes.removeAll { it.name == sourceUnit.name }
			queuedSources.removeIf { it.name == sourceUnit.name }
		}
	}
	
	private boolean removeGeneratedClass(SourceUnit sourceUnit) {
		log.info("[COMPILER] Removing generated classes for: ${TextFile.extractFileName(sourceUnit.name)}")
		
		if (!sourceUnit?.AST) {
			log.warn("[COMPILER] Cannot remove generated classes: sourceUnit or AST is null")
			return false
		}
		
		try {
			List<String> oldGeneratedClasses = sourceUnit.AST.classes?.collect { it?.name }?.findAll { it != null } ?: []
			if (oldGeneratedClasses.empty) {
				log.info("[COMPILER] No generated classes to remove")
				return false
			}
			
			boolean removed = classes.removeIf { it?.name in oldGeneratedClasses }
			log.info("[COMPILER] Removed ${oldGeneratedClasses.size()} generated classes: ${oldGeneratedClasses.join(', ')}")
			return removed
		} catch (Exception e) {
			log.error("[COMPILER] Error removing generated classes", e)
			return false
		}
	}
	
	private boolean removeSource(SourceUnit sourceUnit) {
		log.info("[COMPILER] Removing source unit from compilation unit: ${TextFile.extractFileName(sourceUnit.name)}")
		return sources.remove(sourceUnit.name) != null
	}
	
	private boolean refreshAST(SourceUnit sourceUnit) {
		log.info("[COMPILER] Refreshing AST, excluding: ${TextFile.extractFileName(sourceUnit.name)}")
		if (!ast || !sourceUnit) {
			log.warn("[COMPILER] Cannot refresh AST: ast or sourceUnit is null")
			return false
		}
		
		List<ModuleNode> modules = ast.modules ?: []
		int originalSize = modules.size()
		
		// Create new AST with proper validation
		ast = new CompileUnit(classLoader, null, configuration)
		
		int retainedCount = 0
		modules.each { module ->
			if (module && module.context != sourceUnit) {
				try {
					ast.addModule(module)
					retainedCount++
					log.debug("[COMPILER] Retained module: ${TextFile.extractFileName(module.description)}")
				} catch (Exception e) {
					log.warn("[COMPILER] Failed to retain module: ${module?.description}", e)
				}
			}
		}
		
		log.info("[COMPILER] AST refresh complete: retained ${retainedCount}/${originalSize} modules")
		return originalSize != ast.modules.size()
	}
	
	@Slf4j
	private class MyErrorCollector extends ErrorCollector {
		private static final long serialVersionUID = 1L
		
		MyErrorCollector(CompilerConfiguration configuration) {
			super(configuration)
		}
		
		void clearErrors() {
			try {
				int errorCount = errors?.size() ?: 0
				int warningCount = warnings?.size() ?: 0
				if (errors) errors.clear()
				if (warnings) warnings.clear()
				log.info("[COMPILER] Cleared ${errorCount} errors and ${warningCount} warnings from error collector")
			} catch (Exception e) {
				log.warn("[COMPILER] Failed to clear errors from MyErrorCollector", e)
			}
		}
		
		protected void failIfErrors() throws CompilationFailedException {
			// Don't throw exceptions - let compiler continue processing
			// This allows us to get hidden compile and runtime errors
			if (hasErrors()) {
				log.debug("[COMPILER] Compilation errors detected but continuing processing: ${errorCount} errors")
				// Store errors but don't fail - this is intentional for LSP usage
			}
		}
	}
	
	void clearErrors() {
		if (errorCollector instanceof MyErrorCollector) {
			((MyErrorCollector) errorCollector).clearErrors()
		} else if (errorCollector) {
			// Fallback for other error collector types
			try {
				errorCollector.errors?.clear()
				errorCollector.warnings?.clear()
			} catch (Exception e) {
				log.warn("[COMPILER] Failed to clear errors from error collector", e)
			}
		}
	}
	
	List<SourceUnit> getSourceUnits() {
		return sources?.values()?.toList()
	}
}
