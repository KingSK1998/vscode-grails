package kingsk.grails.lsp.utils

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import kingsk.grails.lsp.model.TextFile
import org.apache.groovy.ast.tools.MethodNodeUtils
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.control.SourceUnit

import java.util.regex.Pattern

/** Static constants + basic checks */
class GrailsUtils {
	
	// === Grails app directory structure ===
	static final String GRAILS_APP_DIR = "\\grails-app"
	static final String CONTROLLERS_PATH = "$GRAILS_APP_DIR\\controllers"
	static final String SERVICES_PATH = "$GRAILS_APP_DIR\\services"
	static final String DOMAINS_PATH = "$GRAILS_APP_DIR\\domains"
	static final String VIEWS_PATH = "$GRAILS_APP_DIR\\views"
	static final String TAGLIBS_PATH = "$GRAILS_APP_DIR\\taglib"
	static final String INIT_PATH = "$GRAILS_APP_DIR\\init"
	static final String CONF_PATH = "$GRAILS_APP_DIR\\conf"
	static final String JOBS_PATH = "$GRAILS_APP_DIR\\jobs"
	static final String SRC_PATH = "\\src\\main\\groovy"
	static final String TEST_PATH = "\\src\\test"
	
	// === Grails Artefacts Annotations ===
	static final String ANNOTATION_ARTEFACT = "grails.artefact.Artefact"
	static final String ANNOTATION_ENHANCED = "grails.artefact.Enhanced"
	static final String ANNOTATION_TRANSACTIONAL = "grails.gorm.transactions.Transactional"
	
	static final String ANNOTATION_CONTROLLER = "grails.artefact.Controller"
	static final String ANNOTATION_SERVICE = "grails.artefact.Service"
	static final String ANNOTATION_TAGLIB = "grails.artefact.TagLib"
	static final String ANNOTATION_INTERCEPTOR = "grails.artefact.Interceptor"
	
	static final String ANNOTATION_WEB_CONTROLLER = "grails.web.Controller"
	static final String ANNOTATION_WEB_DATA_BINDING = "grails.web.databinding.WebDataBinding"
	
	// === DOMAIN ANNOTATIONS ===
	static final String ANNOTATION_DOMAIN = "grails.artefact.DomainClass"
	static final String ANNOTATION_GORM_ENTITY = "grails.gorm.annotation.Entity"
	static final String ANNOTATION_PERSISTENCE_ENTITY = "grails.persistence.Entity"
	static final String ANNOTATION_GORM_VALIDATEABLE = "org.grails.datastore.gorm.GormValidateable"
	
	// === Grails 7+ Specific Annotations ===
	static final String ANNOTATION_SPRING_COMPONENT = "org.springframework.stereotype.Component"
	static final String ANNOTATION_SPRING_SERVICE = "org.springframework.stereotype.Service"
	static final String ANNOTATION_SPRING_CONTROLLER = "org.springframework.stereotype.Controller"
	static final String ANNOTATION_SPRING_REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
	static final String ANNOTATION_SPRING_REPOSITORY = "org.springframework.stereotype.Repository"
	static final String ANNOTATION_SPRING_CONFIGURATION = "org.springframework.context.annotation.Configuration"
	static final String ANNOTATION_SPRING_BOOT_APPLICATION = "org.springframework.boot.autoconfigure.SpringBootApplication"
	static final String ANNOTATION_AUTOWIRED = "org.springframework.beans.factory.annotation.Autowired"
	
	// === Grails keywords and artifacts ===
	static final String SUFFIX_URL_MAPPINGS = "UrlMappings"
	static final String SUFFIX_APPLICATION = "Application"
	static final String SUFFIX_BOOTSTRAP = "Bootstrap"
	static final String SUFFIX_CONTROLLER = "Controller"
	static final String SUFFIX_SERVICE = "Service"
	static final String SUFFIX_DOMAIN = "Domain"
	static final String SUFFIX_TAGLIB = "TagLib"
	static final String SUFFIX_INTERCEPTOR = "Interceptor"
	static final String SUFFIX_JOB = "Job"
	
	
	// === Language specific or meta-programming tokens ===
	static final String GRAILS_ACTION_RETURN_TYPE = "org.springframework.web.servlet.ModelAndView"
	
	// === Patterns ===
	/** Matches <code>new &lt;word&gt;</code> (case-insensitive) at end of line. */
	static final Pattern PATTERN_CONSTRUCTOR_CALL = ~/(?i).*new\s+\w*$/
	
	// === LSP Configs ===
	static final String GRAILS_LSP = "grailsLsp"
	static final String JAVA_HOME = "javaHome"
	static final String COMPILER_VERBOSE = "compilerVerbose"
	static final String PROJECT_TARGET_DIR = "projectTargetDir"
	static final String DIAGNOSTICS_ENABLED = "diagnosticsEnabled"
	static final String EXPERIMENTAL_FEATURES = "experimentalFeatures"
	static final int DEFAULT_COMPILATION_PHASE = 0
	static final int DEFAULT_COMPLETION_ITEM_LIMIT = 100
	static final int MAX_COMPLETION_ITEM_LIMIT = 1000
	static final String DEFAULT_COMPLETION_LEVEL = "STANDARD"
	static final String DUMMY_COMPLETION_IDENTIFIER = "__GRAILS_DUMMY_COMPLETION__"
	static final String DUMMY_COMPLETION_CONSTRUCTOR = "__GRAILS_DUMMY_COMPLETION__()"
	
	// === Types ===
	static final String TYPE_OBJECT = "java.lang.Object"
	static final String TYPE_LONG = "java.lang.Long"
	static final String TYPE_BOOLEAN = "java.lang.Boolean"
	static final String TYPE_STRING = "java.lang.String"
	static final String TYPE_INTEGER = "java.lang.Integer"
	static final String TYPE_DOUBLE = "java.lang.Double"
	static final String TYPE_MAP = "java.util.Map"
	static final String TYPE_VOID = "void"
	
	// Java libs
	static final String JAVA_LANG = "java.lang"
	static final String JAVA_UTIL = "java.util"
	static final String JAVA_IO = "java.io"
	static final String JAVA_NET = "java.net"
	
	
	// Groovy libs
	static final String GROOVY_LANG = "groovy.lang"
	static final String GROOVY_UTIL = "groovy.util"
	
	// Default libs available in Groovy or Grails
	static final List<String> JAVA_INBUILT_LIBS = [JAVA_LANG, JAVA_UTIL, JAVA_IO, JAVA_NET]
	static final List<String> GROOVY_INBUILT_LIBS = [GROOVY_LANG, GROOVY_UTIL]
	static final List<String> DEFAULT_LIBS = JAVA_INBUILT_LIBS + GROOVY_INBUILT_LIBS
	
	static final String ANNOTATION_DEPRECATED = "java.lang.Deprecated"
	
	// === GORM Dynamic Finders ===
	
	static final List<String> GORM_DYNAMIC_FINDERS = ["findBy", "findAllBy", "countBy", "existsBy"]
	
	// === GORM Static Methods ===
	
	static boolean isControllerAnnotation(String annotationName) {
		return annotationName.endsWith(SUFFIX_CONTROLLER) || annotationName == ANNOTATION_CONTROLLER
	}
	
	static boolean isServiceAnnotation(String annotationName) {
		return annotationName.endsWith(SUFFIX_SERVICE) || annotationName == ANNOTATION_SERVICE
	}
	
	static boolean isTagLibAnnotation(String annotationName) {
		return annotationName.endsWith(SUFFIX_TAGLIB) || annotationName == ANNOTATION_TAGLIB
	}
	
	static boolean isInterceptorAnnotation(String annotationName) {
		return annotationName.endsWith(SUFFIX_INTERCEPTOR) || annotationName == ANNOTATION_INTERCEPTOR
	}
	
	
	// === Utility Methods ===
	
	static boolean isNodeDeprecated(ASTNode node) {
		if (!node) return false
		if (node instanceof AnnotatedNode) {
			return node.annotations?.any { ANNOTATION_DEPRECATED == it.classNode?.name }
		}
		return false
	}
	
	// === Grails Core utility methods ===
	
	// Generic field finder
	static FieldNode getStaticField(ClassNode node, String fieldName) {
		return node.fields?.find { it.name == fieldName && it.isStatic() }
	}
	
	static FieldNode getInstanceField(ClassNode node, String fieldName) {
		return node.fields?.find { it.name == fieldName && !it.isStatic() }
	}
	
	static MethodNode getMethod(ClassNode node, String methodName) {
		return node.allDeclaredMethods.find { it.name == methodName }
	}
	
	static List<MethodNode> getMethods(ClassNode node, @ClosureParams(value = SimpleType.class, options = "MethodNode") Closure<Boolean> filter) {
		return node.allDeclaredMethods.findAll(filter)
	}
	
	static List<FieldNode> getFields(ClassNode node, @ClosureParams(value = SimpleType.class, options = "FieldNode") Closure<Boolean> filter) {
		return node.fields?.findAll(filter) ?: []
	}
	
	// Extract string value extraction logic for reusability
	static String extractStringValue(Expression expression) {
		return expression?.text?.replaceAll(/['"]/, '')
	}
	
	static String getFieldStringValue(ClassNode node, String fieldName, String defaultValue = null) {
		FieldNode field = getStaticField(node, fieldName)
		return field?.initialExpression ?
				(extractStringValue(field.initialExpression) ?: defaultValue) :
				defaultValue
	}
	
	// Generic boolean checker for static fields
	static boolean hasStaticField(ClassNode node, String fieldName) {
		return getStaticField(node, fieldName) != null
	}
	
	// === Controller helper methods ===
	
	static FieldNode getDefaultActionField(ClassNode node) {
		return getStaticField(node, 'defaultAction')
	}
	
	static FieldNode getAllowedMethodsField(ClassNode node) {
		return getStaticField(node, 'allowedMethods')
	}
	
	static String getDefaultAction(ClassNode node) {
		return getFieldStringValue(node, 'defaultAction', 'index')
	}
	
	static MethodNode getDefaultControllerAction(ClassNode node) {
		String defaultActionName = getDefaultAction(node)
		return getMethod(node, defaultActionName)
	}
	
	static MethodNode getControllerAction(ClassNode node, String actionName) {
		return getMethods(node) { it ->
			!it.isStatic() && it.isPublic() && it.name == actionName
		}?.first()
	}
	
	static List<MethodNode> getControllerActions(ClassNode node) {
		return getMethods(node) { method ->
			!method.isStatic() &&
					method.isPublic() &&
					!method.name.startsWith('_') &&
					!isGormLifecycleMethod(method.name)
		}
	}
	
	// Boolean convenience methods
	static boolean hasAllowedMethods(ClassNode node) { hasStaticField(node, 'allowedMethods') }
	
	static boolean hasDefaultAction(ClassNode node) { hasStaticField(node, 'defaultAction') }
	
	// === Service helper methods ===
	
	static FieldNode getScopeField(ClassNode node) { getStaticField(node, 'scope') }
	
	static FieldNode getTransactionalField(ClassNode node) { getStaticField(node, 'transactional') }
	
	static FieldNode getLazyInitField(ClassNode node) { getStaticField(node, 'lazyInit') }
	
	static String getServiceScope(ClassNode node) {
		return getFieldStringValue(node, 'scope', 'singleton')
	}
	
	static List<FieldNode> getInjectedServices(ClassNode node) {
		return getFields(node) { isDependencyInjection(it) }
	}
	
	static FieldNode getInjectedService(ClassNode node, String serviceName) {
		return getFields(node) { it.name == serviceName && isDependencyInjection(it) }?.first()
	}
	
	// Boolean convenience methods
	static boolean hasLazyInit(ClassNode node) { hasStaticField(node, 'lazyInit') }
	
	static boolean hasCustomScope(ClassNode node) { hasStaticField(node, 'scope') }
	
	static boolean hasScope(ClassNode node) { hasStaticField(node, 'scope') }
	
	
	// === TagLib helper methods ===
	
	static FieldNode getNamespaceField(ClassNode node) { getStaticField(node, 'namespace') }
	
	static FieldNode getDefaultEncodeAsField(ClassNode node) { getStaticField(node, 'defaultEncodeAs') }
	
	static FieldNode getEncodeAsForTagsField(ClassNode node) { getStaticField(node, 'encodeAsForTags') }
	
	static String getTaglibNamespace(ClassNode node) {
		return getFieldStringValue(node, 'namespace', 'g')
	}
	
	static List<MethodNode> getTagDefinitions(ClassNode node) {
		return getMethods(node) { isTagDefinition(it) }
	}
	
	static MethodNode getTagDefinition(ClassNode node, String tagName) {
		return getMethods(node) { it.name == tagName && isTagDefinition(it) }?.first()
	}
	
	// Boolean convenience methods
	static boolean hasCustomNamespace(ClassNode node) { hasStaticField(node, 'namespace') }
	
	static boolean hasDefaultEncodeAs(ClassNode node) { hasStaticField(node, 'defaultEncodeAs') }
	
	static boolean hasEncodeAsForTags(ClassNode node) { hasStaticField(node, 'encodeAsForTags') }
	
	// === Domain helper methods === //
	
	
	static FieldNode getConstraintsField(ClassNode node) { getStaticField(node, 'constraints') }
	
	static FieldNode getMappingField(ClassNode node) { getStaticField(node, 'mapping') }
	
	static FieldNode getNamedQueriesField(ClassNode node) { getStaticField(node, 'namedQueries') }
	
	static FieldNode getHasManyField(ClassNode node) { getStaticField(node, 'hasMany') }
	
	static FieldNode getBelongsToField(ClassNode node) { getStaticField(node, 'belongsTo') }
	
	static FieldNode getHasOneField(ClassNode node) { getStaticField(node, 'hasOne') }
	
	static FieldNode getTransientsField(ClassNode node) { getStaticField(node, 'transients') }
	
	static FieldNode getFetchModeField(ClassNode node) { getStaticField(node, 'fetchMode') }
	
	static List<FieldNode> getDomainProperties(ClassNode node) {
		return getFields(node) { field ->
			!field.isStatic() &&
					!isGormTimestampProperty(field.name) &&
					!isVersionProperty(field.name) &&
					!isIdProperty(field.name) &&
					!isDependencyInjection(field)
		}
	}
	
	static FieldNode getDomainProperty(ClassNode node, String propertyName) {
		return getFields(node) {
			it.name == propertyName &&
					!it.isStatic() &&
					!isDependencyInjection(it)
		}?.first()
	}
	
	static List<MethodNode> getGormLifecycleMethods(ClassNode node) {
		return getMethods(node) { isGormLifecycleMethod(it.name) }
	}
	
	static MethodNode getGormLifecycleMethod(ClassNode node, String methodName) {
		return getMethods(node) {
			it.name == methodName && isGormLifecycleMethod(methodName)
		}?.first()
	}
	
	static List<MethodNode> getSecuredMethods(ClassNode node) {
		return getMethods(node) { hasSecurityAnnotation(it) }
	}
	
	static List<MethodNode> getTransactionalMethods(ClassNode node) {
		return getMethods(node) { method ->
			method.annotations?.any { it.classNode.name == 'Transactional' }
		}
	}
	
	// Boolean convenience methods using the DRY pattern
	static boolean hasNamedQueries(ClassNode node) { hasStaticField(node, 'namedQueries') }
	
	static boolean hasTransients(ClassNode node) { hasStaticField(node, 'transients') }
	
	static boolean hasFetchMode(ClassNode node) { hasStaticField(node, 'fetchMode') }
	
	static boolean hasConstraintsBlock(ClassNode node) { hasStaticField(node, 'constraints') }
	
	static boolean hasMappingBlock(ClassNode node) { hasStaticField(node, 'mapping') }
	
	static boolean hasRelationships(ClassNode node) {
		return ['hasMany', 'belongsTo', 'hasOne'].any { hasStaticField(node, it) }
	}
	
	static boolean hasTransactionalProperty(ClassNode node) {
		FieldNode transactionalField = getTransactionalField(node)
		if (!transactionalField) return true // Default is true for services
		return transactionalField.initialExpression?.text != 'false'
	}
	
	static boolean isTransactional(ClassNode node) {
		return hasTransactionalAnnotation(node) || hasTransactionalProperty(node)
	}
	
	static boolean hasSecurityAnnotation(MethodNode node) {
		return node?.annotations?.any {
			it.classNode.name in ['Secured', 'PreAuthorize', 'PostAuthorize', 'RolesAllowed']
		}
	}
	
	
	// === Utility predicates ===
	
	/**
	 * Returns true if the field represents a dependency injection (e.g. a service injected into a controller).
	 */
	static boolean isDependencyInjection(FieldNode node) {
		if (ASTUtils.hasInvalidSource(node)) return false
		if (!isControllerClass(node.declaringClass)) return false
		if (!isServiceClass(node.type)) return false
		return node.annotations.any { it.classNode.name == ANNOTATION_AUTOWIRED }
	}
	
	static boolean isGormTimestampProperty(String fieldName) {
		return fieldName in ['dateCreated', 'lastUpdated']
	}
	
	static boolean isVersionProperty(String fieldName) {
		return fieldName == 'version'
	}
	
	static boolean isIdProperty(String fieldName) {
		return fieldName == 'id'
	}
	
	static boolean isGormLifecycleMethod(String methodName) {
		return methodName in ['beforeInsert', 'afterInsert', 'beforeUpdate', 'afterUpdate',
		                      'beforeDelete', 'afterDelete', 'beforeValidate', 'afterValidate']
	}
	
	// TODO: Find annotation pattern for GORM
	static boolean isGormStaticMethod(String methodName) {
		return methodName.startsWith('findBy') || methodName.startsWith('findAllBy') ||
				methodName.startsWith('countBy') || methodName in ['withCriteria', 'createCriteria']
	}
	
	static boolean isTagDefinition(MethodNode methodNode) {
		return !methodNode.isStatic() && methodNode.isPublic() &&
				methodNode.parameters.length <= 2 // attrs, body
	}
	
	// --- Constants ---
	
	static final List<String> DOMAIN_INTERFACES = [
			"org.grails.datastore.gorm.GormEntity",
			"grails.web.databinding.WebDataBinding",
			"grails.artefact.DomainClass",
			"grails.gorm.Entity"
	]
	static final List<String> DOMAIN_ANNOTATIONS = [
			"grails.gorm.annotation.Entity",
			"grails.persistence.Entity",
			"grails.artefact.Artefact",
			"grails.artefact.Enhanced"
	]
	
	static final List<String> CONTROLLER_INTERFACES = [
			"grails.artefact.controller.RestResponder",
			"grails.artefact.Controller",
			"grails.artefact.gsp.TagLibraryInvoker",
			"org.grails.compiler.web.converters.RenderConverterTrait"
	]
	static final List<String> CONTROLLER_ANNOTATIONS = [
			"grails.artefact.Artefact",
			"grails.artefact.Enhanced"
	]
	
	// Grails services rarely use marker interfaces
	static final List<String> SERVICE_INTERFACES = []
	static final List<String> SERVICE_ANNOTATIONS = [
			"grails.artefact.Service"
	]
	
	static final List<String> TAGLIB_ANNOTATIONS = [
			"grails.artefact.TagLib",
			"grails.artefact.Artefact",
			"grails.artefact.Enhanced"
	]
	static final List<String> TAGLIB_INTERFACES = [
			"grails.artefact.TagLib"
	]
	
	static final List<String> INTERCEPTOR_ANNOTATIONS = [
			"grails.artefact.Interceptor",
			"grails.artefact.Artefact",
			"grails.artefact.Enhanced"
	]
	static final List<String> INTERCEPTOR_INTERFACES = [
			"grails.artefact.Interceptor"
	]
	
	static final List<String> APPLICATION_ANNOTATIONS = [
			"org.springframework.boot.autoconfigure.SpringBootApplication",
			"grails.boot.config.GrailsAutoConfiguration"
	]
	static final List<String> APPLICATION_INTERFACES = [
			"grails.boot.config.GrailsAutoConfiguration"
	]
	
	// --- Shared Helpers ---
	
	static boolean hasInterface(ClassNode node, List<String> interfaceNames) {
		return node?.interfaces?.any { it.name in interfaceNames }
	}
	
	static boolean hasAnnotation(ClassNode node, List<String> annotationNames) {
		return node?.annotations?.any { it.classNode.name in annotationNames }
	}
	
	// --- Artefact Type Checks ---
	
	/** Returns true if the given class node is a Domain Artefact, false otherwise. */
	static boolean isDomainClass(ClassNode node, String uri = null) {
		return hasInterface(node, DOMAIN_INTERFACES)
				|| hasAnnotation(node, DOMAIN_ANNOTATIONS)
				|| isArtefactAnnotated(node, SUFFIX_DOMAIN)
				|| isInDomainDir(uri)
	}
	
	/** Returns true if the given class node is a Controller Artefact, false otherwise. */
	static boolean isControllerClass(ClassNode node, String uri = null) {
		return hasInterface(node, CONTROLLER_INTERFACES)
				|| hasAnnotation(node, CONTROLLER_ANNOTATIONS)
				|| isArtefactAnnotated(node, SUFFIX_CONTROLLER)
				|| isInControllerDir(uri)
	}
	
	/** Returns true if the given class node is a Service Artefact, false otherwise. */
	static boolean isServiceClass(ClassNode node, String uri = null) {
		return hasAnnotation(node, SERVICE_ANNOTATIONS)
				|| hasTransactionalAnnotation(node)
				|| isArtefactAnnotated(node, SUFFIX_SERVICE)
				|| isInServiceDir(uri)
				|| isLikelyService(node)
	}
	
	/** Returns true if the given class node is a TagLib Artefact, false otherwise. */
	static boolean isTagLibClass(ClassNode node, String uri = null) {
		return hasAnnotation(node, TAGLIB_ANNOTATIONS) ||
				hasInterface(node, TAGLIB_INTERFACES) ||
				isArtefactAnnotated(node, SUFFIX_TAGLIB) ||
				node.nameWithoutPackage.endsWith(SUFFIX_TAGLIB) ||
				(uri != null && isInTagLibDir(uri))
	}
	
	/** Returns true if the given class node is an Interceptor Artefact, false otherwise. */
	static boolean isInterceptorClass(ClassNode node, String uri = null) {
		return hasAnnotation(node, INTERCEPTOR_ANNOTATIONS) ||
				hasInterface(node, INTERCEPTOR_INTERFACES) ||
				isArtefactAnnotated(node, SUFFIX_INTERCEPTOR) ||
				node.nameWithoutPackage.endsWith(SUFFIX_INTERCEPTOR) ||
				(uri != null && isInInterceptorDir(uri))
	}
	
	/** Returns true if the given class node is an Application Artefact, false otherwise. */
	static boolean isApplicationClass(ClassNode node, String uri = null) {
		return hasAnnotation(node, APPLICATION_ANNOTATIONS) ||
				hasInterface(node, APPLICATION_INTERFACES) ||
				node.nameWithoutPackage.endsWith(SUFFIX_APPLICATION) ||
				(uri != null && isInInitDir(uri))
	}
	
	/** Returns true if the given class node is a Job Artefact, false otherwise. */
	static boolean isJobClass(ClassNode node, String uri = null) {
		return node?.nameWithoutPackage?.endsWith(SUFFIX_JOB) || isInJobsDir(uri)
	}
	
	
	static boolean hasTransactionalAnnotation(AnnotatedNode node) {
		return node?.annotations?.any { it.classNode.name == ANNOTATION_TRANSACTIONAL }
	}
	
	/** Returns true if the given class node is a Grails Artefact, false otherwise. */
	// @Artefact(value=...) Helper
	static boolean isArtefactAnnotated(ClassNode node, String type) {
		def artefactAnnotation = node?.annotations?.find {
			it.classNode.name == ANNOTATION_ARTEFACT
		}
		if (!artefactAnnotation) return false
		
		def valueExpr = artefactAnnotation?.members?.get("value")
		return valueExpr?.text == type
	}
	
	/** Returns true if the given class node is a confirmed Grails Artefact, false otherwise. */
	// Aggregate checker
	static boolean isGrailsArtefact(ClassNode node, String uri = null) {
		return isDomainClass(node, uri) || isControllerClass(node, uri) || isServiceClass(node, uri)
				|| isTagLibClass(node, uri) || isInterceptorClass(node, uri) || isApplicationClass(node, uri)
				|| isJobClass(node, uri)
	}
	
	static boolean isDomainAnnotation(String annotationName) {
		return [ANNOTATION_GORM_ENTITY, ANNOTATION_PERSISTENCE_ENTITY].any { it == annotationName }
	}
	
	/** Returns true if the given class node is a Domain Artefact, false otherwise. */
	static boolean hasDomainAnnotation(ClassNode node) {
		return node?.annotations?.any {
			it.classNode.name in [ANNOTATION_GORM_ENTITY, ANNOTATION_PERSISTENCE_ENTITY]
		}
	}
	
	/** Returns true if the given class node is a Possibly Service Artefact, false otherwise. */
	static boolean isLikelyService(ClassNode node) {
		return node?.nameWithoutPackage?.endsWith(SUFFIX_SERVICE)
	}
	
	// --- GrailsPathUtil
	// return uri?.replace('\\', '/')?.contains('/grails-app/domain/')
	
	static boolean isInGrailsAppDir(String uri) {
		return uri?.contains(GRAILS_APP_DIR)
	}
	
	static boolean isInDomainDir(String uri) {
		return uri?.contains(DOMAINS_PATH)
	}
	
	static boolean isInServiceDir(String uri) {
		return uri?.contains(SERVICES_PATH)
	}
	
	static boolean isInControllerDir(String uri) {
		return uri?.contains(CONTROLLERS_PATH)
	}
	
	static boolean isInTagLibDir(String uri) {
		return uri?.contains(TAGLIBS_PATH)
	}
	
	static boolean isInInterceptorDir(String uri) {
		return isInControllerDir(uri)
	}
	
	static boolean isInInitDir(String uri) {
		return uri?.contains(INIT_PATH)
	}
	
	static boolean isInJobsDir(String uri) {
		return uri?.contains(JOBS_PATH)
	}
	
	static boolean isInTestDir(String uri) {
		return uri?.contains(TEST_PATH)
	}
	
	static boolean isInViewsDir(String uri) {
		return uri?.contains(VIEWS_PATH)
	}
	
	static boolean isInConfDir(String uri) {
		return uri?.contains(CONF_PATH)
	}
	
	static boolean isInSrcDir(String uri) {
		return uri?.contains(SRC_PATH)
	}
	
	static boolean isTestSpec(String uri) {
		return isInTestDir(uri) && uri?.endsWith("Spec.groovy")
	}
	
	static boolean isTestClass(String uri) {
		return isInTestDir(uri) && uri?.endsWith("Test.groovy")
	}
	
	// --- Grails Controller Utility ---
	
	static final String ANNOTATION_GENERATED = "groovy.transform.Generated"
	static final List<String> ANNOTATION_GRAILS_GENERATED = [
			"org.codehaus.groovy.transform.trait.Traits\$TraitBridge"
	]
	
	static boolean isGroovyGenerated(AnnotationNode node) {
		node.any { it.classNode.name == ANNOTATION_GENERATED }
	}
	
	protected static boolean hasAnnotation(MethodNode node, List<String> annotationNames) {
		return node?.annotations?.any { it.classNode.name in annotationNames }
	}
	
	private static boolean isGrailsGeneratedProperty(MethodNode node) {
		return hasAnnotation(node, ANNOTATION_GRAILS_GENERATED)
	}
	
	private static boolean isValidGrailsProperty(MethodNode node) {
		if (!isGrailsArtefact(node.declaringClass)) return false
		return isGrailsGeneratedProperty(node)
	}
	
	/**
	 * Returns true if the method is public, a getter, and has Grails synthetic annotations.
	 */
	static boolean isGrailsSyntheticProperty(MethodNode node) {
		if (!node) return false
		return node.public && MethodNodeUtils.isGetterCandidate(node) && isGrailsGeneratedProperty(node)
	}
	
	/**
	 * Returns the property name from the method if valid, null otherwise.
	 */
	static String getMethodAsPropertyName(MethodNode node) {
		if (!node) return null
		return MethodNodeUtils.getPropertyName(node)
	}
	
	/**
	 * Returns the Grails-injected property name if the method is valid, null otherwise.
	 */
	static String getGrailsPropertyName(MethodNode node) {
		return isGrailsSyntheticProperty(node) ? getMethodAsPropertyName(node) : null
	}
	
	/**
	 * Returns the UPPERCASE Grails controller symbol name, or null if not valid.
	 */
	static String getGrailsControllerPropertySymbol(MethodNode node) {
		String name = getGrailsPropertyName(node)
		return name ? name.toUpperCase() : null
	}
	
	/**
	 * Returns the URI from the class node if available, null otherwise.
	 */
	static String resolveFallbackUriFromClassNode(ClassNode classNode) {
		if (!classNode) return null
		
		def sourceUnit = classNode.compileUnit?.getScriptSourceLocation(classNode.name)
				?: classNode.module?.context
		return normalizedSourceUnitURI(sourceUnit)
	}
	
	private static String normalizedSourceUnitURI(SourceUnit sourceUnit) {
		if (!sourceUnit) return null
		return TextFile.normalizePath(sourceUnit.name)
	}
	
	static boolean isDummyPrefix(String prefix) {
		if (!prefix) return false
		return [DUMMY_COMPLETION_IDENTIFIER, DUMMY_COMPLETION_CONSTRUCTOR].any { it == prefix }
	}
}
