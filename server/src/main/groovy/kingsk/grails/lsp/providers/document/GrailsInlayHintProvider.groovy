package kingsk.grails.lsp.providers.document

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils
import kingsk.grails.lsp.utils.grails.GrailsUtils
import kingsk.grails.lsp.utils.services.TypeInferenceService
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

/**
 * Provides inlay hints for inferred types in Groovy and Grails source files.
 */
@Slf4j
@CompileStatic
class GrailsInlayHintProvider extends BaseProvider {
	
	GrailsInlayHintProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }
	
	/**
	 * Collects inlay hints within a given range.
	 *
	 * @param doc the text document
	 * @param range range to restrict hints to
	 * @param visitor the GrailsASTVisitor for the document
	 * @return a list of InlayHint objects
	 */
    CompletableFuture<List<InlayHint>> provideInlayHints(TextDocumentIdentifier textDocument, Range range) {
        if (!textDocument?.uri) {
            log.warn("[INLAY_HINTS] TextDocument or URI is null")
            return emptyResult([] as List<InlayHint>)
        }

        log.info "[INLAY_HINTS] uri=${textDocument.uri}, range=${range}"

        if (!visitor?.empty == false) {
            log.warn("[INLAY_HINTS] AST visitor is null or empty")
            return emptyResult([] as List<InlayHint>)
        }

        def uri = TextFile.normalizePath(textDocument.uri)
        def classNodes = visitor.getClassNodes(uri)
        if (!classNodes) return emptyResult([] as List<InlayHint>)

        List<InlayHint> hints = []

        classNodes.each { ClassNode classNode ->
            processClassNode(classNode, hints)
            classNode.methods.each { processMethodNode(it, hints) }
            classNode.declaredConstructors.each { processMethodNode(it, hints) }
            classNode.objectInitializerStatements.each { collectHintsFromStatement(it, visitor, hints) }
        }

        log.info("[INLAY_HINTS] provided for document: ${uri}, found ${hints.size()} hints")
        CompletableFuture.completedFuture(hints)
    }

    @CompileDynamic
    static CompletableFuture<InlayHint> resolveInlayHint(InlayHint unresolved) {
        if (!unresolved?.data) return CompletableFuture.completedFuture(unresolved)

        try {
            def variableName = unresolved.data?.variableName as String
            def typeName = unresolved.data?.inferredType as String

            if (variableName && typeName) {
                unresolved.tooltip = Either.forLeft("""
                    |Variable: ${variableName}
                    |Type: ${typeName}
                    |
                    |This type was inferred by the Grails LSP.
                    |""".stripMargin())
            }
            CompletableFuture.completedFuture(unresolved)
        } catch (Exception e) {
            log.error("[INLAY_HINTS] Error resolving inlay hint: ${e.message}", e)
            CompletableFuture.completedFuture(unresolved)
        }
    }
	
	/**
	 * Process a class node for Grails-specific hints
	 */
	private void processClassNode(ClassNode classNode, List<InlayHint> hints) {
		// Check if this is a Grails domain class
		if (GrailsArtefactUtils.isGrailsDomainClass(classNode)) {
			processDomainClassProperties(classNode, hints)
		}
		
		// Check if this is a Grails controller
		if (GrailsArtefactUtils.isGrailsControllerClass(classNode)) {
			processControllerActions(classNode, hints)
		}
	}
	
	/**
	 * Process a method node for hints
	 */
	private void processMethodNode(MethodNode methodNode, List<InlayHint> hints) {
		// Process method parameters for type hints
		methodNode.parameters.each { Parameter param ->
			if (param.dynamicTyped) {
				processParameterTypeHint(param, hints)
			}
		}
		
		// Process method body
		if (methodNode.code) {
			collectHintsFromStatement(methodNode.code, visitor, hints)
		}
	}
	
	/**
	 * Add type hints for dynamically typed method parameters
	 */
	private void processParameterTypeHint(Parameter param, List<InlayHint> hints) {
		// Skip parameters that already have explicit types
		if (!param.dynamicTyped) return
		
		// Try to infer property type from constraints
		def inferredType = TypeInferenceService.inferParameterType(param)
		if (!inferredType || inferredType.name == GrailsUtils.TYPE_OBJECT) return
		
		def pos = new Position(param.lineNumber - 1, param.columnNumber - 1)
		
		def labelPart = new InlayHintLabelPart(": ${inferredType.nameWithoutPackage}")
		labelPart.tooltip = Either.forLeft("Inferred parameter type")
		
		def hint = new InlayHint(pos, Either.forRight([labelPart]))
		hint.kind = InlayHintKind.Type
		hint.tooltip = Either.forLeft("Parameter Type: ${inferredType.nameWithoutPackage}" as String)
		hint.paddingLeft = false
		hint.paddingRight = false
		hint.data = [
				variableName: param.name,
				inferredType: inferredType.nameWithoutPackage
		]
		
		hints << hint
	}
	
	/**
	 * Process Grails domain class properties for type hints
	 */
	private void processDomainClassProperties(ClassNode domainClass, List<InlayHint> hints) {
		// Add hints for domain class properties with constraints
		domainClass.properties.each { property ->
			// Skip properties that already have explicit types
			if (!ClassHelper.isDynamicTyped(property.type)) return
			
			// Try to infer property type from constraints
			def inferredType = TypeInferenceService.inferDomainPropertyType(property)
			if (!inferredType || inferredType.name == GrailsUtils.TYPE_OBJECT) return
			
			def pos = new Position(property.lineNumber - 1, property.columnNumber - 1)
			
			def labelPart = new InlayHintLabelPart(": ${inferredType.nameWithoutPackage}")
			labelPart.tooltip = Either.forLeft("Inferred from domain class constraints")
			
			def hint = new InlayHint(pos, Either.forRight([labelPart]))
			hint.kind = InlayHintKind.Type
			hint.tooltip = Either.forLeft("Domain Property Type: ${inferredType.nameWithoutPackage}" as String)
			hint.paddingLeft = false
			hint.paddingRight = false
			hint.data = [
					variableName: property.name,
					inferredType: inferredType.nameWithoutPackage
			]
			
			hints << hint
		}
	}
	
	/**
	 * Process Grails controller actions for return type hints
	 */
	private void processControllerActions(ClassNode controllerClass, List<InlayHint> hints) {
		// Add return type hints for controller actions
		controllerClass.methods.each { method ->
			// Skip methods that already have explicit return types
			if (!method.dynamicReturnType) return
			
			// Check if this is a controller action
			if (!GrailsArtefactUtils.isControllerAction(method)) return
			
			// Try to infer return type for dynamic finder
			def inferredType = TypeInferenceService.inferControllerActionReturnType(method)
			if (!inferredType || inferredType.name == GrailsUtils.TYPE_OBJECT) return
			
			def pos = new Position(method.lineNumber - 1, method.columnNumber - 1 + method.text.length())
			
			def labelPart = new InlayHintLabelPart(": ${inferredType.nameWithoutPackage}")
			labelPart.tooltip = Either.forLeft("Inferred controller action return type")
			
			def hint = new InlayHint(pos, Either.forRight([labelPart]))
			hint.kind = InlayHintKind.Type
			hint.tooltip = Either.forLeft("Action Return Type: ${inferredType.nameWithoutPackage}" as String)
			hint.paddingLeft = false
			hint.paddingRight = false
			hint.data = [
					variableName: method.name,
					inferredType: inferredType.nameWithoutPackage
			]
			
			hints << hint
		}
	}
	
	/**
	 * Recursively collect hints from statements
	 */
	private static void collectHintsFromStatement(Statement stmt, GrailsASTVisitor visitor, List<InlayHint> hints) {
		if (!stmt) return
		
		if (stmt instanceof BlockStatement) {
			stmt.statements.each { collectHintsFromStatement(it, visitor, hints) }
		} else if (stmt instanceof ExpressionStatement) {
			def expr = stmt.expression
			if (expr instanceof DeclarationExpression) {
				def varExpr = expr.leftExpression
				if (varExpr instanceof VariableExpression && varExpr.isDynamicTyped()) {
					def hint = buildTypeHint(varExpr, expr.rightExpression, visitor)
					if (hint != null) hints << hint
				}
			} else if (expr instanceof MethodCallExpression) {
				// Process GORM dynamic finders
				processGormDynamicFinder(expr, visitor, hints)
				
				// Process method call arguments
				processMethodCallArguments(expr, visitor, hints)
			}
		}
	}
	
	private static void processMethodCallArguments(MethodCallExpression methodCall, GrailsASTVisitor visitor, List<InlayHint> hints) {
		// Get the method being called
		MethodNode methodNode = GrailsASTHelper.getMethodFromCallExpression(methodCall, visitor)
		if (!methodNode) return
		
		// Get the argument list
		if (!(methodCall.arguments instanceof ArgumentListExpression)) return
		ArgumentListExpression argsList = GrailsASTHelper.getArgumentListExpression(methodCall)
		
		def parameters = methodNode.parameters
		
		// Process each argument that corresponds to a parameter
		int paramCount = parameters.length
		int argCount = argsList.expressions.size()
		
		// Only process arguments that have corresponding parameters
		for (int i = 0; i < Math.min(paramCount, argCount); i++) {
			Expression argExpr = argsList.expressions[i]
			Parameter param = parameters[i]
			
			// Skip if parameter has no type or is a var arg beyond the first position
			if (param.type.name == GrailsUtils.TYPE_OBJECT ||
					(i > 0 && param.type.array && i >= paramCount - 1)) continue
			
			// Skip if argument is not a variable expression
			if (!(argExpr instanceof VariableExpression)) continue
			VariableExpression varExpr = argExpr as VariableExpression
			
			// Skip if variable already has a known type
			if (!varExpr.dynamicTyped) continue
			
			// Create a hint for the parameter type
			def pos = new Position(argExpr.lineNumber - 1, argExpr.columnNumber - 1 + argExpr.text.length())
			
			def labelPart = new InlayHintLabelPart(": ${param.type.nameWithoutPackage}")
			labelPart.tooltip = Either.forLeft("Inferred parameter type from method signature")
			
			def hint = new InlayHint(pos, Either.forRight([labelPart]))
			hint.kind = InlayHintKind.Parameter
			hint.tooltip = Either.forLeft("Parameter Type: ${param.type.nameWithoutPackage}" as String)
			hint.paddingLeft = false
			hint.paddingRight = false
			hint.data = [
					variableName: param.name,
					inferredType: param.type.nameWithoutPackage,
					parmeterName: param.name
			]
			
			hints << hint
		}
	}
	
	/**
	 * Process GORM dynamic finders for return type hints
	 */
	private static void processGormDynamicFinder(MethodCallExpression methodCall, GrailsASTVisitor visitor, List<InlayHint> hints) {
		// Check if this is a GORM dynamic finder
		if (!GrailsArtefactUtils.isGormDynamicFinder(methodCall)) return
		
		// Try to infer return type for dynamic finder
		def inferredType = TypeInferenceService.inferGormDynamicFinderReturnType(methodCall, visitor)
		if (!inferredType || inferredType.name == GrailsUtils.TYPE_OBJECT) return
		
		def pos = new Position(methodCall.lineNumber - 1, methodCall.columnNumber - 1 + methodCall.method.text.length())
		
		def labelPart = new InlayHintLabelPart(": ${inferredType.nameWithoutPackage}")
		labelPart.tooltip = Either.forLeft("Inferred GORM finder return type")
		
		def hint = new InlayHint(pos, Either.forRight([labelPart]))
		hint.kind = InlayHintKind.Type
		hint.tooltip = Either.forLeft("GORM Finder Return Type: ${inferredType.nameWithoutPackage}" as String)
		hint.paddingLeft = false
		hint.paddingRight = false
		hint.data = [
				variableName: methodCall.method.text,
				inferredType: inferredType.nameWithoutPackage
		]
		
		hints << hint
	}
	
	/**
	 * Build a type hint for a variable expression
	 */
	private static InlayHint buildTypeHint(VariableExpression varExpr, Expression rhsExpr, GrailsASTVisitor visitor) {
		def inferredType = GrailsASTHelper.getTypeOfNode(rhsExpr, visitor)
		if (!inferredType || inferredType.name == GrailsUtils.TYPE_OBJECT) return null
		
		def pos = new Position(varExpr.lineNumber - 1, varExpr.columnNumber - 1 + varExpr.text.length())
		
		def labelPart = new InlayHintLabelPart(": ${inferredType.nameWithoutPackage}")
		labelPart.tooltip = Either.forLeft("Inferred from right-hand expression")
		labelPart.location = new Location(visitor.getURI(rhsExpr), new Range(pos, pos))
		
		def hint = new InlayHint(pos, Either.forRight([labelPart]))
		hint.kind = InlayHintKind.Type
		hint.tooltip = Either.forLeft("Type: ${inferredType.nameWithoutPackage}" as String)
		hint.paddingLeft = false
		hint.paddingRight = false
		hint.data = [
				variableName: varExpr.name,
				inferredType: inferredType.nameWithoutPackage
		]
		
		return hint
	}
}
