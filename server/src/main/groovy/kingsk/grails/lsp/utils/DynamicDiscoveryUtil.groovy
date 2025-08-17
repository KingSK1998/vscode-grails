package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Utility for dynamic discovery of methods, keywords, and completions
 * Replaces hardcoded lists with dynamic introspection
 */
@Slf4j
@CompileStatic
class DynamicDiscoveryUtil {
	
	private static final Map<String, List<String>> KEYWORD_CACHE = new ConcurrentHashMap<>()
	private static final Map<Class<?>, List<Method>> METHOD_CACHE = new ConcurrentHashMap<>()
	
	/**
	 * Get Java/Groovy language keywords dynamically
	 */
	static List<String> getLanguageKeywords() {
		return KEYWORD_CACHE.computeIfAbsent('language') {
			// Java keywords
			List<String> keywords = [
					'abstract', 'assert', 'boolean', 'break', 'byte', 'case', 'catch', 'char',
					'class', 'const', 'continue', 'default', 'do', 'double', 'else',
					'enum', 'extends', 'false', 'final', 'finally', 'float', 'for', 'goto',
					'if', 'implements', 'import', 'instanceof', 'int', 'interface', 'long',
					'native', 'new', 'null', 'package', 'private', 'protected', 'public',
					'return', 'short', 'static', 'strictfp', 'super', 'switch', 'synchronized',
					'this', 'throw', 'throws', 'transient', 'true', 'try', 'void', 'volatile', 'while'
			]
			
			// Groovy-specific keywords
			keywords.addAll(['def', 'in', 'as', 'trait'])
			
			return keywords
		}
	}
	
	/**
	 * Get Groovy DefaultGroovyMethods using official Groovy utilities
	 */
	static List<String> getGroovyDefaultMethods() {
		return KEYWORD_CACHE.computeIfAbsent('groovy_default') {
			try {
				// Use Groovy's official MetaClassRegistry to get all available methods
				def metaClassRegistry = groovy.lang.GroovySystem.metaClassRegistry
				def objectMetaClass = metaClassRegistry.getMetaClass(Object)
				
				// Get all methods from DefaultGroovyMethods via MetaClass
				def methods = objectMetaClass.methods.findAll {
					it.declaringClass?.name?.contains('DefaultGroovyMethods')
				}.collect { it.name }.unique()
				
				if (methods.isEmpty()) {
					// Fallback to direct class inspection
					Class<?> dgmClass = Class.forName('org.codehaus.groovy.runtime.DefaultGroovyMethods')
					return getPublicStaticMethodNames(dgmClass)
				}
				
				return methods
			} catch (Exception e) {
				log.warn("Could not access DefaultGroovyMethods via MetaClass: ${e.message}")
				return getGroovyMethodsFallback()
			}
		}
	}
	
	/**
	 * Get Collection methods using official Groovy utilities
	 */
	static List<String> getCollectionMethods() {
		return KEYWORD_CACHE.computeIfAbsent('collection') {
			try {
				// Use GroovyHelperIntegration for comprehensive method discovery
				return GroovyHelperIntegration.getCollectionMethodsWithExtensions()
			} catch (Exception e) {
				log.warn("Could not get collection methods via GroovyHelperIntegration: ${e.message}")
				// Fallback to basic collection methods
				return getPublicMethodNames(Collection.class) + [
					'each', 'eachWithIndex', 'find', 'findAll', 'collect', 'inject', 'sum',
					'flatten', 'any', 'every', 'sort', 'unique', 'groupBy', 'join',
					'min', 'max', 'reverse'
				]
			}
		}
	}
	
	/**
	 * Get Map methods dynamically from java.util.Map
	 */
	static List<String> getMapMethods() {
		return KEYWORD_CACHE.computeIfAbsent('map') {
			List<String> methods = getPublicMethodNames(Map.class)
			
			// Add Groovy map methods
			methods.addAll([
					'each', 'eachWithIndex', 'find', 'findAll', 'collect', 'collectEntries',
					'groupBy', 'subMap', 'withDefault'
			])
			
			return methods.unique()
		}
	}
	
	/**
	 * Get String methods using official Groovy utilities
	 */
	static List<String> getStringMethods() {
		return KEYWORD_CACHE.computeIfAbsent('string') {
			try {
				// Use GroovyHelperIntegration for comprehensive method discovery
				return GroovyHelperIntegration.getStringMethodsWithExtensions()
			} catch (Exception e) {
				log.warn("Could not get string methods via GroovyHelperIntegration: ${e.message}")
				// Fallback to basic string methods
				return getPublicMethodNames(String.class) + [
					'eachLine', 'eachMatch', 'findAll', 'split', 'tokenize', 'padLeft',
					'padRight', 'center', 'reverse', 'capitalize', 'uncapitalize',
					'toInteger', 'toDouble', 'toBoolean', 'isNumber', 'isInteger'
				]
			}
		}
	}
	
	/**
	 * Get Object methods dynamically from java.lang.Object
	 */
	static List<String> getObjectMethods() {
		return KEYWORD_CACHE.computeIfAbsent('object') {
			List<String> methods = getPublicMethodNames(Object.class)
			
			// Add Groovy object methods
			methods.addAll([
					'getMetaClass', 'setMetaClass', 'invokeMethod', 'getProperty',
					'setProperty', 'hasProperty', 'respondsTo', 'use', 'with', 'identity'
			])
			
			return methods.unique()
		}
	}
	
	/**
	 * Get methods for a specific class dynamically
	 */
	static List<String> getMethodsForClass(Class<?> clazz) {
		if (!clazz) return []
		
		return METHOD_CACHE.computeIfAbsent(clazz) {
			clazz.methods.findAll { method ->
				Modifier.isPublic(method.modifiers) &&
						!method.name.startsWith('get') &&
						!method.name.startsWith('set') &&
						!method.name.startsWith('is') &&
						method.name != 'wait' &&
						method.name != 'notify' &&
						method.name != 'notifyAll'
			}
		}.collect { it.name }.unique()
	}
	
	/**
	 * Get methods for a ClassNode using advanced Groovy runtime integration
	 */
	static List<String> getMethodsForClassNode(ClassNode classNode) {
		if (!classNode) return []
		
		try {
			// Use GroovyRuntimeIntegration for context-aware method discovery
			return GroovyRuntimeIntegration.getMethodsForClassNode(classNode)
		} catch (Exception e) {
			log.warn("Could not get methods via GroovyRuntimeIntegration: ${e.message}")
			
			// Fallback to ClassHelper-based discovery
			if (classNode == ClassHelper.STRING_TYPE) {
				return getStringMethods()
			} else if (classNode == ClassHelper.LIST_TYPE || classNode == ClassHelper.SET_TYPE) {
				return getCollectionMethods()
			} else if (classNode == ClassHelper.MAP_TYPE) {
				return getMapMethods()
			} else if (classNode == ClassHelper.OBJECT_TYPE) {
				return getObjectMethods()
			}
			
			try {
				Class<?> clazz = Class.forName(classNode.name)
				return getMethodsForClass(clazz)
			} catch (ClassNotFoundException ex) {
				// Final fallback to AST methods
				return classNode.methods?.collect { it.name }?.unique() ?: []
			}
		}
	}
	
	/**
	 * Check if a ClassNode represents a primitive type using ClassHelper
	 */
	static boolean isPrimitiveType(ClassNode classNode) {
		return classNode && (
				classNode == ClassHelper.boolean_TYPE || classNode == ClassHelper.byte_TYPE ||
						classNode == ClassHelper.char_TYPE || classNode == ClassHelper.short_TYPE ||
						classNode == ClassHelper.int_TYPE || classNode == ClassHelper.long_TYPE ||
						classNode == ClassHelper.float_TYPE || classNode == ClassHelper.double_TYPE ||
						classNode == ClassHelper.VOID_TYPE
		)
	}
	
	/**
	 * Check if a ClassNode represents a collection type using ClassHelper
	 */
	static boolean isCollectionType(ClassNode classNode) {
		return classNode && (
				classNode == ClassHelper.LIST_TYPE || classNode == ClassHelper.SET_TYPE ||
						classNode.implementsInterface(ClassHelper.make(Collection)) ||
						classNode.isDerivedFrom(ClassHelper.make(Collection))
		)
	}
	
	/**
	 * Check if a ClassNode represents a map type using ClassHelper
	 */
	static boolean isMapType(ClassNode classNode) {
		return classNode && (
				classNode == ClassHelper.MAP_TYPE ||
						classNode.implementsInterface(ClassHelper.MAP_TYPE) ||
						classNode.isDerivedFrom(ClassHelper.MAP_TYPE)
		)
	}
	
	/**
	 * Get appropriate methods for a ClassNode based on its type
	 */
	static List<String> getMethodsForType(ClassNode classNode) {
		if (!classNode) return []
		
		if (isPrimitiveType(classNode)) {
			return [] // Primitives don't have methods
		} else if (classNode == ClassHelper.STRING_TYPE) {
			return getStringMethods()
		} else if (isCollectionType(classNode)) {
			return getCollectionMethods()
		} else if (isMapType(classNode)) {
			return getMapMethods()
		} else {
			return getMethodsForClassNode(classNode)
		}
	}
	
	/**
	 * Get primitive type ClassNodes using ClassHelper
	 */
	static List<ClassNode> getPrimitiveTypeNodes() {
		return [
				ClassHelper.boolean_TYPE, ClassHelper.byte_TYPE, ClassHelper.char_TYPE,
				ClassHelper.short_TYPE, ClassHelper.int_TYPE, ClassHelper.long_TYPE,
				ClassHelper.float_TYPE, ClassHelper.double_TYPE, ClassHelper.VOID_TYPE,
				ClassHelper.DYNAMIC_TYPE // Groovy's 'def'
		]
	}
	
	/**
	 * Get primitive type names (for backward compatibility)
	 */
	static List<String> getPrimitiveTypes() {
		return getPrimitiveTypeNodes().collect { it.nameWithoutPackage }
	}
	
	/**
	 * Get common Java type ClassNodes using ClassHelper
	 */
	static List<ClassNode> getJavaLangTypeNodes() {
		return [
				ClassHelper.OBJECT_TYPE, ClassHelper.STRING_TYPE, ClassHelper.CLASS_Type,
				ClassHelper.Number_TYPE, ClassHelper.Integer_TYPE, ClassHelper.Long_TYPE,
				ClassHelper.Double_TYPE, ClassHelper.Float_TYPE, ClassHelper.Boolean_TYPE,
				ClassHelper.Character_TYPE, ClassHelper.Byte_TYPE, ClassHelper.Short_TYPE,
				ClassHelper.make(Math), ClassHelper.make(System), ClassHelper.make(Thread),
				ClassHelper.make(Throwable), ClassHelper.make(Exception),
				ClassHelper.make(RuntimeException), ClassHelper.make(Error),
				ClassHelper.make(StringBuilder), ClassHelper.make(StringBuffer)
		]
	}
	
	/**
	 * Get common Java types from java.lang package (for backward compatibility)
	 */
	static List<String> getJavaLangTypes() {
		return getJavaLangTypeNodes().collect { it.nameWithoutPackage }
	}
	
	/**
	 * Get common Java util type ClassNodes using ClassHelper
	 */
	static List<ClassNode> getJavaUtilTypeNodes() {
		return [
				ClassHelper.LIST_TYPE, ClassHelper.MAP_TYPE, ClassHelper.SET_TYPE,
				ClassHelper.make(Collection), ClassHelper.make(Iterator),
				ClassHelper.make(ArrayList), ClassHelper.make(LinkedList),
				ClassHelper.make(HashMap), ClassHelper.make(LinkedHashMap),
				ClassHelper.make(HashSet), ClassHelper.make(LinkedHashSet),
				ClassHelper.make(Date), ClassHelper.make(Calendar),
				ClassHelper.make(Properties), ClassHelper.make(Random),
				ClassHelper.make(UUID)
		]
	}
	
	/**
	 * Get common Java util types (for backward compatibility)
	 */
	static List<String> getJavaUtilTypes() {
		return getJavaUtilTypeNodes().collect { it.nameWithoutPackage }
	}
	
	/**
	 * Get Groovy-specific type ClassNodes using ClassHelper
	 */
	static List<ClassNode> getGroovyTypeNodes() {
		return [
				ClassHelper.CLOSURE_TYPE, ClassHelper.RANGE_TYPE, ClassHelper.PATTERN_TYPE,
				ClassHelper.GROOVY_OBJECT_TYPE, ClassHelper.METACLASS_TYPE,
				ClassHelper.make(groovy.lang.Binding), ClassHelper.make(groovy.lang.Script)
		]
	}
	
	/**
	 * Create completion items from a list of strings
	 */
	static List<CompletionItem> createCompletionItems(List<String> candidates, String prefix,
	                                                  CompletionItemKind kind, String detail = null) {
		if (!candidates) return []
		
		return candidates.findAll { candidate ->
			!prefix || candidate.toLowerCase().startsWith(prefix.toLowerCase())
		}.collect { candidate ->
			CompletionItem item = new CompletionItem(candidate)
			item.kind = kind
			if (detail) item.detail = detail
			return item
		}
	}
	
	/**
	 * Create completion items from ClassNodes using ClassHelper
	 */
	static List<CompletionItem> createCompletionItemsFromClassNodes(List<ClassNode> classNodes, String prefix,
	                                                                CompletionItemKind kind = CompletionItemKind.Class) {
		if (!classNodes) return []
		
		return classNodes.findAll { classNode ->
			String name = classNode.nameWithoutPackage
			!prefix || name.toLowerCase().startsWith(prefix.toLowerCase())
		}.collect { classNode ->
			CompletionItem item = new CompletionItem(classNode.nameWithoutPackage)
			item.kind = kind
			item.detail = classNode.name // Full qualified name as detail
			
			// Add type-specific documentation
			if (isPrimitiveType(classNode)) {
				item.documentation = "Primitive type: ${classNode.nameWithoutPackage}"
			} else if (classNode.packageName?.startsWith('java.lang')) {
				item.documentation = "Java core type: ${classNode.name}"
			} else if (classNode.packageName?.startsWith('java.util')) {
				item.documentation = "Java utility type: ${classNode.name}"
			} else if (classNode.packageName?.startsWith('groovy.lang')) {
				item.documentation = "Groovy type: ${classNode.name}"
			}
			
			return item
		}
	}
	
	/**
	 * Get all available type ClassNodes (primitives + java.lang + java.util + Groovy)
	 */
	static List<ClassNode> getAllTypeNodes() {
		List<ClassNode> allTypes = []
		allTypes.addAll(getPrimitiveTypeNodes())
		allTypes.addAll(getJavaLangTypeNodes())
		allTypes.addAll(getJavaUtilTypeNodes())
		allTypes.addAll(getGroovyTypeNodes())
		return allTypes.unique()
	}
	
	/**
	 * Get completion items for all available types
	 */
	static List<CompletionItem> getAllTypeCompletions(String prefix) {
		return createCompletionItemsFromClassNodes(getAllTypeNodes(), prefix, CompletionItemKind.Class)
	}
	
	/**
	 * Get all public method names from a class
	 */
	private static List<String> getPublicMethodNames(Class<?> clazz) {
		return clazz.methods.findAll { method ->
			Modifier.isPublic(method.modifiers) &&
					!Modifier.isStatic(method.modifiers) &&
					!method.name.startsWith('get') &&
					!method.name.startsWith('set') &&
					!method.name.startsWith('is') &&
					method.name != 'wait' &&
					method.name != 'notify' &&
					method.name != 'notifyAll' &&
					method.name != 'finalize'
		}.collect { it.name }.unique()
	}
	
	/**
	 * Get all public static method names from a class
	 */
	private static List<String> getPublicStaticMethodNames(Class<?> clazz) {
		return clazz.methods.findAll { method ->
			Modifier.isPublic(method.modifiers) &&
					Modifier.isStatic(method.modifiers) &&
					!method.name.startsWith('get') &&
					!method.name.startsWith('set') &&
					method.name != 'valueOf' &&
					method.name != 'values'
		}.collect { it.name }.unique()
	}
	
	/**
	 * Fallback Groovy methods if DefaultGroovyMethods is not available
	 */
	private static List<String> getGroovyMethodsFallback() {
		return [
				'each', 'eachWithIndex', 'find', 'findAll', 'collect', 'inject', 'sum',
				'any', 'every', 'sort', 'unique', 'groupBy', 'flatten', 'reverse',
				'join', 'split', 'tokenize', 'min', 'max', 'size', 'isEmpty',
				'contains', 'containsAll', 'addAll', 'removeAll', 'retainAll',
				'toList', 'toSet', 'asType', 'is', 'dump', 'inspect', 'printf',
				'print', 'println', 'use', 'with', 'identity', 'sleep'
		]
	}
	
	/**
	 * Clear all caches (for testing or memory management)
	 */
	static void clearCaches() {
		KEYWORD_CACHE.clear()
		METHOD_CACHE.clear()
		log.debug("Cleared all dynamic discovery caches")
	}
}