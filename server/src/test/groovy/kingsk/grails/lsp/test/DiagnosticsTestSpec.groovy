package kingsk.grails.lsp.test

import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import spock.lang.Shared

/**
 * Base class for testing diagnostics functionality
 */
abstract class DiagnosticsTestSpec extends BaseLspSpec {
	
	@Shared
	protected int diagnosticsTimeout = 10000
	// ms
	
	/**
	 * Wait for diagnostics to be published for a specific URI
	 * @param uri The URI to wait for diagnostics for
	 * @param timeout The timeout in milliseconds
	 * @return The diagnostics for the URI
	 */
	protected List<Diagnostic> waitForDiagnostics(String uri, long timeout = diagnosticsTimeout) {
		long startTime = System.currentTimeMillis()
		while (System.currentTimeMillis() - startTime < timeout) {
			def diagnostics = mockClient.getDiagnosticsForUri(uri)
			if (!diagnostics.empty) {
				return diagnostics
			}
			Thread.sleep(100)
		}
		return []
	}
	
	/**
	 * Assert that diagnostics contain a specific diagnostic
	 * @param diagnostics The diagnostics
	 * @param message The message of the diagnostic to find
	 * @param severity The severity of the diagnostic to find
	 * @return The found diagnostic
	 */
	protected Diagnostic assertContainsDiagnostic(List<Diagnostic> diagnostics, String message, DiagnosticSeverity severity = null) {
		def diagnostic = diagnostics.find {
			it.message == message && (severity == null || it.severity == severity)
		}
		assert diagnostic != null, "Diagnostics should contain diagnostic with message '${message}'"
		return diagnostic
	}
}