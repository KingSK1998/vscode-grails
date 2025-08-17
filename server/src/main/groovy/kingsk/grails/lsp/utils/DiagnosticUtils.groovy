package kingsk.grails.lsp.utils

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.messages.Message
import org.codehaus.groovy.control.messages.SimpleMessage
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity

@Slf4j
@CompileStatic
class DiagnosticUtils {
	
	/** Converts an error message to a diagnostic */
	static Diagnostic errorToDiagnostic(Message error) {
		if (error instanceof SyntaxErrorMessage) return syntaxErrorToDiagnostic(error)
		if (error instanceof SimpleMessage) return simpleMessageToDiagnostic(error)
		return null
	}
	
	/** Converts an syntax error message to a diagnostic */
	static Diagnostic syntaxErrorToDiagnostic(SyntaxErrorMessage errorMessage) {
		if (!errorMessage) return null
		if (!errorMessage.cause?.sourceLocator) return null
		
		return new Diagnostic(
				message: errorMessage.cause.message,
				source: errorMessage.cause.sourceLocator ?: "Unknown Source",
				range: RangeHelper.syntaxErrorToRange(errorMessage),
				severity: DiagnosticSeverity.Error
		)
	}
	
	/** Converts an simple message to a diagnostic */
	@CompileDynamic
	static Diagnostic simpleMessageToDiagnostic(SimpleMessage simpleMessage) {
		if (!simpleMessage) return null
		
		def owner = simpleMessage.metaPropertyValues?.owner
		def source = (owner instanceof SourceUnit) ? owner.name : "Unknown Source"
		
		return new Diagnostic(
				message: simpleMessage.message,
				source: source,
				range: RangeHelper.zeroRange(),
				severity: DiagnosticSeverity.Information
		)
	}
	
	/** Converts an warning message to a diagnostic */
	static Diagnostic warningToDiagnostic(WarningMessage warning) {
		if (!warning?.context) return null
		
		return new Diagnostic(
				message: warning.message,
				source: warning.context.rootText ?: "Unknown Source",
				range: RangeHelper.warningToRange(warning),
				severity: determineWarningSeverity(warning)
		)
	}
	
	/** Determines the severity level for a warning message */
	static DiagnosticSeverity determineWarningSeverity(WarningMessage warning) {
		if (warning.isRelevant(WarningMessage.NONE)) return DiagnosticSeverity.Hint
		if (warning.isRelevant(WarningMessage.POSSIBLE_ERRORS)) return DiagnosticSeverity.Information
		return DiagnosticSeverity.Warning
	}
	
	/**
	 * Computes a simple hash from diagnostics contents
	 * @param diagnostics List of diagnostics to hash
	 * @return A hex string representing the hash
	 */
	static String computeResultId(Set<Diagnostic> diagnostics) {
		if (!diagnostics) return null
		
		def builder = new StringBuilder()
		diagnostics.each { d ->
			builder.append(d.message).append(d.range).append(d.severity)
		}
		return Integer.toHexString(builder.toString().hashCode())
	}
}
