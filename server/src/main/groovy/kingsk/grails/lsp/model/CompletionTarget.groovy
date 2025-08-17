package kingsk.grails.lsp.model

enum CompletionTarget {
	OFFSET,
	PARENT,
	BOTH
	
	/**
	 * Returns true if this target matches the given phase.
	 * BOTH matches any phase.
	 */
	boolean matches(CompletionTarget phase) {
		this == BOTH || this == phase
	}
	
	// for parent
	boolean isParent() {
		return this == PARENT || this == BOTH
	}
	
	// for offset
	boolean isOffset() {
		return this == OFFSET || this == BOTH
	}
}