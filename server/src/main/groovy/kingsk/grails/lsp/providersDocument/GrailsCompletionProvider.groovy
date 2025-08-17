package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.DocumentationType
import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.providersDocument.completions.CompletionBuilder
import kingsk.grails.lsp.providersDocument.completions.CompletionProcessor
import kingsk.grails.lsp.utils.*
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class GrailsCompletionProvider {
	private final GrailsService service
	
	// Context-aware cache - key based on AST context, not position
	private final Map<String, CompletionCache> completionCache = new ConcurrentHashMap<>()
	
	private static final Set<Character> HARD_TRIGGERS = ['.', ':', '(', '['] as Set<Character>
	private static final Set<Character> SOFT_TRIGGERS = ['\t', '\n', ' '] as Set<Character>
	private static final int MAX_CACHE_SIZE = 100
	private static final long CACHE_TTL_MS = 30_000L
	
	GrailsCompletionProvider(GrailsService grailsService) {
		this.service = grailsService
	}
	
	/** Entry point for textDocument/completion */
	CompletableFuture<Either<List<CompletionItem>, CompletionList>> provideCompletions(
			TextDocumentIdentifier textDocument,
			Position position,
			CompletionContext context
	) {
		return CompletableFuture.supplyAsync {
			try {
				log.info("[COMPLETION] Providing Completions for: ${textDocument.uri}:${position.line}:${position.character}")
				TextFile textFile = service.fileTracker.getTextFile(textDocument.uri)
				return doProvideCompletions(textFile, position, context)
			} catch (Exception e) {
				log.error("[COMPLETION] Completion error for ${textDocument.uri}:${position.line}:${position.character}", e)
				return Either.forLeft(Collections.emptyList())
			}
		} as CompletableFuture<Either<List<CompletionItem>, CompletionList>>
	}
	
	private Either<List<CompletionItem>, CompletionList> doProvideCompletions(
			TextFile file, Position position, CompletionContext context
	) {
		if (!file?.text) {
			return Either.forLeft(Collections.emptyList())
		}
		
		TextFile patchedFile = service.compiler.getPatchedSourceUnitTextFile(file)
		if (file.text.length() != patchedFile.text.length()) {
			file = patchedFile
		}
		
		String lineText = file.textAtLine(position.line)
		String prefix = CompletionUtil.extractPrefixFromLine(lineText, position.character)
		int offset = PositionHelper.getOffset(file.text, position)
		Character lastChar = getLastCharacter(lineText, position.character)
		
		// Get AST nodes for context
		ASTNode offsetNode = service.visitor.getNodeAtPosition(file.uri, position)
		ASTNode parentNode = service.visitor.getParent(offsetNode)
		
		if (!offsetNode) {
			return Either.forLeft(Collections.emptyList())
		}
		
		CompletionTriggerInfo triggerInfo = analyzeTrigger(context, lastChar, prefix)
		
		// Generate context-aware cache key
		String cacheKey = generateContextCacheKey(file.uri, offsetNode, parentNode, prefix, triggerInfo)
		
		// Check cache - can use for both dummy and real prefixes since parent is stable
		if (triggerInfo.canUseCache) {
			CompletionCache cached = completionCache.get(cacheKey)
			if (cached?.isValidForContext(parentNode, prefix, offset)) {
				log.info("[COMPLETION] Using cached completions for parent '{}' with prefix '{}'",
						parentNode.getClass().simpleName, prefix)
				ClassNode currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, service.visitor)
				return cached.toResult(prefix, currentClass)
			}
		}
		
		// Generate completions
		CompletionContextInfo contextInfo = new CompletionContextInfo(offsetNode, parentNode, file)
		List<CompletionItem> items = generateCompletions(contextInfo, prefix, position)
		
		// Cache the complete result set
		CompletionCache newCache = new CompletionCache(
				offset, prefix, items, triggerInfo.isHardTrigger,
				offsetNode, parentNode, System.currentTimeMillis()
		)
		completionCache.put(cacheKey, newCache)
		cleanupCache()
		
		// Return filtered results
		ClassNode currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, service.visitor)
		return newCache.toResult(prefix, currentClass)
	}
	
	/**
	 * Generate context-aware cache key for smart prefix system
	 * Key is based on the parent node (completion source) rather than text parsing
	 */
	private static String generateContextCacheKey(String uri, ASTNode offsetNode, ASTNode parentNode,
	                                              String prefix, CompletionTriggerInfo triggerInfo) {
		StringBuilder key = new StringBuilder(uri)
		
		// Base context from parent node (the actual completion source)
		String baseContext = extractBaseContext(parentNode, prefix, triggerInfo)
		key.append(":").append(baseContext)
		
		// Add offset node type for additional context (unless it's dummy)
		if (offsetNode && !GrailsUtils.isDummyPrefix(prefix)) {
			key.append(":offset:").append(offsetNode.getClass().simpleName)
		}
		
		return key.toString()
	}
	
	/**
	 * Extract the base context for completion caching with smart prefix system
	 *
	 * Smart prefix examples:
	 * - "localVar.g" → offsetNode="g" : parentNode="localVar" -> prefix="g"
	 * - "localVar.getValue" → offsetNode="getValue" : parentNode="localVar" -> prefix="getValue"
	 * - "obj.method().prop" → offsetNode="prop" : parentNode="method" -> prefix="prop"
	 * - "localVar." → offsetNode="__DUMMY_PREFIX__" : parentNode="localVar" -> prefix="__DUMMY_PREFIX__"
	 * - "obj.method().prop." → offsetNode="__DUMMY_PREFIX__" : parentNode="prop" -> prefix="__DUMMY_PREFIX__"
	 *
	 * Cache key should be based on parentNode (completion source) + context type
	 */
	private static String extractBaseContext(ASTNode parentNode, String prefix, CompletionTriggerInfo triggerInfo) {
		if (!parentNode) return ""
		
		// The base context is defined by the parent node (completion source)
		// We don't need prefix parsing since parentNode already represents the "base"
		StringBuilder context = new StringBuilder()
		
		// Use parent node identity as the base context
		context.append(parentNode.getClass().simpleName)
		
		// Add position info for uniqueness
		if (parentNode.lineNumber != null) {
			context.append(":L").append(parentNode.lineNumber)
		}
		if (parentNode.columnNumber != null) {
			context.append(":C").append(parentNode.columnNumber)
		}
		
		// Add trigger type for context differentiation
		if (triggerInfo.isHardTrigger || GrailsUtils.isDummyPrefix(prefix)) {
			context.append(":trigger")
		} else {
			context.append(":typing")
		}
		
		return context.toString()
	}
	
	private List<CompletionItem> generateCompletions(CompletionContextInfo context, String prefix, Position position) {
		CompletionRequest request = new CompletionRequest(
				context.offsetNode, context.parentNode, prefix, position,
				[], new LinkedHashSet<String>(), context.textFile,
				service.project.isGrailsProject, service
		)
		
		CompletionBuilder.buildCompletions(request)
		return request.items
	}
	
	// --- Cache Management ---
	
	void clearCaches(String uri = null) {
		if (uri) {
			// Remove all cache entries for this URI
			completionCache.entrySet().removeIf { it.key.startsWith(uri) }
		} else {
			completionCache.clear()
		}
	}
	
	private void cleanupCache() {
		if (completionCache.size() > MAX_CACHE_SIZE) {
			long cutoff = System.currentTimeMillis() - CACHE_TTL_MS
			completionCache.entrySet().removeIf { it.value.timestamp < cutoff }
			
			// If still too large, remove oldest entries
			if (completionCache.size() > MAX_CACHE_SIZE) {
				List<Map.Entry<String, CompletionCache>> entries = completionCache.entrySet()
						.sort { a, b -> a.value.timestamp <=> b.value.timestamp }
				
				int toRemove = completionCache.size() - (MAX_CACHE_SIZE * 0.8) as int
				entries.take(toRemove).each { completionCache.remove(it.key) }
			}
		}
	}
	
	// --- Helper Methods ---
	
	private static Character getLastCharacter(String lineText, int character) {
		if (!lineText || character <= 0 || character > lineText.length()) {
			return '\0' as Character
		}
		if (lineText.contains(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)) {
			int dummyStart = lineText.lastIndexOf(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)
			if (dummyStart >= 0 && dummyStart < lineText.length() - 1) {
				return lineText.charAt(dummyStart - 1)
			}
		}
		return lineText.charAt(character - 1)
	}
	
	private static CompletionTriggerInfo analyzeTrigger(CompletionContext context, Character lastChar, String prefix) {
		boolean isHardTrigger = HARD_TRIGGERS.contains(lastChar as String)
		boolean isSoftTrigger = SOFT_TRIGGERS.contains(lastChar as String)
		boolean isManualTrigger = context?.triggerKind == CompletionTriggerKind.Invoked
		
		// Cache can be used unless it's a hard trigger that changes context
		boolean canUseCache = !isHardTrigger && !isManualTrigger
		
		return new CompletionTriggerInfo(isHardTrigger, isSoftTrigger, isManualTrigger, canUseCache)
	}
	
	// --- Completion Item Resolution ---
	
	CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
		return CompletableFuture.supplyAsync {
			try {
				Map<String, Object> data = unresolved.data as Map<String, Object>
				if (!data || data.isResolved) {
					return unresolved
				}
				
				ASTNode node = extractASTNode(data)
				if (!node) {
					return unresolved
				}
				
				CompletionUtil.enhanceCompletionItemDetails(unresolved, node)
				
				// Attach Documentation
				unresolved.documentation = DocumentationHelper.getDocumentation(node, service, DocumentationType.COMPLETION)
				
				String complexInsertText = CompletionUtil.astNodeToInsertText(node)
				if (complexInsertText) {
					unresolved.insertText = complexInsertText
					unresolved.insertTextFormat = CompletionUtil.astNodeToInsertTextFormat(node)
				}
				
				List<TextEdit> additionalEdits = CompletionUtil.astNodeToAdditionalTextEdits(node)
				if (additionalEdits) {
					unresolved.additionalTextEdits = additionalEdits
				}
				
				// Auto-import support 💡
				if (data?.autoImport) {
					String fqcn = data.fqcn
					String uri = data.uri
					TextFile file = service.fileTracker.getTextFile(uri)
					if (file) {
						ClassNode classNode = service.visitor.getClassNodes(file.uri).find { it.name == fqcn }
						// def imports = new ImportNode(unresolved.detail, unresolved.label)
						ModuleNode moduleNode = GrailsASTHelper.getEnclosingModuleNode(classNode, service.visitor)
						if (moduleNode && !CompletionUtil.hasImport(moduleNode, fqcn)) {
							Range range = CompletionUtil.findAddImportRange(moduleNode)
							TextEdit importEdit = new TextEdit(range, "import ${fqcn}\n")
							unresolved.additionalTextEdits = (unresolved.additionalTextEdits ?: []) + importEdit
						}
					}
				}
				
				data.isResolved = true
				return unresolved
			} catch (Exception e) {
				log.warn("[COMPLETION] Failed to resolve completion item for ${unresolved.label}", e)
				return unresolved
			}
		}
	}
	
	private static ASTNode extractASTNode(Object data) {
		if (data instanceof ASTNode) return data
		if (data instanceof Map) return data.astNode as ASTNode
		return null
	}
	
	// --- Inner Classes ---
	
	private static class CompletionContextInfo {
		final ASTNode offsetNode
		final ASTNode parentNode
		final TextFile textFile
		
		CompletionContextInfo(ASTNode offsetNode, ASTNode parentNode, TextFile textFile) {
			this.offsetNode = offsetNode
			this.parentNode = parentNode
			this.textFile = textFile
		}
	}
	
	private static class CompletionTriggerInfo {
		final boolean isHardTrigger
		final boolean isSoftTrigger
		final boolean isManualTrigger
		final boolean canUseCache
		
		CompletionTriggerInfo(boolean isHardTrigger, boolean isSoftTrigger, boolean isManualTrigger, boolean canUseCache) {
			this.isHardTrigger = isHardTrigger
			this.isSoftTrigger = isSoftTrigger
			this.isManualTrigger = isManualTrigger
			this.canUseCache = canUseCache
		}
	}
	
	private static class CompletionCache {
		final int baseOffset
		final String basePrefix
		final List<CompletionItem> items
		final boolean lastWasTrigger
		final ASTNode offsetNode
		final ASTNode parentNode
		final long timestamp
		
		CompletionCache(int baseOffset, String basePrefix, List<CompletionItem> items,
		                boolean lastWasTrigger, ASTNode offsetNode, ASTNode parentNode, long timestamp) {
			this.baseOffset = baseOffset
			this.basePrefix = basePrefix ?: ""
			this.items = new ArrayList<>(items) // Defensive copy
			this.lastWasTrigger = lastWasTrigger
			this.offsetNode = offsetNode
			this.parentNode = parentNode
			this.timestamp = timestamp
		}
		
		/**
		 * Check if cache is valid for the given parent context and prefix
		 * Since parent node is the completion source, focus validation on that
		 */
		boolean isValidForContext(ASTNode currentParent, String prefix, int offset) {
			if (!prefix) prefix = ""
			
			long age = System.currentTimeMillis() - timestamp
			if (age >= CACHE_TTL_MS) {
				return false
			}
			
			// Parent node must match (same completion source)
			if (this.parentNode != currentParent) {
				return false
			}
			
			// Allow reasonable offset drift for same context
			boolean offsetInRange = Math.abs(this.baseOffset - offset) <= 50
			
			// For dummy prefixes, we can reuse if parent context is same
			if (GrailsUtils.isDummyPrefix(prefix) && GrailsUtils.isDummyPrefix(this.basePrefix)) {
				return offsetInRange
			}
			
			// For real prefixes, allow any extension of cached prefix
			if (!GrailsUtils.isDummyPrefix(prefix) && !GrailsUtils.isDummyPrefix(this.basePrefix)) {
				return offsetInRange && prefix.startsWith(this.basePrefix)
			}
			
			// Mixed cases (dummy -> real or real -> dummy) need fresh computation
			return false
		}
		
		/**
		 * Return filtered and ranked results for a specific prefix
		 */
		Either<List<CompletionItem>, CompletionList> toResult(String prefix, ClassNode currentClass = null) {
			if (!prefix) prefix = ""
			
			// Filter from complete cached set
			List<CompletionItem> processedItems = CompletionProcessor.processCompletions(items, prefix, currentClass)
			
			log.debug("[COMPLETION] Filtered {} items to {} for prefix '{}'",
					items.size(), processedItems.size(), prefix)
			
			// Always return CompletionList with isIncomplete=true when filtering from cache
			boolean isIncomplete = processedItems.size() < items.size() ||
					processedItems.size() >= CompletionProcessor.getMaxResults(prefix)
			
			if (isIncomplete) {
				Either.forRight(new CompletionList(isIncomplete, processedItems))
			}
			return Either.forLeft(items)
		}
	}
}