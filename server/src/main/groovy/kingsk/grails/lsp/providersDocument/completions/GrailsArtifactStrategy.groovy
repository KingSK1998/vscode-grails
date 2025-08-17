package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.model.GrailsArtifactType
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.GrailsHelperIntegration
import org.codehaus.groovy.ast.ASTNode

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
		try {
			// Use GrailsHelperIntegration for official controller methods
			List<String> controllerMethods = GrailsHelperIntegration.getControllerMethods()
			controllerMethods.each { method ->
				////request.addCompletion(method, CompletionItemKind.Method, 'Grails Controller Method')
			}
			
			// Use GrailsHelperIntegration for official controller properties
			List<String> controllerProperties = GrailsHelperIntegration.getControllerProperties()
			controllerProperties.each { property ->
				// request.addCompletion(property, CompletionItemKind.Property, 'Grails Controller Property')
			}
		} catch (Exception e) {
			logDebug("Error getting controller completions: %s", e.message)
			// Fallback to basic controller completions
			//['render', 'redirect', 'forward', 'chain'].each { method ->
			// //request.addCompletion(method, CompletionItemKind.Method, 'Controller Method')
			//}
			//['params', 'request', 'response', 'session'].each { property ->
			// request.addCompletion(property, CompletionItemKind.Property, 'Controller Property')
			//}
		}
		
		// Add action method patterns
		addActionMethodPatterns(request)
		
		// Add response format completions
		addResponseFormatCompletions(request)
	}
	
	/**
	 * Add domain class completions using official GORM utilities
	 */
	private void addDomainCompletions(CompletionRequest request) {
		try {
			// Use GrailsHelperIntegration for official GORM instance methods
			List<String> gormInstanceMethods = GrailsHelperIntegration.getGormInstanceMethods()
			gormInstanceMethods.each { method ->
				//request.addCompletion(method, CompletionItemKind.Method, 'GORM Instance Method')
			}
			
			// Use GrailsHelperIntegration for official GORM static methods
			List<String> gormStaticMethods = GrailsHelperIntegration.getGormStaticMethods()
			gormStaticMethods.each { method ->
				//request.addCompletion(method, CompletionItemKind.Method, 'GORM Static Method')
			}
		} catch (Exception e) {
			logDebug("Error getting GORM completions: %s", e.message)
			// Fallback to basic GORM completions
			// ['save', 'delete', 'validate', 'hasErrors'].each { method ->
			//request.addCompletion(method, CompletionItemKind.Method, 'GORM Instance Method')
			//}
			// ['get', 'load', 'findBy', 'findAllBy', 'list'].each { method ->
			//request.addCompletion(method, CompletionItemKind.Method, 'GORM Static Method')
			//}
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
			//			if (matchesPrefix(property, request.prefix)) {
			//				request.addCompletion(property, CompletionItemKind.Property,
			//						"Domain property: ${description}")
			//			}
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
			//			if (matchesPrefix(property, request.prefix)) {
			//				request.addCompletion(property, CompletionItemKind.Property,
			//						"Service property: ${description}")
			//			}
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
			//			if (matchesPrefix(property, request.prefix)) {
			//				request.addCompletion(property, CompletionItemKind.Property,
			//						"TagLib property: ${description}")
			//			}
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
			//			if (matchesPrefix(method, request.prefix)) {
			//				//request.addCompletion(method, CompletionItemKind.Method,
			//						"Command method: ${description}")
			//			}
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
			//			if (matchesPrefix(property, request.prefix)) {
			//				request.addCompletion(property, CompletionItemKind.Property,
			//						"Job property: ${description}")
			//			}
		}
		
		// Job methods
		//		if (matchesPrefix('execute', request.prefix)) {
		//			request.addCompletion('execute', CompletionItemKind.Method,
		//					'Job execution method')
		//		}
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
			//			if (matchesPrefix(method, request.prefix)) {
			//				//request.addCompletion(method, CompletionItemKind.Method,
			//						"Interceptor method: ${description}")
			//			}
		}
		
		// Interceptor properties
		Map<String, String> interceptorProperties = [
				'match' : 'URL matching configuration',
				'except': 'URL exclusion configuration'
		]
		
		interceptorProperties.each { property, description ->
			//			if (matchesPrefix(property, request.prefix)) {
			//				request.addCompletion(property, CompletionItemKind.Property,
			//						"Interceptor property: ${description}")
			//			}
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
			//request.addCompletion(annotation, CompletionItemKind.Class, 'Grails annotation')
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
			//			if (matchesPrefix(object, request.prefix)) {
			//				request.addCompletion(object, CompletionItemKind.Variable,
			//						"Grails object: ${description}")
			//			}
		}
	}
	
	// Helper methods for specific completion types
	
	private void addActionMethodPatterns(CompletionRequest request) {
		List<String> actionPatterns = [
				'index', 'show', 'create', 'save', 'edit', 'update', 'delete'
		]
		
		actionPatterns.each { pattern ->
			//request.addCompletion(pattern, CompletionItemKind.Method, 'Controller action pattern')
		}
	}
	
	private void addResponseFormatCompletions(CompletionRequest request) {
		List<String> formats = [
				'html', 'json', 'xml', 'text', 'csv', 'pdf'
		]
		
		formats.each { format ->
			//request.addCompletion(format, CompletionItemKind.EnumMember, 'Response format')
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
			//			if (matchesPrefix(constraint, request.prefix)) {
			//				request.addCompletion(constraint, CompletionItemKind.Property,
			//						"Constraint: ${description}")
			//			}
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
			//			if (matchesPrefix(mapping, request.prefix)) {
			//				request.addCompletion(mapping, CompletionItemKind.Property,
			//						"Mapping: ${description}")
			//			}
		}
	}
	
	private void addTransactionAnnotations(CompletionRequest request) {
		List<String> transactionAnnotations = [
				'@Transactional', '@ReadOnly', '@NotTransactional'
		]
		
		transactionAnnotations.each { annotation ->
			//request.addCompletion(annotation, CompletionItemKind.Class, 'Transaction annotation')
		}
	}
	
	private void addTagPatterns(CompletionRequest request) {
		List<String> tagPatterns = [
				'def tagName = { attrs, body ->', 'def tagName = { attrs ->',
				'out <<', 'attrs.', 'body()'
		]
		
		tagPatterns.each { pattern ->
			//request.addCompletion(pattern, CompletionItemKind.Snippet, 'Tag pattern')
		}
	}
}