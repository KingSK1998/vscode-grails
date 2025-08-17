package kingsk.grails.lsp.test

import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TypeDefinitionParams
import spock.lang.Shared

/**
 * Base class for testing type definition functionality
 */
abstract class TypeDefinitionTestSpec extends BaseLspSpec {
	
	@Shared
	protected int typeDefinitionTimeout = 3000
	// ms
	
	/**
	 * Get type definition locations for a specific position in a document
	 * @param uri The URI of the document
	 * @param line The line number (0-based)
	 * @param character The character number (0-based)
	 * @return The type definition locations
	 */
	protected List<Location> getTypeDefinitionLocations(String uri, int line, int character) {
		def params = new TypeDefinitionParams(new TextDocumentIdentifier(uri), pos(line, character))
		def typeDefinitionResult = waitForFuture(
				grailsService.document.typeDefinition(params),
				typeDefinitionTimeout
		)
		
		if (typeDefinitionResult.isLeft()) {
			return typeDefinitionResult.getLeft()
		} else {
			return typeDefinitionResult.getRight()
		}
	}
	
	/**
	 * Assert that type definition locations contain a specific location
	 * @param locations The type definition locations
	 * @param uri The URI of the location to find
	 * @param startLine The start line of the location to find
	 * @param startChar The start character of the location to find
	 * @param endLine The end line of the location to find
	 * @param endChar The end character of the location to find
	 * @return The found location
	 */
	protected Location assertContainsTypeDefinitionLocation(List<Location> locations, String uri, int startLine, int startChar, int endLine, int endChar) {
		def location = locations.find {
			it.uri == uri &&
					it.range.start.line == startLine &&
					it.range.start.character == startChar &&
					it.range.end.line == endLine &&
					it.range.end.character == endChar
		}
		assert location != null, "Type definition locations should contain location with URI '${uri}' and range (${startLine},${startChar})-(${endLine},${endChar})"
		return location
	}
	
	/**
	 * Assert that type definition locations do not contain a specific location
	 * @param locations The type definition locations
	 * @param uri The URI of the location that should not be present
	 */
	protected void assertNotContainsTypeDefinitionLocation(List<Location> locations, String uri) {
		def location = locations.find { it.uri == uri }
		assert location == null, "Type definition locations should not contain location with URI '${uri}'"
	}
	
	/**
	 * Assert that there is exactly one type definition location
	 * @param locations The type definition locations
	 * @return The single location
	 */
	protected Location assertSingleTypeDefinitionLocation(List<Location> locations) {
		assert locations.size() == 1, "Expected exactly one type definition location, but got ${locations.size()}"
		return locations[0]
	}
}