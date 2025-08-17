package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.reflection.CachedClass
import org.codehaus.groovy.reflection.ReflectionCache
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.MetaClassHelper
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Advanced integration with Groovy runtime and reflection classes
 * Uses org.codehaus.groovy.runtime package for context-aware completions
 *
 * Key classes used:
 * - DefaultGroovyMethods: All DGM methods
 * - MetaClassHelper: Method resolution
 * - ReflectionCache: Cached reflection data
 * - CachedClass: Optimized class reflection
 * - DefaultTypeTransformation: Type coercion methods
 */
@Slf4j
@CompileStatic
class GroovyRuntimeIntegration {
	
	private static final Map<String, Object> CACHE = new ConcurrentHashMap<>()
	
	/**
	 * Get context-aware methods for a specific type using Groovy runtime
	 * This resolves the correct DGM methods for the given type
	 */
	static List<String> getContextAwareMethods(Class<?> targetType) {
		String cacheKey = "context_methods_${targetType.name}".toString()
		return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
			try {
				List<String> methods = []
				
				// Get CachedClass for optimized reflection
				CachedClass cachedClass = ReflectionCache.getCachedClass(targetType)
				
				// Get all methods including DGM methods
				cachedClass.methods.each { method ->
					if (method.isPublic() && !method.isSynthetic()) {
						methods << method.name
					}
				}
				
				// Get DefaultGroovyMethods that apply to this type
				methods.addAll(getDefaultGroovyMethodsForType(targetType))
				
				return methods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get context-aware methods for ${targetType.name}: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Get DefaultGroovyMethods that apply to a specific type
	 * Uses Groovy's method resolution to find applicable DGM methods
	 */
	static List<String> getDefaultGroovyMethodsForType(Class<?> targetType) {
		String cacheKey = "dgm_${targetType.name}".toString()
		return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
			try {
				List<String> dgmMethods = []
				
				// Get all DefaultGroovyMethods
				Method[] allDgmMethods = DefaultGroovyMethods.class.methods
				
				allDgmMethods.each { method ->
					if (Modifier.isStatic(method.modifiers) &&
							Modifier.isPublic(method.modifiers) &&
							method.parameterCount > 0) {
						
						// Check if first parameter is compatible with target type
						Class<?> firstParamType = method.parameterTypes[0]
						if (isTypeCompatible(firstParamType, targetType)) {
							dgmMethods << method.name
						}
					}
				}
				
				return dgmMethods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get DGM methods for ${targetType.name}: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Get methods for a ClassNode using Groovy runtime classes
	 */
	static List<String> getMethodsForClassNode(ClassNode classNode) {
		if (!classNode) return []
		
		try {
			// Try to get the actual Class
			Class<?> clazz = Class.forName(classNode.name)
			return getContextAwareMethods(clazz)
		} catch (ClassNotFoundException e) {
			// For ClassHelper types, use known mappings
			return getMethodsForClassHelperType(classNode)
		}
	}
	
	/**
	 * Get methods for ClassHelper types using runtime knowledge
	 */
	static List<String> getMethodsForClassHelperType(ClassNode classNode) {
		if (classNode == ClassHelper.STRING_TYPE) {
			return getContextAwareMethods(String.class)
		} else if (classNode == ClassHelper.LIST_TYPE) {
			return getContextAwareMethods(List.class)
		} else if (classNode == ClassHelper.MAP_TYPE) {
			return getContextAwareMethods(Map.class)
		} else if (classNode == ClassHelper.SET_TYPE) {
			return getContextAwareMethods(Set.class)
		} else if (classNode == ClassHelper.OBJECT_TYPE) {
			return getContextAwareMethods(Object.class)
		} else if (classNode == ClassHelper.Integer_TYPE || classNode == ClassHelper.int_TYPE) {
			return getContextAwareMethods(Integer.class)
		} else if (classNode == ClassHelper.Double_TYPE || classNode == ClassHelper.double_TYPE) {
			return getContextAwareMethods(Double.class)
		} else if (classNode == ClassHelper.Boolean_TYPE || classNode == ClassHelper.boolean_TYPE) {
			return getContextAwareMethods(Boolean.class)
		} else {
			// Fallback to Object methods
			return getContextAwareMethods(Object.class)
		}
	}
	
	/**
	 * Get type coercion methods using DefaultTypeTransformation
	 */
	static List<String> getTypeCoercionMethods(Class<?> fromType, Class<?> toType) {
		String cacheKey = "coercion_${fromType.name}_${toType.name}".toString()
		return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
			try {
				List<String> coercionMethods = []
				
				// Check if types are coercible
				if (DefaultTypeTransformation.castToBoolean(fromType)) {
					coercionMethods << 'asBoolean'
				}
				
				// Add common coercion methods
				if (fromType != String.class) {
					coercionMethods << 'toString'
				}
				
				if (Number.class.isAssignableFrom(fromType) || fromType.isPrimitive()) {
					coercionMethods.addAll(['toInteger', 'toDouble', 'toLong', 'toFloat'])
				}
				
				if (fromType == String.class) {
					coercionMethods.addAll(['toInteger', 'toDouble', 'toBoolean', 'toBigDecimal'])
				}
				
				return coercionMethods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get coercion methods: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Get collection-specific methods using runtime analysis
	 */
	static List<String> getCollectionSpecificMethods(Class<?> collectionType) {
		String cacheKey = "collection_${collectionType.name}".toString()
		return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
			try {
				List<String> methods = getContextAwareMethods(collectionType)
				
				// Add collection-specific DGM methods
				if (Collection.class.isAssignableFrom(collectionType)) {
					methods.addAll([
							'each', 'eachWithIndex', 'find', 'findAll', 'collect', 'collectMany',
							'inject', 'sum', 'flatten', 'any', 'every', 'sort', 'unique',
							'groupBy', 'join', 'min', 'max', 'reverse', 'intersect', 'disjoint',
							'plus', 'minus', 'multiply', 'leftShift', 'rightShift'
					])
				}
				
				if (List.class.isAssignableFrom(collectionType)) {
					methods.addAll(['getAt', 'putAt', 'pop', 'push', 'head', 'tail', 'init', 'last'])
				}
				
				if (Map.class.isAssignableFrom(collectionType)) {
					methods.addAll(['collectEntries', 'findResult', 'subMap', 'withDefault'])
				}
				
				return methods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get collection methods for ${collectionType.name}: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Get string-specific methods using runtime analysis
	 */
	static List<String> getStringSpecificMethods() {
		return (List<String>) CACHE.computeIfAbsent('string_specific') {
			try {
				List<String> methods = getContextAwareMethods(String.class)
				
				// Add string-specific DGM methods
				methods.addAll([
						'eachLine', 'eachMatch', 'findAll', 'split', 'tokenize',
						'padLeft', 'padRight', 'center', 'reverse', 'capitalize',
						'uncapitalize', 'normalize', 'denormalize', 'tr', 'stripIndent',
						'stripMargin', 'isNumber', 'isInteger', 'isFloat', 'isDouble',
						'toBigInteger', 'toBigDecimal', 'toURL', 'toURI'
				])
				
				return methods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get string-specific methods: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Get closure-specific methods and properties
	 */
	static List<String> getClosureSpecificMethods() {
		return (List<String>) CACHE.computeIfAbsent('closure_specific') {
			try {
				List<String> methods = getContextAwareMethods(Closure.class)
				
				// Add closure-specific methods and properties
				methods.addAll([
						'call', 'doCall', 'curry', 'rcurry', 'ncurry', 'memoize',
						'trampoline', 'rehydrate', 'dehydrate', 'clone',
						'getDelegate', 'setDelegate', 'getOwner', 'getThisObject',
						'getResolveStrategy', 'setResolveStrategy', 'getDirective',
						'getParameterTypes', 'getMaximumNumberOfParameters'
				])
				
				return methods.unique().sort()
			} catch (Exception e) {
				log.warn("Could not get closure-specific methods: ${e.message}")
				return []
			}
		}
	}
	
	/**
	 * Check if two types are compatible using Groovy's type system
	 */
	private static boolean isTypeCompatible(Class<?> paramType, Class<?> targetType) {
		try {
			// Direct assignment
			if (paramType.isAssignableFrom(targetType)) {
				return true
			}
			
			// Primitive/wrapper compatibility
			if (paramType.isPrimitive() || targetType.isPrimitive()) {
				return MetaClassHelper.convertToTypeArray([targetType] as Class[], [paramType] as Class[])
			}
			
			// Groovy type coercion
			return DefaultTypeTransformation.castToBoolean(
					DefaultTypeTransformation.castToType(targetType, paramType)
			)
		} catch (Exception e) {
			return false
		}
	}
	
	/**
	 * Get method signature information using reflection cache
	 */
	static Map<String, Object> getMethodSignature(Class<?> targetType, String methodName) {
		try {
			CachedClass cachedClass = ReflectionCache.getCachedClass(targetType)
			def method = cachedClass.methods.find { it.name == methodName }
			
			if (method) {
				return [
						name          : method.name,
						returnType    : method.returnType,
						parameterTypes: method.parameterTypes,
						isStatic      : method.isStatic(),
						isPublic      : method.isPublic()
				] as Map<String, Object>
			}
		} catch (Exception e) {
			log.debug("Could not get method signature for ${targetType.name}.${methodName}: ${e.message}")
		}
		
		return [:]
	}
	
	/**
	 * Clear all caches
	 */
	static void clearCaches() {
		CACHE.clear()
		log.debug("Cleared GroovyRuntimeIntegration caches")
	}
}