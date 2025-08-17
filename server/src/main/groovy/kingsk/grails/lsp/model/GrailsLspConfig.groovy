package kingsk.grails.lsp.model

import com.google.gson.JsonObject
import kingsk.grails.lsp.utils.GrailsUtils

class GrailsLspConfig {
	enum CompletionDetailLevel {
		BASIC, // Only local symbols and keywords
		STANDARD, // + Project symbols
		ADVANCED // + Framework-specific completions
		
		static CompletionDetailLevel fromString(String value) {
			return values().find { it.name() == value } ?: ADVANCED
		}
	}
	
	CompletionDetailLevel completionDetail
	int COMPLETION_LIMIT
	boolean includeSnippets
	boolean enableGrailsMagic
	
	CodeLensMode codeLensMode
	int compilerPhase
	boolean shouldRecompileOnConfigChange
	
	GrailsLspConfig() {
		this.completionDetail = CompletionDetailLevel.ADVANCED
		this.COMPLETION_LIMIT = GrailsUtils.MAX_COMPLETION_ITEM_LIMIT
		this.includeSnippets = true
		this.enableGrailsMagic = true
		this.codeLensMode = CodeLensMode.ADVANCED
		this.compilerPhase = GrailsUtils.DEFAULT_COMPILATION_PHASE
		this.shouldRecompileOnConfigChange = false
	}
	
	void updateFromClient(JsonObject config) {
		def codeLens = config.get("codeLensMode")?.asString?.toUpperCase()
		this.codeLensMode = CodeLensMode.fromString(codeLens)
		
		this.compilerPhase = config.get("compilerPhase")?.asInt ?: GrailsUtils.DEFAULT_COMPILATION_PHASE
		this.shouldRecompileOnConfigChange = config.get("shouldRecompileOnChange")?.asBoolean ?: false
		
		def completionLevel = config.get("completionDetail")?.asString?.toUpperCase()
		this.completionDetail = CompletionDetailLevel.fromString(completionLevel)
		
		this.COMPLETION_LIMIT = config.get("maxCompletionItems")?.asInt ?: GrailsUtils.MAX_COMPLETION_ITEM_LIMIT
		
		this.includeSnippets = config.get("includeSnippets")?.asBoolean ?: true
		this.enableGrailsMagic = config.get("enableGrailsMagic")?.asBoolean ?: true
	}
}
