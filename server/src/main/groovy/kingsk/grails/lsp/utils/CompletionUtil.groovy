package kingsk.grails.lsp.utils

import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.DocumentationType
import org.apache.groovy.ast.tools.ClassNodeUtils
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.Statement
import org.eclipse.lsp4j.*

@Slf4j
class CompletionUtil {
	
	/**
	 * Extracts the prefix at the given cursor position in the line.
	 * This prefix is used to filter completion candidates.
	 *
	 * Example:
	 * lineText = "foo.ba|r", cursorPos=6 (| is cursor)
	 * returns "ba"
	 *
	 * @param lineText The full text of the current line
	 * @param cursorPos Cursor position (zero-based index) in lineText
	 * @return Prefix string for completion filtering
	 */
	static String extractPrefixFromLine(String lineText, int cursorPos) {
		if (!lineText || cursorPos <= 0 || cursorPos > lineText.length()) return ""
		
		// Handle cursor at EOL
		if (cursorPos == lineText.length()) {
			int dot = lineText.lastIndexOf('.')
			if (dot >= 0 && dot < lineText.length() - 1) {
				return lineText.substring(dot + 1)
			}
		}
		
		// Dummy handling
		if (lineText.contains(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)) {
			int dummyStart = lineText.lastIndexOf(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)
			int dummyEnd = dummyStart + GrailsUtils.DUMMY_COMPLETION_IDENTIFIER.length()
			if (cursorPos > dummyStart && cursorPos <= dummyEnd) {
				return GrailsUtils.DUMMY_COMPLETION_IDENTIFIER
			}
		}
		
		// Step1: Indentify segment containing cursor
		int segmentStart = lineText.lastIndexOf('.', cursorPos - 1) + 1
		int segmentEnd = lineText.indexOf('.', segmentStart)
		if (segmentEnd < 0) segmentEnd = lineText.length()
		
		// Step2: extract identifier
		int cursor = Math.min(cursorPos, lineText.length())
		
		// Start from one char before cursor
		int start = cursor - 1
		while (start >= segmentStart && Character.isJavaIdentifierPart(lineText.charAt(start))) {
			start--
		}
		start++
		
		int end = cursorPos
		while (end < segmentEnd && Character.isJavaIdentifierPart(lineText.charAt(end))) {
			end++
		}
		
		if (cursorPos == end) {
			return lineText.substring(start, end)
		}
		
		return lineText.substring(start, cursorPos)
	}
	
	static void collectCompletionItems(List<? extends ASTNode> nodes, String prefix, Set<String> seen, List<CompletionItem> items) {
		if (!nodes || nodes.isEmpty()) return
		for (ASTNode node : nodes) {
			if (!isSeenItem(node, prefix, seen)) continue
			items << buildCompletionItem(node)
		}
	}
	
	static MethodNode convertPropertyToSyntheticMethod(PropertyNode node, boolean isGetter = true) {
		String name
		ClassNode returnType = node.type
		Parameter[] parameters = Parameter.EMPTY_ARRAY
		ClassNode[] exceptions = ClassNode.EMPTY_ARRAY
		Statement code
		if (isGetter) {
			name = node.getterNameOrDefault + "()"
			code = node.getterBlock
		} else {
			// setter
			name = node.setterNameOrDefault + "()"
			returnType = ClassHelper.VOID_TYPE
			parameters = [new Parameter(node.originType, node.name)] as Parameter[]
			code = node.setterBlock
		}
		def method = new MethodNode(name, node.modifiers, returnType, parameters, exceptions, code)
		method.declaringClass = node.declaringClass
		method.setLineNumber(node.lineNumber)
		method.setLastLineNumber(node.lastLineNumber)
		method.setColumnNumber(node.columnNumber)
		method.setLastColumnNumber(node.lastColumnNumber)
		method.setSynthetic(true)
		return method
	}
	
	/**
	 * Constructs a CompletionItem from an ASTNode with optional metadata.
	 *
	 * @param node AST node representing the symbol
	 * @return A CompletionItem for the given ASTNode
	 */
	static CompletionItem buildCompletionItem(ASTNode node) {
		CompletionItem item = new CompletionItem()
		
		// Essential data for display, filtering, and sorting
		item.label = ASTUtils.astNodeToName(node)
		item.kind = astNodeToCompletionItemKind(node)
		item.sortText = astNodeToSortText(node)
		item.filterText = astNodeToFilterText(node)
		item.commitCharacters = astNodeToCommitCharacters(node)
		
		// Basic insert text (no complex snippets yet)
		if (node instanceof ClassNode) item.insertText = node.nameWithoutPackage
		else item.insertText = item.label
		item.insertTextFormat = InsertTextFormat.PlainText
		
		if (GrailsUtils.isNodeDeprecated(node)) {
			item.tags = [CompletionItemTag.Deprecated]
		}
		
		if (node instanceof MethodNode) {
			String grailsName = GrailsUtils.getGrailsPropertyName(node)
			if (grailsName && GrailsUtils.isGrailsSyntheticProperty(node)) {
				item.label = grailsName
				item.kind = CompletionItemKind.Property // Override the kind to Property
			}
		}
		
		item.data = [
				astNode   : node,
				nodeType  : node.class.simpleName,
				isResolved: false
		]
		
		return item
	}
	
	// Resolve completion item with expensive operations
	static CompletionItem resolveCompletionItem(CompletionItem item, GrailsService grailsService) {
		Map<String, Object> data = item.data as Map<String, Object>
		if (!data || data.isResolved) {
			return item // Already resolved
		}
		
		ASTNode node = data.astNode as ASTNode
		if (!node) {
			return item // No node to resolve from
		}
		
		try {
			// Add expensive detail information
			enhanceCompletionItemDetails(item, node)
			
			def documentation = DocumentationHelper.getDocumentation(
					node, grailsService, DocumentationType.COMPLETION
			)
			if (documentation.value?.trim()) {
				item.documentation = documentation
			}
			
			// Add complex insert text with snippets
			String complexInsertText = astNodeToInsertText(node)
			if (complexInsertText) {
				item.insertText = complexInsertText
				item.insertTextFormat = astNodeToInsertTextFormat(node)
			}
			
			// Add additional text edits (imports, etc.)
			List<TextEdit> additionalEdits = astNodeToAdditionalTextEdits(node)
			if (additionalEdits) {
				item.additionalTextEdits = additionalEdits
			}
			
			// Mark as resolved
			data.isResolved = true
		} catch (Exception e) {
			log.warn("[COMPLETION] Failed to resolve completion item for ${item.label}: ${e.message}")
		}
		return item
	}
	
	/**
	 * Finds the appropriate range in the source file to add a new import statement.
	 *
	 * @param moduleNode The module node from AST.
	 * @return Range indicating where to insert the import. Used for auto-import code actions.
	 */
	static Range findAddImportRange(ModuleNode moduleNode) {
		if (!moduleNode) return RangeHelper.zeroRange()
		
		List<ImportNode> allImports = []
		allImports.addAll(moduleNode.imports ?: [])
		allImports.addAll(moduleNode.staticImports?.values() ?: [])
		allImports.addAll(moduleNode.starImports ?: [])
		allImports.addAll(moduleNode.staticStarImports?.values() ?: [])
		
		ASTNode afterNode = allImports ? allImports.max { it.lineNumber } : moduleNode.package
		if (!afterNode) return RangeHelper.zeroRange()
		
		Range nodeRange = ASTUtils.astNodeToRange(afterNode)
		if (!nodeRange) return RangeHelper.zeroRange()
		
		Position position = new Position(nodeRange.end.line + 1, 0)
		return new Range(position, position)
	}
	
	static boolean hasImport(ModuleNode moduleNode, String fqcn) {
		if (!moduleNode || !fqcn) return false
		
		return moduleNode.imports?.any { it.className == fqcn } ||
				moduleNode.starImports?.any { fqcn.startsWith(it.packageName) } ||
				moduleNode.staticImports?.values()?.any { it.className == fqcn } ||
				moduleNode.staticStarImports?.values()?.any { fqcn.startsWith(it.className) }
	}
	
	
	/**
	 * Determines whether a name has not yet been seen, matches the prefix,
	 * and should be added to the result set. Automatically enforces a global item limit.
	 *
	 * @param node The ASTNode to check.
	 * @param prefix The expected prefix (case-sensitive).
	 * @param seen A set of already-seen names.
	 * @return True if the name matches the prefix, is under the item limit, and not yet seen.
	 */
	static boolean isSeenItem(ASTNode node, String prefix, Set<String> seen) {
		String name = ASTUtils.astNodeToName(node)
		// If it's not a dummy prefix, apply startsWith filter
		if (!GrailsUtils.isDummyPrefix(prefix) && !name.startsWith(prefix)) return false
		String key = generateSeenKey(node)
		if (!key) return false
		return isSeen(key, seen)
	}
	
	static boolean isSeenItem(CompletionItem item, String prefix, Set<String> seen) {
		String name = item.label
		// If it's not a dummy prefix, apply startsWith filter
		if (!GrailsUtils.isDummyPrefix(prefix) && !name.startsWith(prefix)) return false
		String key = item.label
		if (!key) return false
		return isSeen(key, seen)
	}
	
	private static boolean isSeen(String key, Set<String> seen) {
		if (!key || seen.contains(key)) return false
		if (seen.size() >= GrailsUtils.MAX_COMPLETION_ITEM_LIMIT) return false
		return seen.add(key)
	}
	
	private static String generateSeenKey(ASTNode node) {
		if (node instanceof MethodNode) {
			return node.typeDescriptor
			//String paramTypes = node.parameters*.type*.name.join(",")
			// methodName(int,String)@java.util.List
			//return "${node.name}(${paramTypes})@${node.returnType.name}"
		}
		return ASTUtils.astNodeToName(node)
	}
	
	static List<CompletionItem> getGlobalCompletions() {
		List<String> keywords = ["def", "return", "new", "if", "else", "for", "while", "true", "false", "null"]
		List<String> types = ["String", "List", "Map", "Set", "HashMap", "ArrayList", "Optional", "File"]
		
		List<CompletionItem> items = []
		
		keywords.each {
			items << new CompletionItem(label: it, kind: CompletionItemKind.Keyword)
		}
		types.each {
			items << new CompletionItem(
					label: it,
					kind: CompletionItemKind.Class,
					detail: "java.util.${it}"
			)
		}
		
		return items
	}
	
	//--------- Suggest Variable Names Based on Type ---------//
	
	private static final ClassNode COLLECTION_TYPE = ClassHelper.make(Collection)
	private static final ClassNode MAP_TYPE = ClassHelper.make(Map)
	private static final ClassNode BOOLEAN_TYPE = ClassHelper.make(Boolean)
	
	/**
	 * Returns true if the given ClassNode represents a subtype of java.util.Collection or an array.
	 */
	private static boolean isCollectionType(ClassNode node) {
		if (node == null) return false
		return node.isArray() || node.isDerivedFrom(COLLECTION_TYPE) || node.implementsInterface(COLLECTION_TYPE)
	}
	
	/**
	 * Returns true if the given ClassNode represents a subtype of java.util.Map.
	 */
	private static boolean isMapType(ClassNode node) {
		if (node == null) return false
		return node.isDerivedFrom(MAP_TYPE) || node.implementsInterface(MAP_TYPE)
	}
	
	/**
	 * Returns true if the given ClassNode represents a Boolean type.
	 */
	private static boolean isBooleanType(ClassNode node) {
		if (node == null) return false
		return node == BOOLEAN_TYPE || node == ClassHelper.boolean_TYPE || node == ClassHelper.Boolean_TYPE
	}
	
	/**
	 * Suggests variable names based on the ClassNode type.
	 * Handles collections, maps, arrays, booleans, and defaults.
	 */
	static List<String> suggestNamesForType(ClassNode typeNode) {
		String raw = typeNode?.nameWithoutPackage ?: 'obj'
		String base = raw[0].toLowerCase() + raw.substring(1)
		List<String> names = [base]
		
		if (isCollectionType(typeNode)) {
			names << "${base}s".toString() // plural
			names << "my${raw}s".toString() // prefixed
		} else if (isMapType(typeNode)) {
			names << "${base}Map".toString()
			names << "${base}ByKey".toString()
		} else if (isBooleanType(typeNode)) {
			names << "is${raw}".toString() // isEnabled
			names << "has${raw}".toString() // hasAccess
		} else if (typeNode.isArray()) {
			names << "${base}Array".toString()
			names << "${base}s".toString() // treat array as plural
		}
		// other types: base name is already added
		
		return names
	}
	
	// === AST Utils ===
	
	static CompletionItemKind astNodeToCompletionItemKind(ASTNode node) {
		if (node instanceof ClassNode) {
			if (node.interface) return CompletionItemKind.Interface
			if (node.enum) return CompletionItemKind.Enum
			if (node.annotationDefinition) return CompletionItemKind.EnumMember
			return CompletionItemKind.Class
		}
		
		if (node instanceof MethodNode) {
			if (node.static) return CompletionItemKind.Function
			if (node.synthetic && ClassNodeUtils.isValidAccessorName(node.name)) {
				return CompletionItemKind.Property
			}
			return CompletionItemKind.Method
		}
		
		if (node instanceof ConstructorNode) return CompletionItemKind.Constructor
		if (node instanceof PropertyNode) return CompletionItemKind.Property
		
		if (node instanceof FieldNode) {
			if (node.static && node.final) return CompletionItemKind.Constant
			if (node.enum) return CompletionItemKind.EnumMember
			return CompletionItemKind.Field
		}
		
		if (node instanceof VariableExpression || node instanceof Parameter) {
			return CompletionItemKind.Variable
		}
		
		if (node instanceof PackageNode) return CompletionItemKind.Module
		if (node instanceof ImportNode) return CompletionItemKind.Reference
		if (node instanceof AnnotationNode) return CompletionItemKind.Class
		if (node instanceof DynamicVariable) return CompletionItemKind.Variable
		
		return CompletionItemKind.Text
	}
	
	static String astNodeToInsertText(ASTNode node) {
		switch (node) {
			case ClassNode: return node.nameWithoutPackage
			case MethodNode: // Fallthrough
			case ConstructorNode:
				return "${node.name}(${parametersToSnippetText(node.parameters as List)})"
			case PropertyNode: // Fallthrough
			case FieldNode: return "${node.name}: \${1:${node.type?.nameWithoutPackage ?: "Object"}}"
			default: return ASTUtils.astNodeToName(node)
		}
	}
	
	static String parametersToSnippetText(List<Parameter> parameters) {
		if (!parameters || parameters.isEmpty()) return ""
		
		return parameters.withIndex().collect { Parameter param, int i ->
			String typeName = param.type?.nameWithoutPackage ?: "Object"
			String paramName = param.name ?: "param${i + 1}"
			return "\${${i + 1}:$typeName $paramName}"
		}.join(", ")
	}
	
	static InsertTextFormat astNodeToInsertTextFormat(ASTNode node) {
		switch (node) {
			case MethodNode: // Fallthrough
			case ConstructorNode: // Fallthrough
			case PropertyNode: // Fallthrough
			case FieldNode:
				return InsertTextFormat.Snippet
			default:
				return InsertTextFormat.PlainText
		}
	}
	
	// Enhance detail information for resolution phase
	static void enhanceCompletionItemDetails(CompletionItem item, ASTNode node) {
		String detail = ASTUtils.astNodeToDetail(node)
		String container = ASTUtils.astNodeToContainerName(node)
		
		if (container) {
			item.detail = detail ? "${detail} • ${container}" : container
			item.labelDetails = new CompletionItemLabelDetails(
					detail: null,
					description: container
			)
		} else {
			item.detail = detail
		}
	}
	
	// === Sorting and Filtering ===
	
	static String astNodeToSortText(ASTNode node) {
		String prefix = getSortPrefix(node)
		String name = ASTUtils.astNodeToName(node)
		
		if (node instanceof MethodNode || node instanceof ConstructorNode) {
			// Prioritize methods with fewer parameters
			return "${prefix}_${name}_${String.format('%02d', node.parameters?.length ?: 0)}"
		}
		
		return "${prefix}_${name}"
	}
	
	static String getSortPrefix(ASTNode node) {
		switch (node) {
			case ClassNode: return "01"
			case ConstructorNode: return "02"
			case MethodNode: return "03"
			case PropertyNode: return "04"
			case FieldNode: return "05"
			case VariableExpression: return "06"
			case Parameter: return "07"
			default: return "99"
		}
	}
	
	static String astNodeToFilterText(ASTNode node) {
		switch (node) {
			case ClassNode: return node.nameWithoutPackage
			case MethodNode: // Fallthrough
			case ConstructorNode:
				return node.name + AstToTextHelper.getParametersText(node.parameters)
			case PropertyNode: // Fallthrough
			case FieldNode:
				return "${node.name}: ${node.type?.nameWithoutPackage ?: "Object"}"
			default: return ASTUtils.astNodeToName(node)
		}
	}
	
	static List<String> astNodeToCommitCharacters(ASTNode node) {
		switch (node) {
			case ClassNode: return []
			case MethodNode: return [".", "("]
			case ConstructorNode: return ["("]
			case PropertyNode: return [".", ":", "="]
			case FieldNode: return [".", ":", "="]
			default: return []
		}
	}
	
	// === Additional Text Edits (Imports, etc.) ===
	
	static List<TextEdit> astNodeToAdditionalTextEdits(ASTNode node) {
		List<TextEdit> edits = []
		
		// Add import statements if needed
		if (node instanceof ClassNode && needsImport(node)) {
			TextEdit importEdit = createImportEdit(node)
			if (importEdit) edits.add(importEdit)
		}
		return edits.isEmpty() ? null : edits
	}
	
	static boolean needsImport(ClassNode classNode) {
		String packageName = classNode.packageName
		return packageName && packageName != GrailsUtils.JAVA_LANG &&
				!isDefaultGroovyImport(packageName) &&
				!isAlreadyImported(classNode)
	}
	
	static boolean isDefaultGroovyImport(String packageName) {
		return packageName in GrailsUtils.DEFAULT_LIBS
	}
	
	static boolean isAlreadyImported(ClassNode classNode) {
		// This would need to check the current file's import
		def imports = classNode?.module?.imports ?: []
		def starImports = classNode?.module?.starImports ?: []
		
		def className = classNode.name
		def packageName = classNode.packageName
		
		return imports.any { it.className == className } ||
				starImports.any { it.packageName == packageName }
	}
	
	static TextEdit createImportEdit(ClassNode classNode) {
		String importStatement = "import ${classNode.name}\n"
		return new TextEdit(RangeHelper.zeroRange(), importStatement)
	}
	
	// === Completion Score ===
	
	static int computeMatchScore(String label, String prefix) {
		if (!label || !prefix) return 0
		label = label.trim()
		prefix = prefix.trim()
		
		if (label.equalsIgnoreCase(prefix)) return 100
		if (label.toLowerCase().startsWith(prefix.toLowerCase())) return 80
		if (isCamelCaseMatch(label, prefix)) return 60
		if (isSubsequenceMatch(label, prefix)) return 40
		
		return 0
	}
	
	static boolean isCamelCaseMatch(String label, String prefix) {
		if (!label || !prefix) return false
		
		StringBuilder acronym = new StringBuilder()
		for (int i = 0; i < label.length(); i++) {
			char c = label.charAt(i)
			
			if (i == 0 || Character.isUpperCase(c) || label.charAt(i - 1) == '_'.toCharacter() || Character.isDigit(label.charAt(i - 1))) {
				acronym.append(c)
			}
		}
		
		return acronym.toString().toLowerCase().startsWith(prefix.toLowerCase())
	}
	
	static boolean isSubsequenceMatch(String label, String prefix) {
		int li = 0, pi = 0
		while (li < label.length() && pi < prefix.length()) {
			if (label.charAt(li).toLowerCase() == prefix.charAt(pi).toLowerCase()) {
				pi++
			}
			li++
		}
		return pi == prefix.length()
	}
}
