package kingsk.grails.lsp.utils.grails

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassNode

import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Integration with official Grails helper methods and utilities.
 * ClassLoader-aware to dynamically retrieve members from the project's classpath.
 */
@Slf4j
@CompileStatic
class GrailsHelperIntegration {
    
    private static final Map<ClassLoader, Map<String, Object>> CACHE = new ConcurrentHashMap<>()
    
    private static Map<String, Object> getCache(ClassLoader loader) {
        if (loader == null) loader = GrailsHelperIntegration.class.classLoader
        return CACHE.computeIfAbsent(loader) { new ConcurrentHashMap<String, Object>() }
    }

    /**
     * Get Grails artifact types using official Grails utilities
     */
    static List<String> getGrailsArtifactTypes(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('grails_artifact_types') {
            try {
                // Try to use GrailsNameUtils from project classpath
                Class<?> nameUtils = Class.forName('grails.util.GrailsNameUtils', true, loader ?: GrailsHelperIntegration.class.classLoader)
                // If it exists, we assume standard artifacts are available
                return [
                    'Controller', 'Service', 'Domain', 'TagLib', 'Command', 
                    'Job', 'Interceptor', 'Codec', 'Converter', 'Filter'
                ]
            } catch (Exception e) {
                log.debug("GrailsNameUtils not found in project classpath, using standard artifact types")
                return ['Controller', 'Service', 'Domain', 'TagLib']
            }
        }
    }
    
    /**
     * Get GORM methods using official GORM utilities
     */
    static List<String> getGormInstanceMethods(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('gorm_instance_methods') {
            try {
                // Try to get methods from GormEntity interface in project classpath
                Class<?> gormEntity = Class.forName('grails.gorm.GormEntity', true, loader ?: GrailsHelperIntegration.class.classLoader)
                return gormEntity.methods.findAll { method ->
                    !method.name.startsWith('get') && 
                    !method.name.startsWith('set') &&
                    !method.name.startsWith('is')
                }.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("GormEntity not found in project classpath, using standard GORM methods")
                return [
                    'save', 'delete', 'refresh', 'merge', 'attach', 'discard',
                    'validate', 'hasErrors', 'clearErrors', 'getErrors'
                ]
            }
        }
    }
    
    /**
     * Get GORM static methods using official GORM utilities
     */
    static List<String> getGormStaticMethods(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('gorm_static_methods') {
            try {
                // Try to get static methods from GORM in project classpath
                Class<?> gormStaticApi = Class.forName('grails.gorm.GormStaticApi', true, loader ?: GrailsHelperIntegration.class.classLoader)
                return gormStaticApi.methods.findAll { method ->
                    Modifier.isStatic(method.modifiers) &&
                    Modifier.isPublic(method.modifiers)
                }.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("GormStaticApi not found in project classpath, using standard static methods")
                return [
                    'get', 'load', 'findBy', 'findAllBy', 'countBy', 'list', 
                    'findAll', 'count', 'exists', 'createCriteria', 'withCriteria',
                    'withTransaction', 'where', 'findWhere', 'findAllWhere'
                ]
            }
        }
    }
    
    /**
     * Get Grails controller methods using official utilities
     */
    static List<String> getControllerMethods(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('controller_methods') {
            try {
                // Try to get methods from Controller trait/interface in project classpath
                Class<?> controllerTrait = Class.forName('grails.artefact.Controller', true, loader ?: GrailsHelperIntegration.class.classLoader)
                return controllerTrait.methods.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("Controller trait not found in project classpath, using standard methods")
                return [
                    'render', 'redirect', 'forward', 'chain', 'withFormat',
                    'bindData', 'respond'
                ]
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
    static List<String> getTagLibMethods(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('taglib_methods') {
            try {
                // Try to get methods from TagLib trait in project classpath
                Class<?> tagLibTrait = Class.forName('grails.artefact.TagLib', true, loader ?: GrailsHelperIntegration.class.classLoader)
                return tagLibTrait.methods.collect { it.name }.unique().sort()
            } catch (Exception e) {
                log.debug("TagLib trait not found in project classpath, using standard methods")
                return ['render', 'include', 'createLink', 'resource']
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
    static List<String> getGrailsConfigurationKeys(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('grails_config_keys') {
            try {
                // Try to get configuration keys from Grails in project classpath
                Class<?> grailsUtil = Class.forName('grails.util.GrailsUtil', true, loader ?: GrailsHelperIntegration.class.classLoader)
                
                // Standard Grails configuration keys
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
                log.debug("GrailsUtil not found in project classpath, using standard config keys")
                return [
                    'grails.serverURL', 'grails.logging.level', 'server.port'
                ]
            }
        }
    }
    
    /**
     * Get Grails constraint names using official utilities
     */
    static List<String> getGrailsConstraints(ClassLoader loader = null) {
        return (List<String>) getCache(loader).computeIfAbsent('grails_constraints') {
            try {
                // Try to get constraints from Grails validation in project classpath
                Class<?> constraintFactory = Class.forName('grails.validation.ConstraintFactory', true, loader ?: GrailsHelperIntegration.class.classLoader)
                
                // Standard Grails constraints
                return [
                    'nullable', 'blank', 'size', 'minSize', 'maxSize',
                    'min', 'max', 'range', 'inList', 'matches', 'email',
                    'url', 'unique', 'validator', 'bindable', 'display',
                    'editable', 'format', 'password', 'widget', 'attributes'
                ]
            } catch (Exception e) {
                log.debug("ConstraintFactory not found in project classpath, using standard constraints")
                return [
                    'nullable', 'blank', 'size', 'min', 'max', 'range',
                    'inList', 'matches', 'email', 'url', 'unique', 'validator'
                ]
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
            // Use GrailsNameUtils to check artifact type
            Class<?> nameUtils = Class.forName('grails.util.GrailsNameUtils', true, loader ?: GrailsHelperIntegration.class.classLoader)
            def method = nameUtils.getMethod('getLogicalName', String, String)
            
            getGrailsArtifactTypes(loader).any { artifactType ->
                className.endsWith(artifactType)
            }
        } catch (Exception e) {
            // Fallback to simple name checking
            return getGrailsArtifactTypes(loader).any { artifactType ->
                className.endsWith(artifactType)
            }
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
                return [] // Services don't have special methods
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
        log.debug("Cleared GrailsHelperIntegration caches")
    }
}
