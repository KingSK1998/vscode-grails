package kingsk.grails.lsp.providersDocument

import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.services.FileContentTracker
import kingsk.grails.lsp.utils.ASTUtils
import kingsk.grails.lsp.utils.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture

@Slf4j
class GrailsRenameProvider {
	
	GrailsASTVisitor visitor
	FileContentTracker files
	
	GrailsRenameProvider(GrailsASTVisitor visitor, FileContentTracker files) {
		this.visitor = visitor
		this.files = files
	}
	
	CompletableFuture<WorkspaceEdit> provideRename(RenameParams params) {
		Map<String, List<TextEdit>> textEditChanges = [:]
		List<Either<TextDocumentEdit, ResourceOperation>> documentChanges = []
		WorkspaceEdit workspaceEdit = new WorkspaceEdit(documentChanges)
		
		if (!visitor) {
			log.warn("[RENAME] AST is null, returning null.")
			return CompletableFuture.completedFuture(workspaceEdit)
		}
		
		URI documentURI = URI.create(params.textDocument.uri)
		ASTNode offsetNode = visitor.getNodeAtLineAndColumn(documentURI, params.position.line, params.position.character)
		if (!offsetNode) {
			log.warn("[RENAME] No ASTNode found at the specified position.")
			return CompletableFuture.completedFuture(workspaceEdit)
		}
		
		List<ASTNode> references = GrailsASTHelper.getReferences(offsetNode, visitor, params.position)
		references.each { node ->
			URI uri = visitor.getURI(node) ?: documentURI
			String contents = getPartialNodeText(uri, node)
			if (!contents) return // can't find the text? skip it
			Range range = ASTUtils.astNodeToRange(node)
			if (!range) return // can't find the range? skip it
			
			Position start = range.start
			Position end = range.end
			end.line = start.line
			end.character = start.character + contents.length()
			
			TextEdit textEdit = null
			if (node instanceof ClassNode) {
				textEdit = createTextEditToRenameClassNode(node, newName, contents, range)
				if (textEdit && !visitor.getParent(node)) {
					String newURI = uri.toString()
					int slashIndex = newURI.lastIndexOf("/")
					int dotIndex = newURI.lastIndexOf(".")
					newURI = newURI.substring(0, slashIndex + 1) + newName + newURI.substring(dotIndex)
					
					RenameFile renameFile = new RenameFile(oldUri: uri.toString(), newUri: newURI)
					documentChanges.add(Either.forRight(renameFile))
				}
			} else if (node instanceof MethodNode) {
				textEdit = createTextEditToRenameMethodNode(node, newName, contents, range)
			} else if (node instanceof PropertyNode) {
				textEdit = createTextEditToRenamePropertyNode(node, newName, contents, range)
			} else if (node instanceof ConstantExpression || node instanceof VariableExpression) {
				textEdit = new TextEdit(newText: newName, range: range)
			}
			if (!textEdit) return
			
			def uriKey = uri.toString()
			if (!textEditChanges.containsKey(uriKey)) {
				textEditChanges[uriKey] = []
			}
			
			textEditChanges[uriKey].add(textEdit)
		}
		
		textEditChanges.each { uri, textEdits ->
			def versionedIdentifier = new VersionedTextDocumentIdentifier(uri, null)
			def textDocumentEdit = new TextDocumentEdit(versionedIdentifier, textEdits)
			documentChanges.add(0, Either.forLeft(textDocumentEdit))
		}
		
		return CompletableFuture.completedFuture(workspaceEdit)
	}
	
	String getPartialNodeText(URI uri, ASTNode node) {
		def range = ASTUtils.astNodeToRange(node)
		if (!range) return null
		
		def contents = files.getContents(uri)
		if (contents == null) return null
		
		return Ranges.getSubstring(contents, range, 1)
	}
	
	static TextEdit createTextEditToRenameClassNode(ClassNode classNode, String newName, String text, Range range) {
		def className = classNode.nameWithoutPackage
		def dollarIndex = className.indexOf('$')
		if (dollarIndex != 01) {
			className = className.substring(dollarIndex + 1)
		}
		
		def classMatcher = (text =~ /class\s+$className\b/)
		if (!classMatcher.find()) return null
		
		def prefix = classMatcher.group(1)
		def start = range.start
		def end = range.end
		end.character = start.character + classMatcher.end()
		start.character = start.character + prefix.length() + classMatcher.start()
		
		return new TextEdit(range: range, newText: newName)
	}
	
	static TextEdit createTextEditToRenameMethodNode(MethodNode methodNode, String newName, String text, Range range) {
		def methodMatcher = (text =~ /\b${methodNode.name}\b(?=\s*\()/)
		if (!methodMatcher.find()) return null
		
		def start = range.start
		def end = range.end
		end.character = start.character + methodMatcher.end()
		start.character = start.character + methodMatcher.start()
		return new TextEdit(range: range, newText: newName)
	}
	
	static TextEdit createTextEditToRenamePropertyNode(PropertyNode node, String newName, String text, Range range) {
		def propMatcher = (text =~ /\b${node.name}\b/)
		if (!propMatcher.find()) return null
		
		def start = range.start
		def end = range.end
		end.character = start.character + propMatcher.end()
		start.character = start.character + propMatcher.start()
		
		return new TextEdit(range: range, newText: newName)
	}
}