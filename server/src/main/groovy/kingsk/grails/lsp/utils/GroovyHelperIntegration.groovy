package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.InvokerHelper
import groovy.lang.GroovySystem
import groovy.lang.MetaClass
import groovy.lang.MetaMethod
import groovy.lang.MetaProperty

import java.util.concurrent.ConcurrentHashMap

/**
 * Integration with official Groovy and Grails helper methods
 * Uses existing framework utilities instead of hardcoded lists
 * 
 * Advantages:
 * - Auto-updates with Groovy/Grails versions
 * - Uses official framework methods
 * - Comprehensive coverage
 * - Maintenance-free
 */
@Slf4j
@CompileStatic
class GroovyHelperIntegration {
    
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>()
    
    /**
     * Get all available methods for a class using Groovy's MetaClass system
     * This includes DefaultGroovyMethods and other extensions automatically
     */
    static List<String> getMethodsUsingMetaClass(Class<?> clazz) {
        String cacheKey = "methods_${clazz.name}".toString()
        return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
            try {
                MetaClass metaClass = InvokerHelper.getMetaClass(clazz)
                return metaClass.methods.collect { MetaMethod method ->
                    method.name
                }.unique().sort()
            } catch (Exception e) {
                log.warn("Could not get methods for ${clazz.name} via MetaClass: ${e.message}")
                return []
            }
        }
    }
    
    /**
     * Get all available properties for a class using Groovy's MetaClass system
     */
    static List<String> getPropertiesUsingMetaClass(Class<?> clazz) {
        String cacheKey = "properties_${clazz.name}".toString()
        return (List<String>) CACHE.computeIfAbsent(cacheKey) { key ->
            try {
                MetaClass metaClass = InvokerHelper.getMetaClass(clazz)
                return metaClass.properties.collect { MetaProperty property ->
                    property.name
                }.unique().sort()
            } catch (Exception e) {
                log.warn("Could not get properties for ${clazz.name} via MetaClass: ${e.message}")
                return []
            }
        }
    }
    
    /**
     * Get all DefaultGroovyMethods using official Groovy utilities
     */
    static List<String> getDefaultGroovyMethods() {
        return (List<String>) CACHE.computeIfAbsent('default_groovy_methods') {
            try {
                // Use DefaultGroovyMethods class directly
                return DefaultGroovyMethods.class.methods.findAll { method ->
                    java.lang.reflect.Modifier.isPublic(method.modifiers) && 
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    !method.name.startsWith('get') &&
                    !method.name.startsWith('set') &&
                    !method.name.startsWith('is')
                }.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.warn("Could not access DefaultGroovyMethods: ${e.message}")
                return []
            }
        }
    }
    
    /**
     * Get all methods for Object including Groovy extensions
     */
    static List<String> getObjectMethodsWithExtensions() {
        return getMethodsUsingMetaClass(Object.class)
    }
    
    /**
     * Get all methods for String including Groovy extensions
     */
    static List<String> getStringMethodsWithExtensions() {
        return getMethodsUsingMetaClass(String.class)
    }
    
    /**
     * Get all methods for Collection including Groovy extensions
     */
    static List<String> getCollectionMethodsWithExtensions() {
        return getMethodsUsingMetaClass(Collection.class)
    }
    
    /**
     * Get all methods for Map including Groovy extensions
     */
    static List<String> getMapMethodsWithExtensions() {
        return getMethodsUsingMetaClass(Map.class)
    }
    
    /**
     * Get all methods for List including Groovy extensions
     */
    static List<String> getListMethodsWithExtensions() {
        return getMethodsUsingMetaClass(List.class)
    }
    
    /**
     * Get methods for any ClassNode using MetaClass system
     */
    static List<String> getMethodsForClassNode(ClassNode classNode) {
        if (!classNode) return []
        
        try {
            // Try to get the actual Class for the ClassNode
            Class<?> clazz = Class.forName(classNode.name)
            return getMethodsUsingMetaClass(clazz)
        } catch (ClassNotFoundException e) {
            // Fallback to AST methods if class not found
            return classNode.methods?.collect { it.name }?.unique()?.sort() ?: []
        }
    }
    
    /**
     * Get properties for any ClassNode using MetaClass system
     */
    static List<String> getPropertiesForClassNode(ClassNode classNode) {
        if (!classNode) return []
        
        try {
            Class<?> clazz = Class.forName(classNode.name)
            return getPropertiesUsingMetaClass(clazz)
        } catch (ClassNotFoundException e) {
            // Fallback to AST properties if class not found
            return classNode.properties?.collect { it.name }?.unique()?.sort() ?: []
        }
    }
    
    /**
     * Get all available types from Groovy's type system
     */
    static List<ClassNode> getAllGroovyTypes() {
        return (List<ClassNode>) CACHE.computeIfAbsent('all_groovy_types') {
            List<ClassNode> types = []
            
            // Add all ClassHelper constants
            types.addAll([
                // Primitives
                ClassHelper.boolean_TYPE, ClassHelper.byte_TYPE, ClassHelper.char_TYPE,
                ClassHelper.short_TYPE, ClassHelper.int_TYPE, ClassHelper.long_TYPE,
                ClassHelper.float_TYPE, ClassHelper.double_TYPE, ClassHelper.VOID_TYPE,
                
                // Wrapper types
                ClassHelper.Boolean_TYPE, ClassHelper.Byte_TYPE, ClassHelper.Character_TYPE,
                ClassHelper.Short_TYPE, ClassHelper.Integer_TYPE, ClassHelper.Long_TYPE,
                ClassHelper.Float_TYPE, ClassHelper.Double_TYPE,
                
                // Common types
                ClassHelper.OBJECT_TYPE, ClassHelper.STRING_TYPE, ClassHelper.CLASS_Type,
                ClassHelper.Number_TYPE, ClassHelper.LIST_TYPE, ClassHelper.MAP_TYPE,
                ClassHelper.SET_TYPE, ClassHelper.RANGE_TYPE, ClassHelper.PATTERN_TYPE,
                
                // Groovy types
                ClassHelper.CLOSURE_TYPE, ClassHelper.GROOVY_OBJECT_TYPE, 
                ClassHelper.METACLASS_TYPE, ClassHelper.DYNAMIC_TYPE,
                
                // Script types
                ClassHelper.SCRIPT_TYPE, ClassHelper.BINDING_TYPE
            ])
            
            return types.unique()
        }
    }
    
    /**
     * Check if a method is a Groovy extension method
     */
    static boolean isGroovyExtensionMethod(String methodName, Class<?> targetClass) {
        try {
            MetaClass metaClass = InvokerHelper.getMetaClass(targetClass)
            def methods = metaClass.methods.findAll { it.name == methodName }
            
            return methods.any { method ->
                method.declaringClass?.name?.contains('DefaultGroovyMethods') ||
                method.declaringClass?.name?.contains('Extension')
            }
        } catch (Exception e) {
            return false
        }
    }
    
    /**
     * Get all registered MetaClass extensions
     */
    static List<String> getAllMetaClassExtensions() {
        return (List<String>) CACHE.computeIfAbsent('metaclass_extensions') {
            try {
                def registry = GroovySystem.metaClassRegistry
                def extensions = []
                
                // Get extensions from common classes
                [Object, String, Collection, List, Map].each { clazz ->
                    try {
                        MetaClass metaClass = registry.getMetaClass(clazz)
                        metaClass.methods.each { method ->
                            if (isGroovyExtensionMethod(method.name, clazz)) {
                                extensions << method.name
                            }
                        }
                    } catch (Exception e) {
                        log.debug("Could not get MetaClass for ${clazz.name}: ${e.message}")
                    }
                }
                
                return extensions.unique().sort()
            } catch (Exception e) {
                log.warn("Could not get MetaClass extensions: ${e.message}")
                return []
            }
        }
    }
    
    /**
     * Clear all caches
     */
    static void clearCaches() {
        CACHE.clear()
        log.debug("Cleared GroovyHelperIntegration caches")
    }
}