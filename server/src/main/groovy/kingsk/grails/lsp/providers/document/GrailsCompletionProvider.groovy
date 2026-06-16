package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.context.CompilationContext
import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.context.ProviderContext

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.DocumentationType
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.providers.completions.CompletionBuilder
import kingsk.grails.lsp.providers.completions.CompletionProcessor
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.completion.CompletionUtil
import kingsk.grails.lsp.utils.diagnostics.DocumentationHelper
import kingsk.grails.lsp.utils.grails.GrailsUtils
import kingsk.grails.lsp.utils.position.PositionHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.ModuleNode
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

@Slf4j
@CompileStatic
class GrailsCompletionProvider extends BaseProvider {
    private static final Map<String, CompletionCache> completionCache = [:] as ConcurrentHashMap

    private static final Set<Character> HARD_TRIGGERS = ['.' as char, ':' as char, '(' as char, '[' as char] as Set<Character>
    private static final Set<Character> SOFT_TRIGGERS = ['\t' as char, '\n' as char, ' ' as char] as Set<Character>
    private static final int MAX_CACHE_SIZE = 100
    private static final long CACHE_TTL_MS = 30_000L
    private static final long CLEANUP_INTERVAL_MS = 10_000L

    private static final ScheduledExecutorService cleanupExecutor = Executors.newScheduledThreadPool(1)

    static {
        cleanupExecutor.scheduleAtFixedRate({
            try {
                cleanupExpiredEntries()
            } catch (Exception e) {
                log.warn("[COMPLETION] Scheduled cleanup failed", e)
            }
        }, CLEANUP_INTERVAL_MS, CLEANUP_INTERVAL_MS, TimeUnit.MILLISECONDS)
    }

GrailsCompletionProvider(ProviderContext providerContext, CompilationContext compilationContext, ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    /** Entry point for textDocument/completion */
    CompletableFuture<Either<List<CompletionItem>, CompletionList>> provideCompletions(
        TextDocumentIdentifier textDocument,
        Position position,
        CompletionContext context
    ) {
        CompletableFuture.supplyAsync {
            try {
                log.info("[COMPLETION] Providing Completions for: ${textDocument.uri}:${position.line}:${position.character}")
                def textFile = fileTracker.getTextFile(textDocument.uri)
                doProvideCompletions(textFile, position, context)
            } catch (CancellationException e) {
                throw e
            } catch (Exception e) {
                log.error("[COMPLETION] Completion error for ${textDocument.uri}:${position.line}:${position.character}", e)
                Either.forLeft([])
            }
        } as CompletableFuture<Either<List<CompletionItem>, CompletionList>>
    }

    private Either<List<CompletionItem>, CompletionList> doProvideCompletions(
        TextFile file, Position position, CompletionContext context
    ) {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException('completion interrupted')
        }
        if (!file?.text) {
            return Either.forLeft([])
        }

        def patchedFile = compiler.getPatchedSourceUnitTextFile(file)
        if (file.text.length() != patchedFile.text.length()) {
            file = patchedFile
        }

        String lineText = file.textAtLine(position.line)
        String prefix = CompletionUtil.extractPrefixFromLine(lineText, position.character)
        int offset = PositionHelper.getOffset(file.text, position)
        Character lastChar = getLastCharacter(lineText, position.character)

        // Get AST nodes for context
        ASTNode offsetNode = visitor.getNodeAtPosition(file.uri, position)
        ASTNode parentNode = visitor.getParent(offsetNode)

        if (!offsetNode) {
            return Either.forLeft([])
        }

        def triggerInfo = analyzeTrigger(context, lastChar, prefix)

        // Generate context-aware cache key
        String cacheKey = generateContextCacheKey(file.uri, offsetNode, parentNode, prefix, triggerInfo)

        // Check cache - can use for both dummy and real prefixes since parent is stable
        if (triggerInfo.canUseCache) {
            def cached = completionCache[cacheKey]
            if (cached?.isValidForContext(parentNode, prefix, offset)) {
                log.info("[COMPLETION] Using cached completions for parent '{}' with prefix '{}'",
                    parentNode.getClass().simpleName, prefix)
                def currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, visitor)
                return cached.toResult(prefix, currentClass)
            }
        }

        // Generate completions
        def contextInfo = new CompletionContextInfo(offsetNode, parentNode, file)
        List<CompletionItem> items = generateCompletions(contextInfo, prefix, position)

        // Cache the complete result set
        def newCache = new CompletionCache(
            offset, prefix, items, triggerInfo.isHardTrigger,
            offsetNode, parentNode, System.currentTimeMillis()
        )
        completionCache[cacheKey] = newCache
        cleanupCache()

        // Return filtered results
        def currentClass = GrailsASTHelper.getEnclosingClassNode(offsetNode, visitor)
        newCache.toResult(prefix, currentClass)
    }

    /**
     * Generate context-aware cache key for smart prefix system
     */
    private static String generateContextCacheKey(String uri, ASTNode offsetNode, ASTNode parentNode,
                                                   String prefix, CompletionTriggerInfo triggerInfo) {
        def key = new StringBuilder(uri)

        // Base context from parent node (the actual completion source)
        String baseContext = extractBaseContext(parentNode, prefix, triggerInfo)
        key.append(":").append(baseContext)

        // Add offset node type for additional context (unless it's dummy)
        if (offsetNode && !GrailsUtils.isDummyPrefix(prefix)) {
            key.append(":offset:").append(offsetNode.getClass().simpleName)
        }

        key.toString()
    }

    /**
     * Extract the base context for completion caching
     */
    private static String extractBaseContext(ASTNode parentNode, String prefix, CompletionTriggerInfo triggerInfo) {
        if (!parentNode) return ""

        def context = new StringBuilder()
        context.append(parentNode.getClass().simpleName)

        if (parentNode.lineNumber != -1) {
            context.append(":L").append(parentNode.lineNumber)
        }
        if (parentNode.columnNumber != -1) {
            context.append(":C").append(parentNode.columnNumber)
        }

        if (triggerInfo.isHardTrigger || GrailsUtils.isDummyPrefix(prefix)) {
            context.append(":trigger")
        } else {
            context.append(":typing")
        }

        context.toString()
    }

    private List<CompletionItem> generateCompletions(CompletionContextInfo context, String prefix, Position position) {
            def request = new CompletionRequest(
                context.offsetNode, context.parentNode, prefix, position,
                [], [] as Set<String>, context.textFile,
                project.isGrailsProject, providerContext, compilationContext, projectContext
            )

        CompletionBuilder.buildCompletions(request)
        request.items
    }

    // --- Cache Management ---

    void clearCaches(String uri = null) {
        if (uri) {
            completionCache.entrySet().removeIf { it.key.startsWith(uri) }
        } else {
            completionCache.clear()
        }
    }

    private static void cleanupExpiredEntries() {
        long cutoff = System.currentTimeMillis() - CACHE_TTL_MS
        int removed = 0
        completionCache.entrySet().removeIf { it.value.timestamp < cutoff ? ++removed >= 0 : false }
        if (removed > 0) {
            log.debug("[COMPLETION] Cleaned up {} expired cache entries", removed)
        }
        if (completionCache.size() > MAX_CACHE_SIZE) {
            sizeBasedCleanup()
        }
    }

    private static void sizeBasedCleanup() {
        def entries = completionCache.entrySet().toList()
            .sort { Map.Entry<String, CompletionCache> a, Map.Entry<String, CompletionCache> b ->
                a.value.timestamp <=> b.value.timestamp
            }
        int toRemove = (int) (completionCache.size() - (MAX_CACHE_SIZE * 0.8))
        entries.take(toRemove).each { completionCache.remove(it.key) }
        log.debug("[COMPLETION] Size-based cleanup removed {} entries, current size: {}", toRemove, completionCache.size())
    }

    private void cleanupCache() {
        if (completionCache.size() > MAX_CACHE_SIZE) {
            sizeBasedCleanup()
        }
    }

    // --- Helper Methods ---

    private static Character getLastCharacter(String lineText, int character) {
        if (!lineText || character <= 0 || character > lineText.length()) {
            return (char) '\0'
        }
        if (lineText.contains(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)) {
            int dummyStart = lineText.lastIndexOf(GrailsUtils.DUMMY_COMPLETION_IDENTIFIER)
            if (dummyStart >= 0 && dummyStart < lineText.length() - 1) {
                return lineText.charAt(dummyStart - 1)
            }
        }
        lineText.charAt(character - 1)
    }

    private static CompletionTriggerInfo analyzeTrigger(CompletionContext context, Character lastChar, String prefix) {
        boolean isHardTrigger = HARD_TRIGGERS.contains(lastChar)
        boolean isSoftTrigger = SOFT_TRIGGERS.contains(lastChar)
        boolean isManualTrigger = context?.triggerKind == CompletionTriggerKind.Invoked

        boolean canUseCache = !isHardTrigger && !isManualTrigger

        new CompletionTriggerInfo(isHardTrigger, isSoftTrigger, isManualTrigger, canUseCache)
    }

    // --- Completion Item Resolution ---

    CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
        CompletableFuture.supplyAsync {
            try {
                def data = unresolved.data as Map<String, Object>
                if (!data || data.isResolved) {
                    return unresolved
                }

                def node = extractASTNode(data)
                if (!node) {
                    return unresolved
                }

                CompletionUtil.enhanceCompletionItemDetails(unresolved, node)
                unresolved.documentation = DocumentationHelper.getDocumentation(
                    node, project.isGrailsProject, visitor, DocumentationType.COMPLETION
                )

                String complexInsertText = CompletionUtil.astNodeToInsertText(node)
                if (complexInsertText) {
                    unresolved.insertText = complexInsertText
                    unresolved.insertTextFormat = CompletionUtil.astNodeToInsertTextFormat(node)
                }

                List<TextEdit> additionalEdits = CompletionUtil.astNodeToAdditionalTextEdits(node)
                if (additionalEdits) {
                    unresolved.additionalTextEdits = additionalEdits
                }

                // Auto-import support
                if (data?.autoImport) {
                    String fqcn = (String) data.fqcn
                    String uri = (String) data.uri
                    def file = fileTracker.getTextFile(uri)
                    if (file) {
                        def moduleNode = visitor.getNodes(file.uri).find { it instanceof ModuleNode } as ModuleNode
                        if (moduleNode && !CompletionUtil.hasImport(moduleNode, fqcn)) {
                            def range = CompletionUtil.findAddImportRange(moduleNode)
                            def importEdit = new TextEdit(range, "import ${fqcn}\n")
                            unresolved.additionalTextEdits = (unresolved.additionalTextEdits ?: []) + importEdit
                        }
                    }
                }

                data.isResolved = true
                unresolved
            } catch (Exception e) {
                log.warn("[COMPLETION] Failed to resolve completion item for ${unresolved.label}", e)
                unresolved
            }
        }
    }

    private static ASTNode extractASTNode(Object data) {
        if (data instanceof ASTNode) return (ASTNode) data
        if (data instanceof Map) return (ASTNode) ((Map) data).astNode
        null
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
            this.items = new ArrayList<>(items)
            this.lastWasTrigger = lastWasTrigger
            this.offsetNode = offsetNode
            this.parentNode = parentNode
            this.timestamp = timestamp
        }

        boolean isValidForContext(ASTNode currentParent, String prefix, int offset) {
            if (!prefix) prefix = ""

            long age = System.currentTimeMillis() - timestamp
            if (age >= CACHE_TTL_MS) {
                return false
            }

            if (this.parentNode != currentParent) {
                return false
            }

            boolean offsetInRange = Math.abs(this.baseOffset - offset) <= 50

            if (GrailsUtils.isDummyPrefix(prefix) && GrailsUtils.isDummyPrefix(this.basePrefix)) {
                return offsetInRange
            }

            if (!GrailsUtils.isDummyPrefix(prefix) && !GrailsUtils.isDummyPrefix(this.basePrefix)) {
                return offsetInRange && prefix.startsWith(this.basePrefix)
            }

            false
        }

        Either<List<CompletionItem>, CompletionList> toResult(String prefix, ClassNode currentClass = null) {
            if (!prefix) prefix = ""

            List<CompletionItem> processedItems = CompletionProcessor.processCompletions(items, prefix, currentClass)

            log.debug("[COMPLETION] Filtered {} items to {} for prefix '{}'",
                items.size(), processedItems.size(), prefix)

            boolean isIncomplete = processedItems.size() < items.size() ||
                processedItems.size() >= CompletionProcessor.getMaxResults(prefix)

            isIncomplete ? Either.forRight(new CompletionList(isIncomplete, processedItems)) : Either.forLeft(processedItems)
        }
    }
}