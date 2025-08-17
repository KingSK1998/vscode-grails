package kingsk.grails.lsp.utils


import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.GrailsArtifactType
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

@Slf4j
class ASTUtils {
	
	/**
	 * Returns true if the node has no valid source location (e.g. generated).
	 */
	static boolean hasInvalidSource(ASTNode node) {
		if (node == null) return true
		return node.lineNumber < 0 || node.columnNumber < 0
	}
	
	
	//==== AST Node to Range ====
	
	/**
	 * Converts a Groovy AST node to an LSP range.
	 * @return May return null if the node's start line is -1
	 */
	static Range astNodeToRange(ASTNode node) {
		Position start = PositionHelper.fromGroovyPosition(node.lineNumber, node.columnNumber)
		if (!start) return null
		Position end = PositionHelper.fromGroovyPosition(node.lastLineNumber, node.lastColumnNumber) ?: start
		return new Range(start, end)
	}
	
	static Range astNodeToRangeOrZeroRange(ASTNode node) {
		return astNodeToRange(node) ?: RangeHelper.zeroRange()
	}
	
	static Range astNodeToSelectionRange(ASTNode node) {
		Range range = astNodeToRange(node)
		if (!range) return null
		
		//		if (range.start != range.end) return range
		// compute start line
		//		int newStart = range.start.line != range.end.line ? range.end.line : range.start.line
		// compute end column
		int newCharacter = range.start.character + astNodeToName(node).length()
		return new Range(range.start, new Position(range.start.line, newCharacter))
	}
	
	static Range astNodeToSelectionRangeOrZeroRange(ASTNode node) {
		return astNodeToSelectionRange(node) ?: RangeHelper.zeroRange()
	}
	
	static Range astNodeToSingleLineRange(ASTNode node) {
		def range = astNodeToRange(node)
		if (!range) return null
		return new Range(range.start, range.start)
	}
	
	//==== AST Node to Location ====
	
	static Location astNodeToLocation(ASTNode node, String uri) {
		def range = astNodeToRange(node)
		if (!range) return null
		return new Location(uri, range)
	}
	
	static Either<Location, WorkspaceSymbolLocation> astNodeToWorkspaceLocation(ASTNode node, String uri) {
		def location = astNodeToLocation(node, uri)
		if (!location) return Either.forLeft(location)
		return Either.forRight(new WorkspaceSymbolLocation(uri))
	}
	
	//==== AST Node to Symbol ====
	
	static WorkspaceSymbol astNodeToWorkspaceSymbol(ASTNode node, String uri, ClassNode parentNode = null) {
		if (!node) return null
		return new WorkspaceSymbol(
				name: astNodeToName(node),
				kind: astNodeToSymbolKind(node),
				tags: [], // Tags for this completion item.
				location: astNodeToWorkspaceLocation(node, uri),
				containerName: astNodeToContainerName(parentNode),
				data: null // A data entry field that is preserved on a workspace symbol between a workspace symbol request and a workspace symbol resolve request.
		)
	}
	
	static boolean isInvalidDocumentSymbol(ASTNode node) {
		if (!node) return true
		if (node instanceof MethodNode && GrailsUtils.isGrailsSyntheticProperty(node)) return false
		if (node.lineNumber < 0 || node.columnNumber < 0) return true
		if (node instanceof AnnotatedNode && node.synthetic) return true
		return false
	}
	
	static DocumentSymbol astNodeToDocumentSymbol(ASTNode node, String uri, int depth = 1) {
		if (depth < 0) return null
		if (isInvalidDocumentSymbol(node)) return null
		
		Range range = astNodeToRangeOrZeroRange(node)
		Range selectionRange = astNodeToSelectionRangeOrZeroRange(node) ?: range
		SymbolKind kind = astNodeToSymbolKind(node)
		String name = astNodeToName(node)
		
		String detail = astNodeToDetail(node)
		
		if (node instanceof MethodNode) {
			String grailsName = GrailsUtils.getGrailsControllerPropertySymbol(node)
			if (grailsName != null && GrailsUtils.isGrailsSyntheticProperty(node)) {
				name = grailsName
				kind = SymbolKind.Property // Override the kind to Property
			}
		}
		
		List<DocumentSymbol> children = []
		if (node instanceof ClassNode) {
			List<ASTNode> members = (node.fields + node.methods + node.properties) as List<ASTNode>
			members.each { childNode ->
				def symbol = astNodeToDocumentSymbol(childNode, uri, depth - 1)
				if (symbol) children << symbol
			}
		}
		
		List<SymbolTag> deprecated = GrailsUtils.isNodeDeprecated(node)
				? [SymbolTag.Deprecated]
				: []
		
		return new DocumentSymbol(
				name: name,
				detail: detail,
				kind: kind,
				tags: deprecated,
				range: range,
				selectionRange: selectionRange,
				children: children
		)
	}
	
	//==== AST Node to Type ====
	
	/**
	 * Compares the line and column positions of two AST nodes.
	 *
	 * @param n1 the first AST node
	 * @param n2 the second AST node
	 * @return true if all line/column positions match
	 */
	static boolean comparePositionsFully(ASTNode n1, ASTNode n2) {
		return n1.lineNumber == n2.lineNumber &&
				n1.columnNumber == n2.columnNumber &&
				n1.lastLineNumber == n2.lastLineNumber &&
				n1.lastColumnNumber == n2.lastColumnNumber
	}
	
	//==== AST Node to Kind ====
	
	static SymbolKind astNodeToSymbolKind(ASTNode node) {
		switch (node) {
			case ClassNode: return classNodeToSymbolKind(node as ClassNode)
			case MethodNode: return (node.static ? SymbolKind.Function : SymbolKind.Method)
			case ConstructorNode: return SymbolKind.Constructor
			case PropertyNode: return SymbolKind.Property
			case FieldNode: return (node.static ? SymbolKind.Constant : SymbolKind.Field)
			default: return SymbolKind.Variable
		}
	}
	
	static SymbolKind classNodeToSymbolKind(ClassNode node) {
		switch (node) {
			case node.enum: return SymbolKind.Enum
			case node.interface: return SymbolKind.Interface
			case node.annotationDefinition: return SymbolKind.Interface
			case node.record: return SymbolKind.Struct
			case node.staticClass: return SymbolKind.Module
			case node.array: return SymbolKind.Array
			default: return SymbolKind.Class
		}
	}
	
	//==== AST Node to Code Lens ====
	
	static CodeLens astNodeToCodeLens(ASTNode node, Map data = [:], String labelOverride = null) {
		if (!node) return null
		def range = astNodeToSingleLineRange(node)
		if (!range) return null
		
		String label = labelOverride ?: getDefaultLabel(data)
		def command = new Command(label, "grailsLsp.codelens.action", [data] as List<Object>)
		
		return new CodeLens(range: range, command: command, data: data)
	}
	
	private static String getDefaultLabel(Map data) {
		switch (data.type) {
			case "test": return data.action == "debug" ? "Debug" : "Run"
			case "main": return data.action == "debug" ? "Debug" : "Run"
			case "override": return "@Overridden"
			case "refCount": return "..."
			default: return "Open"
		}
	}
	
	static boolean hasMainMethod(ClassNode classNode) {
		// public static void main(String[] args)
		// public static void main(String... args)
		return classNode.methods.any {
			it.public &&
					it.static &&
					it.returnType.name == "void" &&
					it.name == "main" &&
					it.parameters.size() == 1 &&
					it.parameters[0].type.name == "String[]"
		}
	}
	
	static boolean isOverriddenMethod(MethodNode methodNode, ClassNode owner) {
		// Simplified Check
		return owner.superClass?.methods?.any {
			it.name == methodNode.name && it.parameters.size() == methodNode.parameters.size()
		}
		// TODO: improve using method resolution with parent class
	}
	
	static MethodNode findOverriddenMethod(MethodNode node, ClassNode owner) {
		return owner.superClass?.methods?.find {
			it.name == node.name && it.parameters.size() == node.parameters.size()
		}
	}
	
	static String astNodeToName(ASTNode node) {
		switch (node) {
			case ClassNode: return node.nameWithoutPackage
			case MethodNode: return node.name
			case ConstructorNode: return node.name
			case PropertyNode: return node.name
			case FieldNode: return node.name
			case VariableExpression: return node.name
			case Parameter: return node.name
			case PackageNode: return node.name
			case DynamicVariable: return node?.name ?: "it"
			case ImportNode: return node.packageName ?: node.type ?: node.text
			case AnnotationNode: return node.classNode?.nameWithoutPackage ?: "@Unknown"
			default: return "<unnamed>"
		}
	}
	
	// The name of the symbol containing this symbol. For UI
	static String astNodeToContainerName(ASTNode node) {
		switch (node) {
			case ClassNode: return node.packageName
			case MethodNode: return node.declaringClass?.name
			case ConstructorNode: return node.declaringClass?.name
			case PropertyNode: return node.declaringClass?.name
			case FieldNode: return node.declaringClass?.name
			default: return null
		}
	}
	
	static String astNodeToDetail(ASTNode node, String uri = null) {
		switch (node) {
			case ClassNode: return getClassNodeDetail(node, uri)
			case MethodNode: return getMethodNodeDetail(node)
			case ConstructorNode: return getConstructorNodeDetail(node)
			case PropertyNode: return getFieldNodeDetail(node.field)
			case FieldNode: return getFieldNodeDetail(node)
			default: return ""
		}
	}
	
	private static String getClassNodeDetail(ClassNode node, String uri = null) {
		List<String> details = []
		
		// Add inheritance info
		if (node.superClass && node.superClass.name != GrailsUtils.TYPE_OBJECT) {
			details.add("extends ${node.superClass.nameWithoutPackage}")
		}
		
		// Add interface info
		if (node.interfaces) {
			String interfaces = node.interfaces.collect { it.nameWithoutPackage }.join(', ')
			details.add("implements ${interfaces}")
		}
		
		// Add Grails artifact type if applicable
		GrailsArtifactType artifactType = GrailsArtefactUtils.getGrailsArtifactType(node)
		if (artifactType.valid) {
			details.add("(${artifactType.name().toLowerCase()})")
		}
		
		// Add class type info
		if (node.interface) details.add("interface")
		else if (node.enum) details.add("enum")
		else if (node.abstract) details.add("abstract class")
		
		return details.join(' ')
	}
	
	private static String getMethodNodeDetail(MethodNode node) {
		def returnType = AstToTextHelper.getClassText(node.returnType)
		def params = AstToTextHelper.getParametersText(node.parameters)
		def modifiers = AstToTextHelper.getModifiersText(node.modifiers)
		def throwsClause = AstToTextHelper.getThrowsClauseText(node.exceptions)
		
		return "${modifiers} ${returnType} ${node.name}(${params})${throwsClause}".replaceAll(/\s+/, " ").trim()
	}
	
	private static String getConstructorNodeDetail(ConstructorNode node) {
		def params = AstToTextHelper.getParametersText(node.parameters)
		def modifiers = AstToTextHelper.getModifiersText(node.modifiers)
		return "${modifiers} ${node.name}(${params})".replaceAll(/\s+/, " ").trim()
	}
	
	private static String getFieldNodeDetail(FieldNode node) {
		String type = AstToTextHelper.getClassText(node.type)
		String modifiers = AstToTextHelper.getModifiersText(node.modifiers)
		return "${modifiers} ${type} ${node.name}".replaceAll(/\s+/, ' ').trim()
	}
}

