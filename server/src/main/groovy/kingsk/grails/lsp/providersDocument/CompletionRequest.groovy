package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.model.GrailsArtifactType
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.utils.CompletionUtil
import kingsk.grails.lsp.utils.GrailsASTHelper
import kingsk.grails.lsp.utils.GrailsArtefactUtils
import kingsk.grails.lsp.utils.MemberExtractor.CompletionItems
import kingsk.grails.lsp.utils.ScopeHelper.ScopeItems
import org.codehaus.groovy.ast.*
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.Position

/**
 * Encapsulates a single code completion request.
 *
 * Holds context such as AST nodes, variables prefix, file info, and result collection.
 * Supports prefix filtering and de-duplication of suggestions.
 */
@CompileStatic
class CompletionRequest {
	final ASTNode offsetNode
	final ASTNode parentNode
	final String prefix
	final Position position
	final List<CompletionItem> items
	final Set<String> seen
	final TextFile file
	final boolean isGrailsProject
	final GrailsService service
	
	// Lazy-loaded high-level context (expensive to compute)
	private ModuleNode _currentModule
	private ClassNode _currentClass
	private MethodNode _currentMethod
	// get the node type of the current node for offsetNode
	private ClassNode _offsetNodeType
	// get the node type of the current node for parentNode
	private ClassNode _parentNodeType
	private GrailsArtifactType _artifactType
	
	private boolean _currentModuleComputed = false
	private boolean _currentClassComputed = false
	private boolean _currentMethodComputed = false
	private boolean _offsetNodeTypeComputed = false
	private boolean _parentNodeTypeComputed = false
	private boolean _artifactTypeComputed = false
	
	CompletionRequest(ASTNode offsetNode, ASTNode parentNode, String prefix, Position position,
	                  List<CompletionItem> items, Set<String> seen, TextFile file,
	                  boolean isGrailsProject, GrailsService service) {
		this.offsetNode = offsetNode
		this.parentNode = parentNode
		this.prefix = prefix
		this.position = position
		this.items = items
		this.seen = seen
		this.file = file
		this.isGrailsProject = isGrailsProject
		this.service = service
	}
	
	GrailsASTVisitor getVisitor() {
		return service.visitor
	}
	
	/**
	 * Adds a completion item if it hasn't been seen before and matches prefix
	 * @param node The AST node to create completion from
	 */
	void addCompletion(ASTNode node) {
		if (!node || !CompletionUtil.isSeenItem(node, prefix, seen)) return
		items.add(CompletionUtil.buildCompletionItem(node))
	}
	
	/**
	 * Adds a completion item if it hasn't been seen before and matches prefix
	 * @param node the AST node to create completion from
	 * @param item The completion item to add
	 */
	void addCompletion(ASTNode node, CompletionItem item) {
		if (!node || !item || !CompletionUtil.isSeenItem(node, prefix, seen)) return
		items.add(item)
	}
	
	/**
	 * Adds a raw completion item if it hasn't been seen before and matches prefix
	 * @param item The pre-built completion item
	 */
	void addCompletion(CompletionItem item) {
		if (!item || !item.label) return
		if (!CompletionUtil.isSeenItem(item, prefix, seen)) return
		items.add(item)
	}
	
	/**
	 * Add all items from MemberExtractor's CompletionItems, i.e. Properties/Fields/Methods
	 * @param items The MemberExtractor.CompletionItems to add
	 */
	void addAllCompletions(CompletionItems items) {
		// addListCompletions(items.properties)
		addListCompletions(items.fields)
		addListCompletions(items.methods)
	}
	
	void addAllCompletions(ScopeItems items) {
		addVariableListCompletions(items.variables)
		addListCompletions(items.parameters)
		addAllCompletions(items.members)
	}
	
	/**
	 * Adds all items from list of ASTNode
	 * @param nodes The list of nodes to add, must extends ASTNode
	 */
	void addListCompletions(List<? extends ASTNode> nodes) {
		nodes?.each { addCompletion(it) }
	}
	
	/**
	 * Adds all items from list of Variable
	 * @param nodes The list of nodes to add, must extends ASTNode
	 */
	void addVariableListCompletions(List<? extends Variable> vars) {
		vars?.each { addVariableCompletion(it) }
	}
	
	void addVariableCompletion(Variable variable) {
		if (!variable instanceof ASTNode) return
		addCompletion(variable as ASTNode)
	}
	
	// --- Lazy Context Resolution ---
	
	/**
	 * Gets the current module node for the offset node
	 * @return ModuleNode or null if not found
	 */
	ModuleNode getCurrentModule() {
		if (!_currentModuleComputed) {
			_currentModule = GrailsASTHelper.getEnclosingModuleNode(offsetNode, service.visitor)
			_currentModuleComputed = true
		}
		return _currentModule
	}
	
	/**
	 * Gets the current class node for the offset node
	 * @return ClassNode or null if not found
	 */
	ClassNode getCurrentClass() {
		if (!_currentClassComputed) {
			_currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, service.visitor)
			_currentClassComputed = true
		}
		return _currentClass
	}
	
	/**
	 * Gets the current method node for the offset node
	 * @return MethodNode or null if not found
	 */
	MethodNode getCurrentMethod() {
		if (!_currentMethodComputed) {
			_currentMethod = GrailsASTHelper.getEnclosingMethodNode(offsetNode, service.visitor)
			_currentMethodComputed = true
		}
		return _currentMethod
	}
	
	/**
	 * Gets the Grails artifact type for the current class node
	 * @return GrailsArtifactType or UNKNOWN if not found
	 */
	GrailsArtifactType getArtefactType() {
		if (!_artifactTypeComputed) {
			_artifactType = GrailsArtefactUtils.getGrailsArtifactType(getCurrentClass(), file.uri)
			if (_artifactType.valid) {
				_artifactTypeComputed = true
			}
		}
		return _artifactType
	}
	
	boolean isArtifactTypeValid() {
		if (!isGrailsProject) return false
		if (!_artifactTypeComputed) {
			return getArtefactType().valid
		}
		return _artifactType.valid
	}
	
	ClassNode getOffsetNodeType() {
		if (!_offsetNodeTypeComputed) {
			_offsetNodeType = GrailsASTHelper.getTypeOfNode(offsetNode, service.visitor)
			_offsetNodeTypeComputed = true
		}
		return _offsetNodeType
	}
	
	ClassNode getParentNodeType() {
		if (!_parentNodeTypeComputed) {
			_parentNodeType = GrailsASTHelper.getTypeOfNode(parentNode, service.visitor)
			_parentNodeTypeComputed = true
		}
		return _parentNodeType
	}
}