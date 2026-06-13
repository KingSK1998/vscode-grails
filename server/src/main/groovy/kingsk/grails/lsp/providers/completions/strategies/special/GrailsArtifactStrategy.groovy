package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.grails.GrailsHelperIntegration
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.stmt.BlockStatement

/**
 * Handles Grails artifact-specific completions
 * Provides context-aware completions based on Grails artifact type
 */
@CompileStatic
class GrailsArtifactStrategy extends BaseCompletionStrategy {

    GrailsArtifactStrategy(CompletionRequest request) { super(request) }

    @Override
    int getPriority() { return 92 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(ASTNode node) { return request.isGrailsProject && request.artefactType?.valid }

    @Override
    void provideCompletions(ASTNode node) {
        GrailsArtifactType artifactType = request.getArtefactType()
        if (!artifactType) return

        logDebug("Providing Grails artifact completions for: %s", artifactType)

        switch (artifactType) {
            case GrailsArtifactType.CONTROLLER:
                addControllerCompletions(request)
                break
            case GrailsArtifactType.DOMAIN:
                addDomainCompletions(request)
                break
            case GrailsArtifactType.SERVICE:
                addServiceCompletions(request)
                break
            case GrailsArtifactType.TAGLIB:
                addTagLibCompletions(request)
                break
            case GrailsArtifactType.COMMAND:
                addCommandCompletions(request)
                break
            case GrailsArtifactType.JOB:
                addJobCompletions(request)
                break
            case GrailsArtifactType.INTERCEPTOR:
                addInterceptorCompletions(request)
                break
            default:
                addGenericGrailsCompletions(request)
        }

        // Add common Grails completions for all artifacts
        addCommonGrailsCompletions(request)
    }

    /**
     * Add controller-specific completions using official Grails utilities
     */
    private void addControllerCompletions(CompletionRequest request) {
        def loader = request.service.compiler.classLoader
        // Add methods
        GrailsHelperIntegration.getControllerMethods(loader).each { method ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(method)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
            item.detail = 'Grails controller method'
            request.addCompletion(item)
        }

        // Add properties
        GrailsHelperIntegration.getControllerProperties(loader).each { prop ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(prop)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = 'Grails controller property'
            request.addCompletion(item)
        }

        // Special handling for common web params
        ['view', 'model', 'template', 'collection', 'bean', 'plugin', 'contentType', 'encoding'].each { webParam ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(webParam)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Keyword
            item.detail = 'Grails controller parameter'
            request.addRelatedCompletion(item)
        }

        if (request.offsetNode instanceof BlockStatement) {
            addScopeCompletions(request.offsetNode)
        }
    }

    /**
     * Add domain class completions using official GORM utilities
     */
    private void addDomainCompletions(CompletionRequest request) {
        try {
            def loader = request.service.compiler.classLoader
            // Use GrailsHelperIntegration for official GORM instance methods
            List<String> gormInstanceMethods = GrailsHelperIntegration.getGormInstanceMethods(loader)
            gormInstanceMethods.each { method ->
                org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(method)
                item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
                item.detail = 'GORM Instance Method'
                request.addCompletion(item)
            }

            // Use GrailsHelperIntegration for official GORM static methods
            List<String> gormStaticMethods = GrailsHelperIntegration.getGormStaticMethods(loader)
            gormStaticMethods.each { method ->
                org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(method)
                item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
                item.detail = 'GORM Static Method'
                request.addCompletion(item)
            }

            // Dynamically generate dynamic finders for domain properties
            def currentClass = request.getCurrentClass()
            if (currentClass) {
                currentClass.properties.each { prop ->
                    if (prop.name != 'class' && prop.name != 'metaClass') {
                        String capitalized = prop.name.capitalize()
                        ['findBy', 'findAllBy', 'countBy', 'existsBy'].each { prefix ->
                            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(prefix + capitalized)
                            item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
                            item.detail = 'GORM dynamic finder'
                            request.addCompletion(item)
                        }
                    }
                }
            }
        } catch (Exception e) {
            logDebug("Error getting GORM completions: %s", e.message)
        }

        // Domain properties
        Map<String, String> domainProperties = [
            'id'      : 'Domain instance ID',
            'version' : 'Optimistic locking version',
            'errors'  : 'Validation errors',
            'dirty'   : 'Has unsaved changes',
            'attached': 'Attached to session'
        ]

        domainProperties.each { property, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(property)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Domain property: ${description}"
            request.addCompletion(item)
        }

        // Add constraint completions
        addConstraintCompletions(request)

        // Add mapping completions
        addMappingCompletions(request)
    }

    /**
     * Add service completions
     */
    private void addServiceCompletions(CompletionRequest request) {
        // Service properties
        Map<String, String> serviceProperties = [
            'transactional'  : 'Transaction configuration',
            'sessionRequired': 'Hibernate session required',
            'dataSource'     : 'Data source reference'
        ]

        serviceProperties.each { property, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(property)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Service property: ${description}"
            request.addCompletion(item)
        }

        // Add transaction annotations
        addTransactionAnnotations(request)
    }

    /**
     * Add tag library completions
     */
    private void addTagLibCompletions(CompletionRequest request) {
        // TagLib properties
        Map<String, String> tagLibProperties = [
            'namespace'      : 'Tag namespace',
            'defaultEncodeAs': 'Default encoding',
            'out'            : 'Output writer',
            'request'        : 'HTTP request',
            'response'       : 'HTTP response',
            'session'        : 'HTTP session',
            'params'         : 'Request parameters',
            'pageScope'      : 'Page scope variables',
            'attrs'          : 'Tag attributes'
        ]

        tagLibProperties.each { property, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(property)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "TagLib property: ${description}"
            request.addCompletion(item)
        }

        // Add common tag patterns
        addTagPatterns(request)
    }

    /**
     * Add command object completions
     */
    private void addCommandCompletions(CompletionRequest request) {
        // Command object methods
        Map<String, String> commandMethods = [
            'validate'   : 'Validate command object',
            'hasErrors'  : 'Check for validation errors',
            'clearErrors': 'Clear validation errors'
        ]

        commandMethods.each { method, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(method)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
            item.detail = "Command method: ${description}"
            request.addCompletion(item)
        }

        // Add constraint completions for command objects
        addConstraintCompletions(request)
    }

    /**
     * Add job completions
     */
    private void addJobCompletions(CompletionRequest request) {
        // Job properties
        Map<String, String> jobProperties = [
            'concurrent'     : 'Allow concurrent execution',
            'sessionRequired': 'Hibernate session required',
            'group'          : 'Job group name',
            'description'    : 'Job description'
        ]

        jobProperties.each { property, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(property)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Job property: ${description}"
            request.addCompletion(item)
        }
    }

    /**
     * Add interceptor completions
     */
    private void addInterceptorCompletions(CompletionRequest request) {
        // Interceptor methods
        Map<String, String> interceptorMethods = [
            'before'   : 'Execute before action',
            'after'    : 'Execute after action',
            'afterView': 'Execute after view rendering'
        ]

        interceptorMethods.each { method, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(method)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
            item.detail = "Interceptor method: ${description}"
            request.addCompletion(item)
        }

        // Interceptor properties
        Map<String, String> interceptorProperties = [
            'match' : 'URL matching configuration',
            'except': 'URL exclusion configuration'
        ]

        interceptorProperties.each { property, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(property)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Interceptor property: ${description}"
            request.addCompletion(item)
        }
    }

    /**
     * Add generic Grails completions
     */
    private void addGenericGrailsCompletions(CompletionRequest request) {
        // Common Grails annotations
        List<String> grailsAnnotations = [
            '@Transactional', '@CompileStatic', '@GrailsCompileStatic',
            '@Resource', '@Autowired', '@Value'
        ]

        grailsAnnotations.each { annotation ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(annotation)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Class
            item.detail = 'Grails annotation'
            request.addCompletion(item)
        }
    }

    /**
     * Add common Grails completions for all artifacts
     */
    private void addCommonGrailsCompletions(CompletionRequest request) {
        // Common Grails objects
        Map<String, String> commonObjects = [
            'grailsApplication' : 'Grails application instance',
            'applicationContext': 'Spring application context',
            'log'               : 'Logger instance'
        ]

        commonObjects.each { object, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(object)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Variable
            item.detail = "Grails object: ${description}"
            request.addCompletion(item)
        }
    }

    // Helper methods for specific completion types

    private void addActionMethodPatterns(CompletionRequest request) {
        List<String> actionPatterns = [
            'index', 'show', 'create', 'save', 'edit', 'update', 'delete'
        ]

        actionPatterns.each { pattern ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(pattern)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
            item.detail = 'Controller action pattern'
            request.addCompletion(item)
        }
    }

    private void addResponseFormatCompletions(CompletionRequest request) {
        List<String> formats = [
            'html', 'json', 'xml', 'text', 'csv', 'pdf'
        ]

        formats.each { format ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(format)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.EnumMember
            item.detail = 'Response format'
            request.addCompletion(item)
        }
    }

    private void addConstraintCompletions(CompletionRequest request) {
        Map<String, String> constraints = [
            'nullable' : 'Allow null values',
            'blank'    : 'Allow blank strings',
            'size'     : 'String/collection size range',
            'minSize'  : 'Minimum size',
            'maxSize'  : 'Maximum size',
            'min'      : 'Minimum numeric value',
            'max'      : 'Maximum numeric value',
            'range'    : 'Numeric range',
            'inList'   : 'Value must be in list',
            'matches'  : 'Regular expression pattern',
            'email'    : 'Valid email format',
            'url'      : 'Valid URL format',
            'unique'   : 'Unique constraint',
            'validator': 'Custom validator closure'
        ]

        constraints.each { constraint, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(constraint)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Constraint: ${description}"
            request.addCompletion(item)
        }
    }

    private void addMappingCompletions(CompletionRequest request) {
        Map<String, String> mappings = [
            'table'  : 'Database table name',
            'column' : 'Database column name',
            'cache'  : 'Hibernate cache strategy',
            'lazy'   : 'Lazy loading configuration',
            'fetch'  : 'Fetch strategy',
            'cascade': 'Cascade operations',
            'sort'   : 'Default sort order',
            'order'  : 'Sort direction'
        ]

        mappings.each { mapping, description ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(mapping)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
            item.detail = "Mapping: ${description}"
            request.addCompletion(item)
        }
    }

    private void addTransactionAnnotations(CompletionRequest request) {
        List<String> transactionAnnotations = [
            '@Transactional', '@ReadOnly', '@NotTransactional'
        ]

        transactionAnnotations.each { annotation ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(annotation)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Class
            item.detail = 'Transaction annotation'
            request.addCompletion(item)
        }
    }

    private void addTagPatterns(CompletionRequest request) {
        List<String> tagPatterns = [
            'def tagName = { attrs, body ->', 'def tagName = { attrs ->',
            'out <<', 'attrs.', 'body()'
        ]

        tagPatterns.each { pattern ->
            org.eclipse.lsp4j.CompletionItem item = new org.eclipse.lsp4j.CompletionItem(pattern)
            item.kind = org.eclipse.lsp4j.CompletionItemKind.Snippet
            item.detail = 'Tag pattern'
            request.addCompletion(item)
        }
    }
}