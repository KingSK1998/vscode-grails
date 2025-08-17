package kingsk.grails.lsp.core.visitor

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.PositionHelper
import kingsk.grails.lsp.utils.RangeHelper
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.control.SourceUnit
import org.eclipse.lsp4j.Position

import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors

@Slf4j
@CompileStatic
class GrailsASTVisitor extends ClassCodeVisitorSupport {
	private SourceUnit sourceUnit
	private final Stack<ASTNode> stack = new Stack<>()
	private final Map<String, Set<ASTNode>> nodesByURI = new ConcurrentHashMap<>()
	private final Map<String, Set<ClassNode>> classNodesByURI = new ConcurrentHashMap<>()
	private final Map<ASTLookupKey, ASTNodeLookupData> lookup = new ConcurrentHashMap<>()
	
	// Reference to GrailsService for cross-file resolution
	final GrailsService service
	
	// Line-based indexing for fast position lookups
	private final Map<String, Map<Integer, Set<ASTNode>>> nodesByLineIndex = new ConcurrentHashMap<>()
	
	// Memory management
	private static final int MAX_FILES_IN_MEMORY = 50
	
	private static class ASTLookupKey {
		ASTNode node
		
		boolean equals(Object o) {
			if (this.is(o)) return true
			if (!(o instanceof ASTLookupKey)) return false
			ASTLookupKey that = (ASTLookupKey) o
			return node == that.node // Identity comparison
		}
		
		int hashCode() {
			return System.identityHashCode(node)
		}
	}
	
	private static class ASTNodeLookupData {
		ASTNode parent
		String uri
	}
	
	SourceUnit getSourceUnit() {
		return this.sourceUnit
	}
	
	GrailsASTVisitor() {
		service = null
	}
	
	GrailsASTVisitor(GrailsService service) {
		this.service = service
	}
	
	private void pushASTNode(ASTNode node) {
		String uri = TextFile.normalizePath(sourceUnit.name)
		boolean isSynthetic = (node instanceof AnnotatedNode && node.synthetic) ?: false
		
		// track parent for all nodes
		ASTNode parent = (stack.size() > 0) ? stack.lastElement() : null
		ASTNodeLookupData data = new ASTNodeLookupData(uri: uri, parent: parent)
		lookup.put(new ASTLookupKey(node: node), data)
		
		// Add non-synthetic nodes to nodesByURI
		if (!isSynthetic) {
			nodesByURI.computeIfAbsent(uri, k -> [] as Set).add(node)
		}
		
		String type = "${node.getClass().simpleName}@${System.identityHashCode(node)}"
		log.debug("[AST] Pushed: $type")
		visitCounts.compute(type) { k, v -> v == null ? 1 : v + 1 }
		
		// Add line indexing
		if (node.lineNumber > 0 && !isSynthetic) {
			def startLine = node.lineNumber
			def endLine = Math.max(startLine, node.lastLineNumber)
			def lineMap = nodesByLineIndex.computeIfAbsent(uri) {
				new ConcurrentHashMap<Integer, Set<ASTNode>>()
			}
			for (int line = startLine; line <= endLine; line++) {
				lineMap.computeIfAbsent(line, k -> [] as Set).add(node)
			}
		}
		
		stack.add(node)
	}
	
	private void popASTNode() {
		def node = stack.pop()
		log.debug("[AST] Popping: ${node.getClass().simpleName}@${System.identityHashCode(node)}")
	}
	
	private final Map<String, Integer> visitCounts = new ConcurrentHashMap<>()
	
	/**
	 * Smart cleanup - only remove files that are safe to remove
	 * Safe = not tracked in fileTracker (not open/active)
	 */
	private void cleanupUnusedFiles() {
		if (nodesByURI.size() <= MAX_FILES_IN_MEMORY || !service?.fileTracker) return
		
		// Get currently tracked files (open/active files)
		Set<String> trackedUris = service.fileTracker.activeTextFiles*.uri.toSet()
		
		// Find files in AST that are NOT tracked (safe to remove)
		Set<String> safeToRemove = nodesByURI.keySet().findAll { uri ->
			!trackedUris.contains(uri)
		}
		
		if (safeToRemove.empty) {
			log.debug "[AST] No safe files to cleanup - all files are tracked"
			return
		}
		
		// Remove safe files
		safeToRemove.each { uri ->
			removeFileFromASTVisitor(uri)
		}
		
		log.debug "[AST] Cleaned up ${safeToRemove.size()} unused files from memory"
	}
	
	/**
	 * Invalidate visitor state - clear all AST data
	 * Use when project structure changes significantly
	 */
	void invalidateVisitor() {
		log.info "[AST] Invalidating all visitor state"
		
		nodesByURI.clear()
		classNodesByURI.clear()
		nodesByLineIndex.clear()
		lookup.clear()
		visitCounts.clear()
		stack.clear()
		
		log.info "[AST] Visitor state invalidated"
	}
	
	/**
	 * Remove specific file and its dependencies from AST
	 * Use when file is closed or significantly changed
	 */
	void removeFileWithDependencies(String uri) {
		if (!uri || !service?.fileTracker) return
		
		log.debug "[AST] Removing file with dependencies: ${uri}"
		
		// Get the file and its dependencies
		TextFile targetFile = service.fileTracker.getTextFile(uri)
		if (!targetFile) {
			// File not tracked, just remove from AST if present
			removeFileFromASTVisitor(uri)
			return
		}
		
		// Get dependencies but only remove those NOT currently tracked
		Set<TextFile> dependencies = service.fileTracker.getFileAndItsDependencies(targetFile)
		Set<String> trackedUris = service.fileTracker.activeTextFiles*.uri.toSet()
		
		dependencies.each { depFile ->
			// Only remove if not currently tracked (not open/active)
			if (!trackedUris.contains(depFile.uri)) {
				removeFileFromASTVisitor(depFile.uri)
			}
		}
		
		// Always remove the target file itself
		removeFileFromASTVisitor(uri)
		
		log.debug "[AST] Removed file and safe dependencies"
	}
	
	/**
	 * Remove single file from AST state
	 */
	private void removeFileFromASTVisitor(String uri) {
		if (!uri) return
		
		nodesByURI.remove(uri)
		classNodesByURI.remove(uri)
		nodesByLineIndex.remove(uri)
		lookup.entrySet().removeIf { entry -> entry.value.uri == uri }
		
		log.debug "[AST] Removed file from AST: ${uri}"
	}
	
	void printVisitCounts() {
		log.info("[AST] === AST Node Visit Counts ===")
		// visitCounts.sort().each { k, v -> log.debug("$k - $v") }
		log.info("[AST] TOTAL AST NODES : ${visitCounts.size()}")
	}
	
	boolean isEmpty() {
		return visitCounts.isEmpty()
	}
	
	// ==== Core Data Access Methods ====
	
	/**
	 * Retrieves all ClassNodes across all source files or a specific URI.
	 * @param uri (optional) Source file URI to filter classes.
	 * @return Set<ClassNode> - Empty set if none found.
	 */
	Set<ClassNode> getClassNodes(String uri = null) {
		if (uri) return classNodesByURI.getOrDefault(uri, [] as Set)
		return classNodesByURI.values().flatten() as Set<ClassNode>
	}
	
	/**
	 * Retrieves all ASTNodes across all source files or a specific URI.
	 * @param uri (optional) Source file URI to filter nodes.
	 * @return Set<ASTNode> - Empty set if none found.
	 */
	Set<ASTNode> getNodes(String uri = null) {
		if (uri) return nodesByURI.getOrDefault(uri, [] as Set)
		return nodesByURI.values().stream().flatMap(Set::stream).collect(Collectors.toSet())
	}
	
	// ==== Position-Based Queries ====
	
	/**
	 * Finds the most specific ASTNode at a given position
	 * @param uri Source file URI to search
	 * @param position Position (1-based)
	 * @return ASTNode - Null if no matching node
	 */
	ASTNode getNodeAtPosition(String uri, Position position) {
		Set<ASTNode> candidates = getNodesAtPosition(uri, position)
		return findMostSpecificNode(candidates)
	}
	
	/**
	 * Finds the most specific ASTNode at a given line/column position
	 * @param uri Source file URI to search
	 * @param line Line number (1-based)
	 * @param column Column number (1-based)
	 * @return ASTNode - Null if no matching node
	 */
	ASTNode getNodeAtLineAndColumn(String uri, int line, int column) {
		Position position = new Position(line, column)
		Set<ASTNode> candidates = getNodesAtPosition(uri, position)
		return findMostSpecificNode(candidates)
	}
	
	/**
	 * Finds all ASTNodes at a specific line
	 * @param uri Source file URI to search
	 * @param line Line number (1-based)
	 * @return Set<ASTNode> - Empty set if none found
	 */
	Set<ASTNode> getNodesAtLine(String uri, int line) {
		return getNodes(uri).findAll { it.lineNumber == line }
	}
	
	// ==== AST Hierarchy Methods ====
	
	/**
	 * Gets the immediate parent of an ASTNode
	 * @param child Node to find parent for
	 * @return ASTNode - Null if root node
	 */
	ASTNode getParent(ASTNode child) {
		if (!child) return null
		return lookup[new ASTLookupKey(node: child)]?.parent
	}
	
	/**
	 * Checks if ancestor contains descendant in AST hierarchy
	 * @param ancestor Potential parent node
	 * @param descendant Potential child node
	 * @return boolean - True if ancestor is in parent chain
	 */
	boolean contains(ASTNode ancestor, ASTNode descendant) {
		if (!ancestor || !descendant) return false
		Set<ASTNode> visited = new HashSet<>()
		ASTNode current = descendant
		
		while (current && visited.add(current)) {
			if (current == ancestor) return true
			current = getParent(current)
		}
		return false
	}
	
	// ==== Node Metadata ====
	
	/**
	 * Gets source URI for a node
	 * @param node ASTNode to locate
	 * @return String - Null if node not tracked
	 */
	String getURI(ASTNode node) {
		return node ? lookup[new ASTLookupKey(node: node)]?.uri : null
	}
	
	// ==== Implementation Details ====
	
	private Set<ASTNode> getNodesAtPosition(String uri, Position position) {
		// Fast line-based lookup instead of scanning all nodes
		def lineIndex = nodesByLineIndex.get(uri)
		if (!lineIndex) return Collections.emptySet()
		
		Set<ASTNode> candidates = lineIndex.getOrDefault(position.line + 1, Collections.emptySet())
		// Set<ASTNode> nodes = getNodes(uri)
		//		return nodes.findAll { node ->
		//			node.lineNumber != -1 && RangeHelper.isPositionWithinNode(node, position)
		//		}
		return candidates.findAll { RangeHelper.isPositionWithinNode(it, position) }
	}
	
	private ASTNode findMostSpecificNode(Set<ASTNode> candidates) {
		if (candidates.empty) return null
		
		return candidates.sort { n1, n2 ->
			// Prefer nodes that start later
			int startCompare = PositionHelper.compareStartPositions(n1, n2)
			if (startCompare != 0) return startCompare
			
			// Prefer nodes that end earlier
			int endCompare = PositionHelper.compareEndPositions(n1, n2)
			if (endCompare != 0) return endCompare
			
			// Parent nodes before children
			return contains(n1, n2) ? -1 : contains(n2, n1) ? 1 : 0
		}.first()
	}
	
	//=============================================================================
	// Visitor
	//=============================================================================
	
	void visitSourceUnit(SourceUnit unit) {
		if (!unit) return
		String uri = TextFile.normalizePath(unit.name)
		if (!uri) return
		log.debug "Visiting related file for definition: ${uri}"
		
		visitCounts.clear()
		
		// Smart memory management - only cleanup if safe
		if (nodesByURI.size() >= MAX_FILES_IN_MEMORY) {
			cleanupUnusedFiles()
		}
		
		// Reset data for this source unit
		nodesByURI.remove(uri)
		classNodesByURI.remove(uri)
		nodesByLineIndex.remove(uri)
		lookup.entrySet().removeIf { entry -> entry.value.uri == uri }
		
		// Initialize fresh state
		nodesByURI[uri] = [] as Set
		classNodesByURI[uri] = [] as Set
		stack.clear()
		
		sourceUnit = unit
		visitModule(unit.AST)
		sourceUnit = null
		
		printVisitCounts()
	}
	
	void visitModule(ModuleNode node) {
		if (!node) return
		pushASTNode(node)
		try {
			node.classes.each { visitClass(it) }
		} finally {
			popASTNode()
		}
	}
	
	void visitClass(ClassNode node) {
		def uri = TextFile.normalizePath(sourceUnit.name)
		classNodesByURI[uri].add(node)
		pushASTNode(node)
		try {
			processUnresolvedType(node.unresolvedSuperClass)
			node.unresolvedInterfaces.each { processUnresolvedType(it) }
			super.visitClass(node)
		} finally {
			popASTNode()
		}
	}
	
	private void processUnresolvedType(ClassNode type) {
		if (type?.lineNumber == -1) return
		pushASTNode(type)
		popASTNode()
	}
	
	//	void visitAnnotations(AnnotatedNode node) {
	//		pushASTNode(node)
	//		try {
	//			super.visitAnnotations(node.getAnnotations())
	//		} finally {
	//			popASTNode()
	//		}
	//	}
	
	void visitImports(ModuleNode node) {
		if (!node) return
		
		node.imports?.each { importNode ->
			pushASTNode(importNode)
			visitAnnotations(importNode)
			importNode.visit(this)
			popASTNode()
		}
		
		node.starImports?.each { starImportNode ->
			pushASTNode(starImportNode)
			visitAnnotations(starImportNode)
			starImportNode.visit(this)
			popASTNode()
		}
		
		node.staticImports?.values()?.each { staticImportNode ->
			pushASTNode(staticImportNode)
			visitAnnotations(staticImportNode)
			staticImportNode.visit(this)
			popASTNode()
		}
		
		node.staticStarImports?.values()?.each { staticStarImportNode ->
			pushASTNode(staticStarImportNode)
			visitAnnotations(staticStarImportNode)
			staticStarImportNode.visit(this)
			popASTNode()
		}
	}
	
	void visitPackage(PackageNode node) {
		if (!node) return
		
		pushASTNode(node)
		try {
			super.visitPackage(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitConstructor(ConstructorNode node) {
		pushASTNode(node)
		try {
			super.visitConstructor(node)
			node.parameters.each { visitParameter(it) }
		} finally {
			popASTNode()
		}
	}
	
	void visitMethod(MethodNode node) {
		pushASTNode(node)
		try {
			super.visitMethod(node)
			node.parameters.each { visitParameter(it) }
		} finally {
			popASTNode()
		}
	}
	
	protected void visitParameter(Parameter node) {
		pushASTNode(node)
		try {
			super.visitAnnotations(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitField(FieldNode node) {
		pushASTNode(node)
		try {
			super.visitField(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitProperty(PropertyNode node) {
		pushASTNode(node)
		try {
			super.visitProperty(node)
		} finally {
			popASTNode()
		}
	}
	
	// GroovyCodeVisitor
	
	void visitBlockStatement(BlockStatement node) {
		pushASTNode(node)
		try {
			super.visitBlockStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitForLoop(ForStatement node) {
		pushASTNode(node)
		try {
			super.visitForLoop(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitWhileLoop(WhileStatement node) {
		pushASTNode(node)
		try {
			super.visitWhileLoop(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitDoWhileLoop(DoWhileStatement node) {
		pushASTNode(node)
		try {
			super.visitDoWhileLoop(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitIfElse(IfStatement node) {
		pushASTNode(node)
		try {
			super.visitIfElse(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitExpressionStatement(ExpressionStatement node) {
		pushASTNode(node)
		try {
			super.visitExpressionStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitReturnStatement(ReturnStatement node) {
		pushASTNode(node)
		try {
			super.visitReturnStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitAssertStatement(AssertStatement node) {
		pushASTNode(node)
		try {
			super.visitAssertStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitTryCatchFinally(TryCatchStatement node) {
		pushASTNode(node)
		try {
			super.visitTryCatchFinally(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitEmptyStatement(EmptyStatement node) {
		pushASTNode(node)
		try {
			super.visitEmptyStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitSwitch(SwitchStatement node) {
		pushASTNode(node)
		try {
			super.visitSwitch(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitCaseStatement(CaseStatement node) {
		pushASTNode(node)
		try {
			super.visitCaseStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitBreakStatement(BreakStatement node) {
		pushASTNode(node)
		try {
			super.visitBreakStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitContinueStatement(ContinueStatement node) {
		pushASTNode(node)
		try {
			super.visitContinueStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitSynchronizedStatement(SynchronizedStatement node) {
		pushASTNode(node)
		try {
			super.visitSynchronizedStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitThrowStatement(ThrowStatement node) {
		pushASTNode(node)
		try {
			super.visitThrowStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitMethodCallExpression(MethodCallExpression node) {
		pushASTNode(node)
		try {
			super.visitMethodCallExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitStaticMethodCallExpression(StaticMethodCallExpression node) {
		pushASTNode(node)
		try {
			super.visitStaticMethodCallExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitConstructorCallExpression(ConstructorCallExpression node) {
		pushASTNode(node)
		try {
			super.visitConstructorCallExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitBinaryExpression(BinaryExpression node) {
		pushASTNode(node)
		try {
			super.visitBinaryExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitTernaryExpression(TernaryExpression node) {
		pushASTNode(node)
		try {
			super.visitTernaryExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitShortTernaryExpression(ElvisOperatorExpression node) {
		pushASTNode(node)
		try {
			super.visitShortTernaryExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitPostfixExpression(PostfixExpression node) {
		pushASTNode(node)
		try {
			super.visitPostfixExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitPrefixExpression(PrefixExpression node) {
		pushASTNode(node)
		try {
			super.visitPrefixExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitBooleanExpression(BooleanExpression node) {
		pushASTNode(node)
		try {
			super.visitBooleanExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitNotExpression(NotExpression node) {
		pushASTNode(node)
		try {
			super.visitNotExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitClosureExpression(ClosureExpression node) {
		pushASTNode(node)
		try {
			super.visitClosureExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitTupleExpression(TupleExpression node) {
		pushASTNode(node)
		try {
			super.visitTupleExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitListExpression(ListExpression node) {
		pushASTNode(node)
		try {
			super.visitListExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitArrayExpression(ArrayExpression node) {
		pushASTNode(node)
		try {
			super.visitArrayExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitMapExpression(MapExpression node) {
		pushASTNode(node)
		try {
			super.visitMapExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitMapEntryExpression(MapEntryExpression node) {
		pushASTNode(node)
		try {
			super.visitMapEntryExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitRangeExpression(RangeExpression node) {
		pushASTNode(node)
		try {
			super.visitRangeExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitSpreadExpression(SpreadExpression node) {
		pushASTNode(node)
		try {
			super.visitSpreadExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitSpreadMapExpression(SpreadMapExpression node) {
		pushASTNode(node)
		try {
			super.visitSpreadMapExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitMethodPointerExpression(MethodPointerExpression node) {
		pushASTNode(node)
		try {
			super.visitMethodPointerExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitUnaryMinusExpression(UnaryMinusExpression node) {
		pushASTNode(node)
		try {
			super.visitUnaryMinusExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitUnaryPlusExpression(UnaryPlusExpression node) {
		pushASTNode(node)
		try {
			super.visitUnaryPlusExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitBitwiseNegationExpression(BitwiseNegationExpression node) {
		pushASTNode(node)
		try {
			super.visitBitwiseNegationExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitCastExpression(CastExpression node) {
		pushASTNode(node)
		try {
			super.visitCastExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitConstantExpression(ConstantExpression node) {
		pushASTNode(node)
		try {
			super.visitConstantExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitClassExpression(ClassExpression node) {
		pushASTNode(node)
		try {
			super.visitClassExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitVariableExpression(VariableExpression node) {
		pushASTNode(node)
		try {
			super.visitVariableExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	// this calls visitBinaryExpression()
	// public void visitDeclarationExpression(DeclarationExpression node) {
	// pushASTNode(node);
	// try {
	// super.visitDeclarationExpression(node);
	// } finally {
	// popASTNode();
	// }
	// }
	
	void visitPropertyExpression(PropertyExpression node) {
		pushASTNode(node)
		try {
			super.visitPropertyExpression(node)
			//			pushASTNode(node.property)
			//			node.getProperty().visit(this)
			//			popASTNode()
		} finally {
			popASTNode()
		}
	}
	
	void visitAttributeExpression(AttributeExpression node) {
		pushASTNode(node)
		try {
			super.visitAttributeExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitFieldExpression(FieldExpression node) {
		pushASTNode(node)
		try {
			super.visitFieldExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitGStringExpression(GStringExpression node) {
		pushASTNode(node)
		try {
			super.visitGStringExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitCatchStatement(CatchStatement node) {
		pushASTNode(node)
		try {
			super.visitCatchStatement(node)
		} finally {
			popASTNode()
		}
	}
	
	// this calls visitTupleListExpression()
	// public void visitArgumentlistExpression(ArgumentListExpression node) {
	// pushASTNode(node);
	// try {
	// super.visitArgumentlistExpression(node);
	// } finally {
	// popASTNode();
	// }
	// }
	
	void visitClosureListExpression(ClosureListExpression node) {
		pushASTNode(node)
		try {
			super.visitClosureListExpression(node)
		} finally {
			popASTNode()
		}
	}
	
	void visitBytecodeExpression(BytecodeExpression node) {
		pushASTNode(node)
		try {
			super.visitBytecodeExpression(node)
		} finally {
			popASTNode()
		}
	}
}