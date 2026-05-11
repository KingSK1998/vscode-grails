package kingsk.grails.lsp.model.enums

enum DocumentationType {
	HOVER, // Detailed info with sections
	COMPLETION, // Concise, actionable info
	SIGNATURE_HELP, // Parameter info focused
	DIAGNOSTIC, // Error or warning context
	DEFINITION, // Jump-to-definition context
	REFERENCE, // Find references context
}
