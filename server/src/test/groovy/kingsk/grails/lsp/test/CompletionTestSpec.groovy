package kingsk.grails.lsp.test

import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import spock.lang.Shared

/**
 * Base class for testing completion functionality
 */
abstract class CompletionTestSpec extends BaseLspSpec {
	
	@Shared
	protected int completionTimeout = 5000
	// ms
	
	/**
	 * Get completion items at a specific position in a document
	 * @param uri The URI of the document
	 * @param line The line number (0-based)
	 * @param character The character number (0-based)
	 * @return The completion items
	 */
	protected List<CompletionItem> getCompletionItems(String uri, int line, int character) {
		def params = new CompletionParams(new TextDocumentIdentifier(uri), pos(line, character))
		def completionResult = waitForFuture(
				grailsService.document.completion(params),
				completionTimeout
		)
		
		if (completionResult.isLeft()) {
			return completionResult.getLeft()
		} else {
			return completionResult.getRight().getItems()
		}
	}
	
	/**
	 * Assert that completion items contain a specific item
	 * @param items The completion items
	 * @param label The label of the item to find
	 * @return The found item
	 */
	protected CompletionItem assertContainsItem(List<CompletionItem> items, String label) {
		def item = items.find { it.label == label }
		assert item != null, "Completion items should contain item with label '${label}'"
		return item
	}
	
	/**
	 * Assert that completion items contain items with specific labels
	 * @param items The completion items
	 * @param labels The labels to find
	 */
	protected void assertContainsItems(List<CompletionItem> items, List<String> labels) {
		labels.each { label ->
			assertContainsItem(items, label)
		}
	}
	
	/**
	 * Assert that completion items do not contain a specific item
	 * @param items The completion items
	 * @param label The label of the item that should not be present
	 */
	protected void assertNotContainsItem(List<CompletionItem> items, String label) {
		def item = items.find { it.label == label }
		assert item == null, "Completion items should not contain item with label '${label}'"
	}
}