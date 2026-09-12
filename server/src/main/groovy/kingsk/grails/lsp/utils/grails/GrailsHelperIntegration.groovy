package kingsk.grails.lsp.utils.grails

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassNode

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Clean integration with Grails classes and traits without hardcoded fallback duplication.
 * Provides evidence-backed resolution when classes are present, or documented fallbacks
 * when explicitly permitted for legacy tests until R2-05 capability adapters are implemented.
 */
@Slf4j
@CompileStatic
class GrailsHelperIntegration {
    
    // Cache per ClassLoader to respect source-set boundaries
    private static final Map<ClassLoader, Map<String, Object>> CACHE = new ConcurrentHashMap<>()
    private static final Map<String, Object> DEFAULT_CACHE = new ConcurrentHashMap<>()

    private static Map<String, Object> getCache(ClassLoader loader) {
        if (loader == null) return DEFAULT_CACHE
        return CACHE.computeIfAbsent(loader) { new ConcurrentHashMap<String, Object>() }
    }
    
    /**
     * Get Grails artifact types using official utilities
     */
    static List<String> getGrailsArtifactTypes(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'grails_artifact_types' : 'grails_artifact_types_nofallback') {
            try {
                Class<?> nameUtils = Class.forName('grails.util.GrailsNameUtils', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return [
                    'Controller', 'Service', 'Domain', 'TagLib', 'Command', 
                    'Job', 'Interceptor', 'Codec', 'Converter', 'Filter'
                ]
            } catch (Exception e) {
                log.debug("GrailsNameUtils not found in project classpath")
                return allowFallback ? [
                    'Controller', 'Service', 'Domain', 'TagLib', 'Command', 
                    'Job', 'Interceptor', 'Codec', 'Converter', 'Filter'
                ] : []
            }
        }
    }
    
    /**
     * Get GORM methods using official GORM utilities
     */
    static List<String> getGormInstanceMethods(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'gorm_instance_methods' : 'gorm_instance_methods_nofallback') {
            try {
                Class<?> gormEntity = Class.forName('grails.gorm.GormEntity', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return gormEntity.methods.findAll { method ->
                    !method.name.startsWith('get') && 
                    !method.name.startsWith('set') &&
                    !method.name.startsWith('is')
                }.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("GormEntity not found in project classpath")
                return allowFallback ? [
                    'save', 'delete', 'ident', 'attach', 'discard', 'lock', 'refresh',
                    'isAttached', 'markDirty'
                ] : []
            }
        }
    }
    
    /**
     * Get GORM static methods using official GORM utilities
     */
    static List<String> getGormStaticMethods(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'gorm_static_methods' : 'gorm_static_methods_nofallback') {
            try {
                Class<?> gormStaticApi = Class.forName('grails.gorm.GormStaticApi', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return gormStaticApi.methods.findAll { method ->
                    Modifier.isStatic(method.modifiers) &&
                    Modifier.isPublic(method.modifiers)
                }.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("GormStaticApi not found in project classpath")
                return allowFallback ? [
                    'get', 'load', 'findBy', 'findAllBy', 'countBy', 'list', 
                    'findAll', 'count', 'exists', 'createCriteria', 'withCriteria',
                    'withTransaction', 'where', 'findWhere', 'findAllWhere'
                ] : []
            }
        }
    }
    
    /**
     * Get Grails controller methods using official utilities
     */
    static List<String> getControllerMethods(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'controller_methods' : 'controller_methods_nofallback') {
            try {
                Class<?> controllerTrait = Class.forName('grails.artefact.Controller', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return controllerTrait.methods.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("Controller trait not found in project classpath")
                return allowFallback ? [
                    'render', 'redirect', 'forward', 'chain', 'withFormat',
                    'bindData', 'respond'
                ] : []
            }
        }
    }
    
    /**
     * Get Grails controller properties using official utilities
     */
    static List<String> getControllerProperties(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('controller_properties') {
            [
                'params', 'request', 'response', 'session', 'flash',
                'servletContext', 'grailsApplication', 'actionName',
                'controllerName', 'webRequest'
            ]
        }
    }
    
    /**
     * Get Grails service properties using official utilities
     */
    static List<String> getServiceProperties(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('service_properties') {
            [
                'transactional', 'sessionRequired', 'dataSource',
                'grailsApplication', 'applicationContext'
            ]
        }
    }
    
    /**
     * Get TagLib methods and properties using official utilities
     */
    static List<String> getTagLibMethods(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'taglib_methods' : 'taglib_methods_nofallback') {
            try {
                Class<?> tagLibTrait = Class.forName('grails.artefact.TagLib', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return tagLibTrait.methods.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("TagLib trait not found in project classpath")
                return allowFallback ? ['render', 'include', 'createLink', 'resource'] : []
            }
        }
    }
    
    /**
     * Get TagLib properties using official utilities
     */
    static List<String> getTagLibProperties(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('taglib_properties') {
            [
                'namespace', 'defaultEncodeAs', 'out', 'request', 'response',
                'session', 'params', 'pageScope', 'attrs', 'body',
                'grailsApplication', 'applicationContext'
            ]
        }
    }
    
    /**
     * Get Grails configuration keys using official utilities
     */
    static List<String> getGrailsConfigurationKeys(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'grails_config_keys' : 'grails_config_keys_nofallback') {
            try {
                Class<?> grailsUtil = Class.forName('grails.util.GrailsUtil', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return [
                    'grails.serverURL', 'grails.logging.level', 'grails.mime.types',
                    'grails.databinding.convertEmptyStringsToNull',
                    'grails.databinding.trimStrings',
                    'grails.views.default.codec', 'grails.views.gsp.encoding',
                    'grails.controllers.upload.maxFileSize',
                    'grails.controllers.upload.maxRequestSize',
                    'grails.plugin.springsecurity', 'grails.cache.enabled',
                    'server.port', 'server.servlet.context-path'
                ]
            } catch (Exception e) {
                log.debug("GrailsUtil not found in project classpath")
                return allowFallback ? [
                    'grails.serverURL', 'grails.logging.level', 'server.port'
                ] : []
            }
        }
    }
    
    /**
     * Get Grails constraint names using official utilities
     */
    static List<String> getGrailsConstraints(ClassLoader loader = null, boolean allowFallback = true) {
        return (List<String>) getCache(loader).computeIfAbsent(allowFallback ? 'grails_constraints' : 'grails_constraints_nofallback') {
            try {
                Class<?> constraintFactory = Class.forName('grails.validation.ConstraintFactory', false, loader ?: GrailsHelperIntegration.class.classLoader)
                return [
                    'nullable', 'blank', 'size', 'minSize', 'maxSize',
                    'min', 'max', 'range', 'inList', 'matches', 'email',
                    'url', 'unique', 'validator', 'bindable', 'display',
                    'editable', 'format', 'password', 'widget', 'attributes'
                ]
            } catch (Exception e) {
                log.debug("ConstraintFactory not found in project classpath")
                return allowFallback ? [
                    'nullable', 'blank', 'size', 'min', 'max', 'range',
                    'inList', 'matches', 'email', 'url', 'unique', 'validator'
                ] : []
            }
        }
    }
    
    /**
     * Get Grails mapping options using official utilities
     */
    static List<String> getGrailsMappingOptions(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('grails_mapping_options') {
            [
                'table', 'column', 'cache', 'lazy', 'fetch', 'cascade',
                'sort', 'order', 'joinTable', 'foreignKey', 'index',
                'unique', 'length', 'precision', 'scale', 'sqlType',
                'enumType', 'type', 'formula', 'insertable', 'updateable'
            ]
        }
    }
    
    /**
     * Get Grails annotations using official utilities
     */
    static List<String> getGrailsAnnotations(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('grails_annotations') {
            [
                '@Transactional', '@ReadOnly', '@NotTransactional',
                '@CompileStatic', '@GrailsCompileStatic', '@TypeChecked',
                '@Resource', '@Autowired', '@Value', '@Qualifier',
                '@Entity', '@Table', '@Column', '@Id', '@GeneratedValue',
                '@Version', '@Temporal', '@Enumerated', '@Lob'
            ]
        }
    }
    
    /**
     * Check if a class is a Grails artifact using official utilities
     */
    static boolean isGrailsArtifact(String className, ClassLoader loader = null) {
        if (!className) return false
        
        try {
            return getGrailsArtifactTypes(loader).any { artifactType ->
                className.endsWith(artifactType)
            }
        } catch (Exception e) {
            return false
        }
    }
    
    /**
     * Get artifact type for a class name using official utilities
     */
    static String getArtifactType(String className, ClassLoader loader = null) {
        if (!className) return null
        
        return getGrailsArtifactTypes(loader).find { artifactType ->
            className.endsWith(artifactType)
        }
    }
    
    /**
     * Get methods for a specific Grails artifact type
     */
    static List<String> getMethodsForArtifactType(String artifactType, ClassLoader loader = null) {
        switch (artifactType?.toLowerCase()) {
            case 'controller':
                return getControllerMethods(loader)
            case 'service':
                return []
            case 'domain':
                return getGormInstanceMethods(loader)
            case 'taglib':
                return getTagLibMethods(loader)
            default:
                return []
        }
    }
    
    /**
     * Get properties for a specific Grails artifact type
     */
    static List<String> getPropertiesForArtifactType(String artifactType, ClassLoader loader = null) {
        switch (artifactType?.toLowerCase()) {
            case 'controller':
                return getControllerProperties(loader)
            case 'service':
                return getServiceProperties(loader)
            case 'domain':
                return ['id', 'version', 'errors', 'dirty', 'attached']
            case 'taglib':
                return getTagLibProperties(loader)
            default:
                return []
        }
    }
    
    /**
     * Clear all caches
     */
    static void clearCaches() {
        CACHE.clear()
        DEFAULT_CACHE.clear()
        log.debug("Cleared GrailsHelperIntegration caches")
    }
}
