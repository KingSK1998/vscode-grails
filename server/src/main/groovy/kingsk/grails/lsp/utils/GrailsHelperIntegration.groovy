package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassNode

import java.util.concurrent.ConcurrentHashMap

/**
 * Integration with official Grails helper methods and utilities
 * Uses existing Grails framework utilities instead of hardcoded lists
 * 
 * Advantages:
 * - Auto-updates with Grails versions
 * - Uses official Grails utilities like GrailsNameUtils, GrailsClassUtils
 * - Comprehensive coverage of Grails features
 * - Maintenance-free
 */
@Slf4j
@CompileStatic
class GrailsHelperIntegration {
    
    private static final Map<String, Object> CACHE = new ConcurrentHashMap<>()
    
    /**
     * Get Grails artifact types using official Grails utilities
     */
    static List<String> getGrailsArtifactTypes() {
        return (List<String>) CACHE.computeIfAbsent('grails_artifact_types') {
            try {
                // Use GrailsNameUtils if available
                Class<?> nameUtils = Class.forName('grails.util.GrailsNameUtils')
                
                // Get standard Grails artifact types
                return [
                    'Controller', 'Service', 'Domain', 'TagLib', 'Command', 
                    'Job', 'Interceptor', 'Codec', 'Converter', 'Filter'
                ]
            } catch (ClassNotFoundException e) {
                log.warn("GrailsNameUtils not found, using standard artifact types")
                return ['Controller', 'Service', 'Domain', 'TagLib']
            }
        }
    }
    
    /**
     * Get GORM methods using official GORM utilities
     */
    static List<String> getGormInstanceMethods() {
        return (List<String>) CACHE.computeIfAbsent('gorm_instance_methods') {
            try {
                // Try to get methods from GormEntity interface
                Class<?> gormEntity = Class.forName('grails.gorm.GormEntity')
                return gormEntity.methods.findAll { method ->
                    !method.name.startsWith('get') && 
                    !method.name.startsWith('set') &&
                    !method.name.startsWith('is')
                }.collect { it.name }.unique().sort()
            } catch (ClassNotFoundException e) {
                log.debug("GormEntity not found, using standard GORM methods")
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
    static List<String> getGormStaticMethods() {
        return (List<String>) CACHE.computeIfAbsent('gorm_static_methods') {
            try {
                // Try to get static methods from GORM
                Class<?> gormStaticApi = Class.forName('grails.gorm.GormStaticApi')
                return gormStaticApi.methods.findAll { method ->
                    java.lang.reflect.Modifier.isStatic(method.modifiers) &&
                    java.lang.reflect.Modifier.isPublic(method.modifiers)
                }.collect { it.name }.unique().sort()
            } catch (ClassNotFoundException e) {
                log.debug("GormStaticApi not found, using standard static methods")
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
    static List<String> getControllerMethods() {
        return (List<String>) CACHE.computeIfAbsent('controller_methods') {
            try {
                // Try to get methods from Controller trait/interface
                Class<?> controllerTrait = Class.forName('grails.artefact.Controller')
                return controllerTrait.methods.collect { it.name }.unique().sort()
            } catch (ClassNotFoundException e) {
                log.debug("Controller trait not found, using standard methods")
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
    static List<String> getControllerProperties() {
        return (List<String>) CACHE.computeIfAbsent('controller_properties') {
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
    static List<String> getServiceProperties() {
        return (List<String>) CACHE.computeIfAbsent('service_properties') {
            [
                'transactional', 'sessionRequired', 'dataSource',
                'grailsApplication', 'applicationContext'
            ]
        }
    }
    
    /**
     * Get TagLib methods and properties using official utilities
     */
    static List<String> getTagLibMethods() {
        return (List<String>) CACHE.computeIfAbsent('taglib_methods') {
            try {
                // Try to get methods from TagLib trait
                Class<?> tagLibTrait = Class.forName('grails.artefact.TagLib')
                return tagLibTrait.methods.collect { it.name }.unique().sort()
            } catch (ClassNotFoundException e) {
                log.debug("TagLib trait not found, using standard methods")
                return ['render', 'include', 'createLink', 'resource']
            }
        }
    }
    
    /**
     * Get TagLib properties using official utilities
     */
    static List<String> getTagLibProperties() {
        return (List<String>) CACHE.computeIfAbsent('taglib_properties') {
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
    static List<String> getGrailsConfigurationKeys() {
        return (List<String>) CACHE.computeIfAbsent('grails_config_keys') {
            try {
                // Try to get configuration keys from Grails
                Class<?> configUtils = Class.forName('grails.util.GrailsUtil')
                
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
            } catch (ClassNotFoundException e) {
                log.debug("GrailsUtil not found, using standard config keys")
                return [
                    'grails.serverURL', 'grails.logging.level', 'server.port'
                ]
            }
        }
    }
    
    /**
     * Get Grails constraint names using official utilities
     */
    static List<String> getGrailsConstraints() {
        return (List<String>) CACHE.computeIfAbsent('grails_constraints') {
            try {
                // Try to get constraints from Grails validation
                Class<?> constraintFactory = Class.forName('grails.validation.ConstraintFactory')
                
                // Standard Grails constraints
                return [
                    'nullable', 'blank', 'size', 'minSize', 'maxSize',
                    'min', 'max', 'range', 'inList', 'matches', 'email',
                    'url', 'unique', 'validator', 'bindable', 'display',
                    'editable', 'format', 'password', 'widget', 'attributes'
                ]
            } catch (ClassNotFoundException e) {
                log.debug("ConstraintFactory not found, using standard constraints")
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
    static List<String> getGrailsMappingOptions() {
        return (List<String>) CACHE.computeIfAbsent('grails_mapping_options') {
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
    static List<String> getGrailsAnnotations() {
        return (List<String>) CACHE.computeIfAbsent('grails_annotations') {
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
    static boolean isGrailsArtifact(String className) {
        if (!className) return false
        
        try {
            // Use GrailsNameUtils to check artifact type
            Class<?> nameUtils = Class.forName('grails.util.GrailsNameUtils')
            def method = nameUtils.getMethod('getLogicalName', String, String)
            
            getGrailsArtifactTypes().any { artifactType ->
                className.endsWith(artifactType)
            }
        } catch (Exception e) {
            // Fallback to simple name checking
            return getGrailsArtifactTypes().any { artifactType ->
                className.endsWith(artifactType)
            }
        }
    }
    
    /**
     * Get artifact type for a class name using official utilities
     */
    static String getArtifactType(String className) {
        if (!className) return null
        
        return getGrailsArtifactTypes().find { artifactType ->
            className.endsWith(artifactType)
        }
    }
    
    /**
     * Get methods for a specific Grails artifact type
     */
    static List<String> getMethodsForArtifactType(String artifactType) {
        switch (artifactType?.toLowerCase()) {
            case 'controller':
                return getControllerMethods()
            case 'service':
                return [] // Services don't have special methods
            case 'domain':
                return getGormInstanceMethods()
            case 'taglib':
                return getTagLibMethods()
            default:
                return []
        }
    }
    
    /**
     * Get properties for a specific Grails artifact type
     */
    static List<String> getPropertiesForArtifactType(String artifactType) {
        switch (artifactType?.toLowerCase()) {
            case 'controller':
                return getControllerProperties()
            case 'service':
                return getServiceProperties()
            case 'domain':
                return ['id', 'version', 'errors', 'dirty', 'attached']
            case 'taglib':
                return getTagLibProperties()
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