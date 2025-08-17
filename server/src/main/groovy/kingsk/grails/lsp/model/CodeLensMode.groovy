package kingsk.grails.lsp.model

enum CodeLensMode {
	OFF,
	BASIC,
	ADVANCED,
	FULL
	
	static CodeLensMode fromString(String value) {
		values().find { it.name() == value } ?: ADVANCED
	}
}