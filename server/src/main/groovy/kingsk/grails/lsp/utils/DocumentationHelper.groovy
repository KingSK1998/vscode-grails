package kingsk.grails.lsp.utils

import groovy.lang.groovydoc.Groovydoc
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.DocumentationType
import kingsk.grails.lsp.model.GrailsArtifactType
import org.apache.groovy.ast.tools.MethodNodeUtils
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.tools.GenericsUtils
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind

import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Abstract documentation layer for Grails LSP.
 * Single interface for all documentation needs across providers.
 */
@Slf4j
class DocumentationHelper {
	
	// Main abstraction - single function to remember
	static MarkupContent getDocumentation(ASTNode node, GrailsService grailsService, DocumentationType type = DocumentationType.HOVER) {
		if (!grailsService?.project) return new MarkupContent(MarkupKind.MARKDOWN, "No project context available")
		return resolveDocumentation(node, grailsService.project.isGrailsProject, grailsService.visitor, type)
	}
	
	// Alternative with explicit parameters if you prefer
	static MarkupContent getDocumentation(ASTNode node, boolean isGrailsProject, GrailsASTVisitor visitor, DocumentationType type = DocumentationType.HOVER) {
		return resolveDocumentation(node, isGrailsProject, visitor, type)
	}
	
	// Simplified text-only version for cases that don't need markup
	static String getDocumentationText(ASTNode node, GrailsService grailsService, DocumentationType type = DocumentationType.HOVER) {
		if (!grailsService?.project) return new MarkupContent(MarkupKind.MARKDOWN, "No project context available")
		return getDocumentation(node, grailsService, type).value
	}
	
	static String getDocumentationText(ASTNode node, boolean isGrailsProject, GrailsASTVisitor visitor, DocumentationType type = DocumentationType.HOVER) {
		return getDocumentation(node, isGrailsProject, visitor, type).value
	}
	
	static boolean hasDocumentation(ASTNode node, GrailsService service) {
		if (!node) return false
		
		if (node instanceof AnnotatedNode && node.groovydoc.present) {
			return true
		}
		
		if (service.project.isGrailsProject) {
			return grailsArtifactToString(node, service.visitor.getURI(node))?.trim()
		}
		
		return false
	}
	
	// Core resolution logic
	private static MarkupContent resolveDocumentation(ASTNode node, boolean isGrailsProject, GrailsASTVisitor visitor, DocumentationType type) {
		if (!node) {
			return new MarkupContent(MarkupKind.MARKDOWN, "")
		}
		
		DocumentationBuilder builder = new DocumentationBuilder(type)
		
		// 1. Extract Groovydoc if available
		if (node instanceof AnnotatedNode) {
			builder.addGroovydoc(node.groovydoc)
		}
		
		// 2. Add Grails-specific context if available
		boolean hasGrailsContext = false
		if (isGrailsProject) {
			hasGrailsContext = builder.addGrailsContext(node, visitor.getURI(node))
		}
		
		// 3. Add general AST context if no Grails context was added
		if (!hasGrailsContext) {
			builder.addASTContext(node)
		}
		
		return builder.build()
	}
	
	// Internal builder for clean documentation assembly
	private static class DocumentationBuilder {
		private final StringBuilder content = new StringBuilder()
		private final DocumentationType type
		private boolean hasContent = false
		
		DocumentationBuilder(DocumentationType type) {
			this.type = type
		}
		
		DocumentationBuilder addGroovydoc(Groovydoc groovydoc) {
			String processed = GroovydocConverter.groovydocToDescription(groovydoc)
			if (processed?.trim()) {
				appendSection(processed)
			}
			return this
		}
		
		boolean addGrailsContext(ASTNode node, String uri = null) {
			String grailsInfo = grailsArtifactToString(node, uri)
			if (grailsInfo?.trim()) {
				appendSection(grailsInfo, getSectionTitle("grails"))
				return true
			}
			return false
		}
		
		DocumentationBuilder addASTContext(ASTNode node) {
			String astInfo = astNodeToString(node)
			if (astInfo?.trim()) {
				appendSection(astInfo, getSectionTitle("ast"))
			}
			return this
		}
		
		private void appendSection(String text, String title = null) {
			if (hasContent) {
				content.append(getSeparator())
			}
			
			if (title && shouldShowTitles()) {
				content.append("### ").append(title).append("\n\n")
			}
			
			content.append(text.trim())
			hasContent = true
		}
		
		private String getSectionTitle(String section) {
			switch (type) {
				case DocumentationType.COMPLETION:
					return section == "grails" ? "Grails Convention" : "Signature"
				case DocumentationType.HOVER:
					return section == "grails" ? "Grails Context" : "Details"
				case DocumentationType.DIAGNOSTIC:
					return section == "grails" ? "Grails Validation" : "Type Info"
				default:
					return null
			}
		}
		
		private String getSeparator() {
			switch (type) {
				case DocumentationType.COMPLETION:
					return "\n\n"
				case DocumentationType.HOVER:
					return "\n\n---\n\n"
				case DocumentationType.DIAGNOSTIC:
					return "\n\n"
				default:
					return "\n\n"
			}
		}
		
		private boolean shouldShowTitles() {
			return type == DocumentationType.HOVER || type == DocumentationType.DIAGNOSTIC
		}
		
		MarkupContent build() {
			return new MarkupContent(MarkupKind.MARKDOWN, content.toString())
		}
	}
	
	
	// === API ===
	
	static String getContentFromJavadocJar(DependencyNode dependency, String className) {
		if (!dependency?.javadocFileClasspath || !dependency?.jarFileClasspath || !className?.trim()) {
			log.warn("Invalid input: missing jar path or class name [${dependency?.name}, ${className}]")
			return ""
		}
		
		File javadocJar = dependency?.javadocFileClasspath
		if (!javadocJar.exists() || !javadocJar.canRead()) {
			log.warn("Javadoc jar not accessible: ${javadocJar.absolutePath}")
			return ""
		}
		
		try (JarFile jarFile = new JarFile(javadocJar)) {
			String basePath = className.replace('.', '/') + ".html"
			List<String> alternatives = [
					basePath,
					className.replace('$', '.').replace('.', '/') + ".html",
					className.contains('.') ? className.substring(0, className.lastIndexOf('.')).replace('.', '/') + ".html" : null
			].findAll { it }
			// remove nulls
			
			JarEntry entry = alternatives.collect { jarFile.getJarEntry(it) }.find { it != null }
			
			if (!entry) {
				log.warn("Javadoc not found for class '${className}' in ${dependency}")
				return ""
			}
			
			return jarFile.getInputStream(entry).withReader("UTF-8") { it.text }
		} catch (IOException e) {
			log.error("Error reading Javadoc for ${className} in ${dependency}", e)
			return ""
		}
	}
	
	static String astNodeToString(ASTNode node) {
		if (!node) return ""
		
		switch (node) {
			case ClassNode: return classNodeToString(node)
			case ConstructorNode: // Fallthrough
			case MethodNode:
				return MethodNodeUtils.methodDescriptor(node, true)
			case FieldNode: return fieldNodeToString(node)
			case PropertyNode: return node.field ? astNodeToString(node.field) : propertyNodeToString(node)
			case Parameter: return AstToTextHelper.getParameterText(node)
			case AnnotationNode: return annotationNodeToString(node)
			case ImportNode: return node.text
			case PackageNode: return node.text
			case Variable: return variableToString(node as Variable)
			default: return node.text ?: node.toString()
		}
	}
	
	private static String classNodeToString(ClassNode node) {
		if (!node) return ""
		
		StringBuilder result = new StringBuilder()
		
		// Package declaration
		if (node.packageName) {
			// result.append(node.package.text + "\n")
			result.append("package ${node.packageName}\n\n")
		}
		
		// Annotations (if any)
		if (node.annotations) {
			node.annotations.each { annotation ->
				result.append(annotationNodeToString(annotation)).append("\n")
			}
			if (!node.annotations.empty) return result.append("\n")
		}
		
		// Access modifiers & class declaration
		String modifiers = AstToTextHelper.getModifiersText(node.modifiers)
		if (modifiers) result.append(modifiers).append(' ')
		
		// Class type and name
		result.append(classObjectTypeString(node)).append(' ')
		result.append(node.nameWithoutPackage)
		
		// Generic parameters
		if (node.genericsTypes) {
			String generics = GenericsUtils.toGenericTypesString(node.genericsTypes)
			result.append(generics)
		}
		
		// Superclass
		if (node.superClass && node.superClass.name != GrailsUtils.TYPE_OBJECT) {
			result.append(" extends ${node.superClass.nameWithoutPackage}")
		}
		
		// Interfaces
		if (node.interfaces) {
			String keyword = node.interface ? " extends " : " implements "
			result.append(keyword)
			result.append(node.interfaces.collect { it.nameWithoutPackage }.join(', '))
		}
		
		return result.toString().trim()
	}
	
	private static String fieldNodeToString(FieldNode node) {
		if (!node) return ""
		
		StringBuilder result = new StringBuilder()
		
		// Annotations
		if (node.annotations) {
			node.annotations.each { annotation ->
				result.append(annotationNodeToString(annotation)).append("\n")
			}
		}
		
		// Modifiers
		result.append(AstToTextHelper.getModifiersText(node.modifiers)).append(' ')
		// Type and name
		result.append(AstToTextHelper.getClassText(node.originType ?: node.type))
		result.append(' ').append(node.name)
		
		// initial value expression (if meaningful to show)
		if (node.initialValueExpression && !(node.initialValueExpression instanceof ClosureExpression)
				&& node.initialValueExpression.text.length() < 50) {
			result.append(" = ").append(node.initialValueExpression.text)
		}
		return result.toString().trim()
	}
	
	private static String propertyNodeToString(PropertyNode node) {
		if (!node) return ""
		
		StringBuilder result = new StringBuilder()
		
		// For properties without backing field, show property info
		String modifiers = AstToTextHelper.getModifiersText(node.modifiers)
		if (modifiers) result.append(modifiers).append(' ')
		
		result.append(AstToTextHelper.getClassText(node.type))
		result.append(' ').append(node.name)
		
		// Show getter/setter info if custom
		if (node.getterBlock && !node.synthetic) {
			result.append(" [custom getter]")
		}
		if (node.setterBlock && !node.synthetic) {
			result.append(" [custom setter]")
		}
		
		return result.toString().trim()
	}
	
	private static String annotationNodeToString(AnnotationNode node) {
		if (!node) return ""
		
		StringBuilder result = new StringBuilder("@")
		result.append(node.classNode.nameWithoutPackage)
		
		// Annotation parameters
		if (node.members && !node.members.isEmpty()) {
			result.append('(')
			List<String> params = []
			node.members.each { key, value ->
				String paramValue = value.text
				if (paramValue.length() > 30) paramValue = paramValue.substring(0, 27) + "..."
				params.add("${key}=${paramValue}")
			}
			result.append(params.join(', '))
			result.append(')')
		}
		
		return result.toString().trim()
	}
	
	private static String classObjectTypeString(ClassNode node) {
		if (node.interface) return "interface"
		if (node.enum) return "enum"
		if (node.record) return "record"
		if (node.annotationDefinition) return "@interface"
		return "class"
	}
	
	private static String variableToString(Variable variable) {
		if (!variable) return ""
		String typeName = variable.type?.nameWithoutPackage ?: "def"
		return "${typeName} ${variable.name}"
	}
	
	// === Grails Artifact Documentation Builder ===
	
	/**
	 * Main entry point for generating Grails artifact documentation
	 * @param node The AST node to generate documentation for
	 * @param visitor The Grails AST visitor for context
	 * @return Markdown documentation string or null if no Grails-specific info available
	 */
	static String grailsArtifactToString(ASTNode node, String uri = null) {
		if (!node) return null
		
		switch (node) {
			case ClassNode: return buildClassDocumentation(node, uri)
			case PropertyNode: return buildPropertyDocumentation(node, uri)
			case FieldNode: return buildFieldDocumentation(node, uri)
			case MethodNode: return buildMethodDocumentation(node, uri)
			default: return null
		}
	}
	
	// === Private Implementation ===
	
	private static String buildClassDocumentation(ClassNode node, String uri = null) {
		GrailsArtifactType artifactType = GrailsArtefactUtils.getGrailsArtifactType(node, uri)
		
		switch (artifactType) {
			case GrailsArtifactType.CONTROLLER: return buildControllerDocumentation(node)
			case GrailsArtifactType.SERVICE: return buildServiceDocumentation(node)
			case GrailsArtifactType.DOMAIN: return buildDomainDocumentation(node)
			case GrailsArtifactType.INTERCEPTOR: return buildInterceptorDocumentation(node)
			case GrailsArtifactType.TAGLIB: return buildTaglibDocumentation(node)
			case GrailsArtifactType.COMMAND: return buildCommandDocumentation(node)
			case GrailsArtifactType.JOB: return buildJobDocumentation(node)
			case GrailsArtifactType.UNKNOWN: return null
			case GrailsArtifactType.NOT_SUPPORTED: return null
			case GrailsArtifactType.INCORRECT_PACKAGE: return null
			case GrailsArtifactType.POGO: return null
			default: return buildGenericArtifactDocumentation(artifactType)
		}
	}
	
	private static String buildControllerDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Controller:** `${node.nameWithoutPackage}`\n\n")
		
		String controllerName = GrailsArtefactUtils.getControllerName(node)
		doc.append("**URL Mapping:** `/${controllerName}/*`\n")
		
		// Check for custom defaultAction
		String defaultAction = GrailsUtils.getDefaultAction(node)
		doc.append("**Default Action:** `/${controllerName}/${defaultAction}`\n\n")
		
		// Package-based namespace
		String packageName = node.packageName
		if (packageName) {
			doc.append("**Package:** `${packageName}`\n\n")
		}
		
		// Special controller properties
		doc.append("**Controller Properties:**\n")
		if (GrailsUtils.hasAllowedMethods(node)) {
			doc.append("- `allowedMethods` - HTTP method restrictions ✓\n")
		}
		if (GrailsUtils.hasDefaultAction(node)) {
			doc.append("- `defaultAction` - Custom default action ✓\n")
		}
		if (GrailsUtils.hasScope(node)) {
			doc.append("- `scope` - Controller scope configuration ✓\n")
		}
		doc.append("\n")
		
		// Controller-specific features
		doc.append("**Features:**\n")
		doc.append("- Automatic request/response handling\n")
		doc.append("- Built-in parameter binding\n")
		doc.append("- Interceptor support\n")
		doc.append("- Automatic view resolution\n")
		doc.append("- Flash scope support\n")
		doc.append("- Command object binding\n\n")
		
		return doc.toString()
	}
	
	private static String buildServiceDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Service** `${node.nameWithoutPackage}`\n\n")
		
		// Check for @Transactional annotation or transactional property
		boolean isTransactional = GrailsUtils.isTransactional(node)
		doc.append("**Transactional:** ${isTransactional ? 'Yes (default)' : 'No'}\n")
		
		if (isTransactional) {
			doc.append("**Isolation:** Read Committed (default)\n")
			doc.append("**Propagation:** Required (default)\n")
		}
		
		// Service scope
		String scope = GrailsUtils.getServiceScope(node)
		doc.append("**Scope:** ${scope}\n\n")
		
		// Service-specific properties
		doc.append("**Service Properties:**\n")
		if (GrailsUtils.hasLazyInit(node)) {
			doc.append("- `lazyInit` - Lazy initialization ✓\n")
		}
		if (GrailsUtils.hasCustomScope(node)) {
			doc.append("- `scope` - Custom scope configuration ✓\n")
		}
		doc.append("\n")
		
		// Service-specific features
		doc.append("**Features:**\n")
		doc.append("- Dependency injection ready\n")
		doc.append("- Singleton by default\n")
		if (isTransactional) {
			doc.append("- Automatic transaction management\n")
			doc.append("- Rollback on unchecked exceptions\n")
		}
		doc.append("- Spring bean integration\n\n")
		
		return doc.toString()
	}
	
	private static String buildDomainDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Domain Class** `${node.nameWithoutPackage}`\n\n")
		
		// Table name convention - convert ClassName to class_name
		String tableName = convertToTableName(node.nameWithoutPackage)
		doc.append("**Database Table:** `${tableName}`\n")
		
		// Version and ID information
		doc.append("**Primary Key:** `id` (Long, auto-generated)\n")
		doc.append("**Version:** `version` (Long, optimistic locking)\n\n")
		
		// GORM features
		doc.append("**GORM Features:**\n")
		doc.append("- Dynamic finders (`findBy*`, `findAllBy*`)\n")
		doc.append("- Criteria queries (`withCriteria`)\n")
		doc.append("- Named queries (`namedQueries`)\n")
		doc.append("- Lifecycle events (`beforeInsert`, `afterUpdate`, etc.)\n")
		doc.append("- Validation constraints (`constraints`)\n")
		doc.append("- Custom mapping (`mapping`)\n")
		doc.append("- Automatic timestamping (`dateCreated`, `lastUpdated`)\n\n")
		
		// Check for domain-specific blocks
		doc.append("**Domain Properties:**\n")
		if (GrailsUtils.hasConstraintsBlock(node)) {
			doc.append("- `constraints` - Validation rules ✓\n")
		}
		if (GrailsUtils.hasMappingBlock(node)) {
			doc.append("- `mapping` - Custom ORM mapping ✓\n")
		}
		if (GrailsUtils.hasRelationships(node)) {
			doc.append("- Relationships (`hasMany`, `belongsTo`, `hasOne`) ✓\n")
		}
		if (GrailsUtils.hasNamedQueries(node)) {
			doc.append("- `namedQueries` - Predefined queries ✓\n")
		}
		if (GrailsUtils.hasTransients(node)) {
			doc.append("- `transients` - Non-persistent properties ✓\n")
		}
		if (GrailsUtils.hasFetchMode(node)) {
			doc.append("- Custom fetch strategies ✓\n")
		}
		doc.append("\n")
		
		return doc.toString()
	}
	
	private static String buildInterceptorDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Interceptor** `${node.nameWithoutPackage}`\n\n")
		
		doc.append("**Execution Order:**\n")
		doc.append("1. `before()` - Execute before controller action\n")
		doc.append("2. `after()` - Execute after controller action\n")
		doc.append("3. `afterView()` - Execute after view rendering\n\n")
		
		doc.append("**Matching Options:**\n")
		doc.append("- Controller/action patterns\n")
		doc.append("- URI patterns\n")
		doc.append("- Exception handling\n\n")
		
		return doc.toString()
	}
	
	private static String buildTaglibDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Tag Library** `${node.nameWithoutPackage}`\n\n")
		
		String namespace = GrailsUtils.getTaglibNamespace(node)
		doc.append("**Namespace:** `${namespace}`\n")
		doc.append("**Usage:** `<${namespace}:tagName>content</${namespace}:tagName>`\n\n")
		
		// Taglib-specific properties
		doc.append("**TagLib Properties:**\n")
		if (GrailsUtils.hasCustomNamespace(node)) {
			doc.append("- `namespace` - Custom namespace ✓\n")
		}
		if (GrailsUtils.hasDefaultEncodeAs(node)) {
			doc.append("- `defaultEncodeAs` - Default encoding ✓\n")
		}
		if (GrailsUtils.hasEncodeAsForTags(node)) {
			doc.append("- `encodeAsForTags` - Per-tag encoding ✓\n")
		}
		doc.append("\n")
		
		doc.append("**Tag Features:**\n")
		doc.append("- Closure-based tag definitions\n")
		doc.append("- Attribute validation\n")
		doc.append("- Nested tag support\n")
		doc.append("- Body content processing\n")
		doc.append("- Automatic XSS protection\n")
		doc.append("- Custom encoding support\n\n")
		
		return doc.toString()
	}
	
	private static String buildCommandDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Command Object** `${node.nameWithoutPackage}`\n\n")
		
		doc.append("**Features:**\n")
		doc.append("- Data binding from request parameters\n")
		doc.append("- Validation constraints support\n")
		doc.append("- Error handling integration\n")
		doc.append("- Type conversion\n\n")
		
		return doc.toString()
	}
	
	private static String buildJobDocumentation(ClassNode node) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Grails Job (Quartz)** `${node.nameWithoutPackage}`\n\n")
		
		doc.append("**Execution:**\n")
		doc.append("- Scheduled via triggers\n")
		doc.append("- Concurrent execution control\n")
		doc.append("- Job data persistence\n\n")
		
		return doc.toString()
	}
	
	private static String buildGenericArtifactDocumentation(GrailsArtifactType artifactType) {
		return "**Grails ${artifactType.toString().toLowerCase().capitalize()} Artifact**\n\n"
	}
	
	private static String buildPropertyDocumentation(PropertyNode node, String uri = null) {
		// PropertyNode wraps a FieldNode, so delegate to field documentation
		return buildFieldDocumentation(node.field, uri)
	}
	
	private static String buildFieldDocumentation(FieldNode node, String uri = null) {
		ClassNode classNode = node.declaringClass
		if (!classNode) return null
		
		GrailsArtifactType artifactType = GrailsArtefactUtils.getGrailsArtifactType(classNode, uri)
		if (!artifactType) return null
		
		String fieldName = node.name
		
		switch (artifactType) {
			case GrailsArtifactType.DOMAIN:
				return buildDomainFieldDocumentation(node, classNode, fieldName)
			case GrailsArtifactType.CONTROLLER:
				return buildControllerFieldDocumentation(node, fieldName)
			case GrailsArtifactType.SERVICE:
				return buildServiceFieldDocumentation(node, fieldName)
			case GrailsArtifactType.TAGLIB:
				return buildTaglibFieldDocumentation(node, fieldName)
			default:
				return null
		}
	}
	
	private static String buildDomainFieldDocumentation(FieldNode node, ClassNode classNode, String fieldName) {
		StringBuilder doc = new StringBuilder()
		doc.append("**Domain Property** `${fieldName}`\n\n")
		
		// Database column mapping - convert fieldName to field_name convention
		String columnName = convertToColumnName(fieldName)
		doc.append("**Column:** `${columnName}`\n")
		
		// Type information
		String typeName = node.type.nameWithoutPackage
		doc.append("**Type:** `${typeName}`\n\n")
		
		// Check for special domain properties
		if (GrailsUtils.isGormTimestampProperty(fieldName)) {
			doc.append("**GORM Timestamp:** Automatically managed by GORM\n\n")
		}
		
		if (GrailsUtils.isVersionProperty(fieldName)) {
			doc.append("**Optimistic Locking:** Used for version-based locking\n\n")
		}
		
		if (GrailsUtils.isIdProperty(fieldName)) {
			doc.append("**Primary Key:** Database identifier\n\n")
		}
		
		return doc.toString()
	}
	
	private static String buildControllerFieldDocumentation(FieldNode node, String fieldName) {
		if (GrailsUtils.isDependencyInjection(node)) {
			return "**Injected Service** `${fieldName}`\n\nAutomatically injected by Grails dependency injection.\n\n"
		}
		return null
	}
	
	private static String buildServiceFieldDocumentation(FieldNode node, String fieldName) {
		if (GrailsUtils.isDependencyInjection(node)) {
			return "**Injected Dependency** `${fieldName}`\n\nAutomatically injected by Grails.\n\n"
		}
		return null
	}
	
	private static String buildTaglibFieldDocumentation(FieldNode node, String fieldName) {
		if (fieldName == "namespace") {
			return "**Tag Namespace** `${fieldName}`\n\nDefines the XML namespace for all tags in this library.\n\n"
		}
		return null
	}
	
	private static String buildMethodDocumentation(MethodNode methodNode, String uri = null) {
		ClassNode classNode = methodNode.declaringClass
		if (!classNode) return null
		
		GrailsArtifactType artifactType = GrailsArtefactUtils.getGrailsArtifactType(classNode, uri)
		if (!artifactType) return null
		
		String methodName = methodNode.name
		
		switch (artifactType) {
			case GrailsArtifactType.CONTROLLER:
				return buildControllerMethodDocumentation(methodNode, classNode, methodName)
			case GrailsArtifactType.DOMAIN:
				return buildDomainMethodDocumentation(methodNode, methodName)
			case GrailsArtifactType.SERVICE:
				return buildServiceMethodDocumentation(methodNode, methodName)
			case GrailsArtifactType.TAGLIB:
				return buildTaglibMethodDocumentation(methodNode, classNode, methodName)
			case GrailsArtifactType.INTERCEPTOR:
				return buildInterceptorMethodDocumentation(methodNode, methodName)
			default:
				return null
		}
	}
	
	private static String buildControllerMethodDocumentation(MethodNode methodNode, ClassNode classNode, String methodName) {
		if (!GrailsArtefactUtils.isControllerAction(methodNode)) return null
		
		StringBuilder doc = new StringBuilder()
		doc.append("**Controller Action** `${methodName}`\n\n")
		
		String controllerName = GrailsArtefactUtils.getControllerName(classNode)
		doc.append("**URL:** `/${controllerName}/${methodName}`\n")
		doc.append("**HTTP Methods:** GET, POST (default)\n\n")
		
		// Check for security annotations
		if (GrailsUtils.hasSecurityAnnotation(methodNode)) {
			doc.append("**Security:** Secured ✓\n\n")
		}
		
		return doc.toString()
	}
	
	private static String buildDomainMethodDocumentation(MethodNode methodNode, String methodName) {
		StringBuilder doc = new StringBuilder()
		
		// GORM lifecycle events
		if (GrailsUtils.isGormLifecycleMethod(methodName)) {
			doc.append("**GORM Lifecycle Event** `${methodName}`\n\n")
			doc.append("**Trigger:** ${getLifecycleEventDescription(methodName)}\n\n")
			return doc.toString()
		}
		
		// Static GORM methods
		if (methodNode.isStatic() && GrailsUtils.isGormStaticMethod(methodName)) {
			doc.append("**GORM Static Method** `${methodName}`\n\n")
			doc.append("**Purpose:** ${getGormMethodDescription(methodName)}\n\n")
			return doc.toString()
		}
		
		return null
	}
	
	private static String buildServiceMethodDocumentation(MethodNode methodNode, String methodName) {
		if (GrailsUtils.hasTransactionalAnnotation(methodNode)) {
			StringBuilder doc = new StringBuilder()
			doc.append("**Transactional Method** `${methodName}`\n\n")
			doc.append("**Transaction:** Automatic rollback on exceptions\n\n")
			return doc.toString()
		}
		return null
	}
	
	private static String buildTaglibMethodDocumentation(MethodNode methodNode, ClassNode classNode, String methodName) {
		if (GrailsUtils.isTagDefinition(methodNode)) {
			StringBuilder doc = new StringBuilder()
			doc.append("**Tag Definition** `${methodName}`\n\n")
			
			String namespace = GrailsUtils.getTaglibNamespace(classNode) ?: 'g'
			doc.append("**Usage:** `<${namespace}:${methodName}>content</${namespace}:${methodName}>`\n\n")
			
			return doc.toString()
		}
		return null
	}
	
	private static String buildInterceptorMethodDocumentation(MethodNode methodNode, String methodName) {
		if (methodName in ['before', 'after', 'afterView']) {
			StringBuilder doc = new StringBuilder()
			doc.append("**Interceptor Method** `${methodName}`\n\n")
			
			switch (methodName) {
				case 'before':
					doc.append("**Execution:** Before controller action\n")
					doc.append("**Return false to prevent action execution**\n\n")
					break
				case 'after':
					doc.append("**Execution:** After controller action\n")
					doc.append("**Access to model and view**\n\n")
					break
				case 'afterView':
					doc.append("**Execution:** After view rendering\n")
					doc.append("**Final cleanup opportunity**\n\n")
					break
			}
			
			return doc.toString()
		}
		return null
	}
	
	// === Helper Methods ===
	
	private static String convertToTableName(String className) {
		// Convert CamelCase to snake_case (e.g., UserAccount -> user_account)
		return className.replaceAll(/([A-Z])/, /_$1/).toLowerCase().replaceAll(/^_/, '')
	}
	
	private static String convertToColumnName(String propertyName) {
		// Convert camelCase to snake_case (e.g., firstName -> first_name)
		return propertyName.replaceAll(/([A-Z])/, /_$1/).toLowerCase()
	}
	
	private static String getLifecycleEventDescription(String methodName) {
		switch (methodName) {
			case 'beforeInsert': return 'Before saving new instance'
			case 'afterInsert': return 'After saving new instance'
			case 'beforeUpdate': return 'Before updating existing instance'
			case 'afterUpdate': return 'After updating existing instance'
			case 'beforeDelete': return 'Before deleting instance'
			case 'afterDelete': return 'After deleting instance'
			case 'beforeValidate': return 'Before validation'
			case 'afterValidate': return 'After validation'
			default: return 'Unknown lifecycle event'
		}
	}
	
	private static String getGormMethodDescription(String methodName) {
		if (methodName.startsWith('findBy')) return 'Dynamic finder query'
		if (methodName.startsWith('findAllBy')) return 'Dynamic finder returning list'
		if (methodName.startsWith('countBy')) return 'Dynamic count query'
		if (methodName == 'withCriteria') return 'Criteria query execution'
		if (methodName == 'createCriteria') return 'Criteria builder creation'
		return 'GORM query method'
	}
}

