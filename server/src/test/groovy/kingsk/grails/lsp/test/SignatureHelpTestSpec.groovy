package kingsk.grails.lsp.test


import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.TextDocumentIdentifier
import spock.lang.Shared

/**
 * Base class for testing signature help functionality
 */
abstract class SignatureHelpTestSpec extends BaseLspSpec {
	
	@Shared
	protected int signatureHelpTimeout = 3000
	// ms
	
	/**
	 * Get signature help at a specific position in a document
	 * @param uri The URI of the document
	 * @param line The line number (0-based)
	 * @param character The character number (0-based)
	 * @return The signature help
	 */
	protected SignatureHelp getSignatureHelp(String uri, int line, int character) {
		def params = new SignatureHelpParams(new TextDocumentIdentifier(uri), pos(line, character))
		return waitForFuture(
				grailsService.document.signatureHelp(params),
				signatureHelpTimeout
		)
	}
	
	/**
	 * Assert that signature help contains a specific signature
	 * @param signatureHelp The signature help
	 * @param expectedLabel The expected signature label
	 * @return The found signature
	 */
	protected SignatureInformation assertContainsSignature(SignatureHelp signatureHelp, String expectedLabel) {
		def signature = signatureHelp.signatures.find { it.label == expectedLabel }
		assert signature != null, "Signature help should contain signature with label '${expectedLabel}'"
		return signature
	}
	
	/**
	 * Assert that signature help has the expected active signature and parameter
	 * @param signatureHelp The signature help
	 * @param expectedActiveSignature The expected active signature index
	 * @param expectedActiveParameter The expected active parameter index
	 */
	protected void assertActiveSignatureAndParameter(SignatureHelp signatureHelp, int expectedActiveSignature, int expectedActiveParameter) {
		assert signatureHelp.activeSignature == expectedActiveSignature, "Expected active signature ${expectedActiveSignature}, but got ${signatureHelp.activeSignature}"
		assert signatureHelp.activeParameter == expectedActiveParameter, "Expected active parameter ${expectedActiveParameter}, but got ${signatureHelp.activeParameter}"
	}
	
	/**
	 * Assert that a signature has the expected parameters
	 * @param signature The signature
	 * @param expectedParameters The expected parameter labels
	 */
	protected void assertSignatureParameters(SignatureInformation signature, List<String> expectedParameters) {
		assert signature.parameters.size() == expectedParameters.size(), "Expected ${expectedParameters.size()} parameters, but got ${signature.parameters.size()}"
		
		expectedParameters.eachWithIndex { expectedParam, index ->
			def actualParam = signature.parameters[index]
			assert actualParam.label.get() == expectedParam, "Expected parameter ${index} to be '${expectedParam}', but got '${actualParam.label.get()}'"
		}
	}
}