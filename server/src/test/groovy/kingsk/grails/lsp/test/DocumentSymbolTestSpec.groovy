package kingsk.grails.lsp.test

import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.SymbolKind
import org.eclipse.lsp4j.TextDocumentIdentifier
import spock.lang.Shared

class DocumentSymbolTestSpec extends BaseLspSpec {
	
	@Shared
	protected int documentSymbolTimeout = 3000
	
	protected DocumentSymbol getDocumentSymbol(String uri) {
		def params = new DocumentSymbolParams(new TextDocumentIdentifier(uri))
		def result = waitForFuture(
				grailsService.document.documentSymbol(params),
				// documentSymbolTimeout
		)
		
		return result?.first()?.getRight()
	}
	
	// --- Assertions ---
	
	protected void assertClassSymbol(DocumentSymbol symbol, String expectedName) {
		assert symbol != null: "Expected a DocumentSymbol but got null"
		assert symbol.kind == SymbolKind.Class: "Expected symbol kind Class but got ${symbol.kind}"
		assert symbol.name == expectedName: "Expected class name '${expectedName}' but got '${symbol.name}'"
	}
	
	protected DocumentSymbol assertMethodSymbol(DocumentSymbol classSymbol, String methodName) {
		assert classSymbol?.children: "Class symbol '${classSymbol?.name}' has no children"
		def method = classSymbol.children.find { it.name == methodName && it.kind == SymbolKind.Method }
		assert method != null: "Expected method '${methodName}' in class '${classSymbol.name}', but not found"
		return method
	}
	
	protected DocumentSymbol assertConstructorSymbol(DocumentSymbol classSymbol, String name = null) {
		def constructor = classSymbol.children.find {
			it.kind == SymbolKind.Constructor && (!name || it.name == name)
		}
		assert constructor != null: "Expected constructor ${name ?: ''} in class '${classSymbol.name}', but not found"
		return constructor
	}
	
	protected DocumentSymbol assertFieldSymbol(DocumentSymbol classSymbol, String fieldName) {
		assert classSymbol.children != null
		def fieldSymbol = classSymbol.children.find {
			it.name == fieldName && (it.kind == SymbolKind.Field || it.kind == SymbolKind.Property)
		}
		assert fieldSymbol != null: "Expected Grails field '${fieldName}' in class '${classSymbol.name}'"
		return fieldSymbol
	}
	
	
	protected DocumentSymbol assertPropertySymbol(DocumentSymbol classSymbol, String propertyName) {
		def prop = classSymbol.children.find { it.name == propertyName && it.kind == SymbolKind.Property }
		assert prop != null: "Expected property '${propertyName}' in class '${classSymbol.name}', but not found"
		return prop
	}
	
	protected void assertNoSymbol(DocumentSymbol classSymbol, String name, SymbolKind kind) {
		def found = classSymbol.children?.find { it.name == name && it.kind == kind }
		assert found == null: "Did not expect symbol '${name}' of kind ${kind}, but it was found"
	}
	
	protected void assertClassHasChildren(DocumentSymbol classSymbol, int expectedCount) {
		assert classSymbol.children != null: "Expected class to have children but got null"
		assert classSymbol.children.size() == expectedCount:
				"Expected ${expectedCount} children, but got ${classSymbol.children.size()}"
	}
	
	// ─── Flatten Helper  ───
	
	protected List<DocumentSymbol> flattenSymbols(DocumentSymbol root) {
		def result = []
		def stack = [root]
		while (!stack.isEmpty()) {
			def current = stack.pop()
			result << current
			if (current.children) stack.addAll(current.children)
		}
		return result
	}
}
