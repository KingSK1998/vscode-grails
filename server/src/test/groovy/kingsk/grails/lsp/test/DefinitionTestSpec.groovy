package kingsk.grails.lsp.test

import kingsk.grails.lsp.model.TextFile
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.jsonrpc.messages.Either
import spock.lang.Shared

/**
 * Base class for testing definition functionality
 */
abstract class DefinitionTestSpec extends BaseLspSpec {
	
	@Shared
	protected int definitionTimeout = 3000
	// ms
	
	/**
	 * Get definition locations for a specific position in a document
	 * @param uri The URI of the document
	 * @param line The line number (0-based)
	 * @param character The character number (0-based)
	 * @return The definition locations
	 */
	protected Either<List<Location>, List<LocationLink>> getDefinitionLocations(String uri, int line, int character) {
		DefinitionParams params = new DefinitionParams(new TextDocumentIdentifier(uri), pos(line, character))
		return waitForFuture(
				grailsService.document.definition(params),
				definitionTimeout
		) as Either<List<Location>, List<LocationLink>>
	}
	
	protected static void assertLocation(def location, String expectedUri, int startLine, int startChar, int endLine, int endChar) {
		assert location.uri == TextFile.normalizePath(expectedUri)
		assert location.range.start.line == startLine
		assert location.range.start.character == startChar
		assert location.range.end.line == endLine
		assert location.range.end.character == endChar
	}
	
}