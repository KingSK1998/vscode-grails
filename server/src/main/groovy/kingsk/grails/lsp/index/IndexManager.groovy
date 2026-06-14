package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.codehaus.groovy.ast.ClassNode

/**
 * Service-layer orchestrator that coordinates updates to the ProjectIndex,
 * MethodScopeCache, and GroovydocCache in a thread-safe manner using CAS retry loops.
 */
@Slf4j
@CompileStatic
class IndexManager {
    private final ProjectIndex projectIndex
    private final MethodScopeCache methodScopeCache
    private final GroovydocCache groovydocCache

    IndexManager(ProjectIndex projectIndex, MethodScopeCache methodScopeCache, GroovydocCache groovydocCache) {
        this.projectIndex = projectIndex
        this.methodScopeCache = methodScopeCache
        this.groovydocCache = groovydocCache
    }

    /**
     * Rebuild index symbols and locals for a single file.
     */
    void rebuildFile(String uri, List<ClassNode> classNodes) {
        if (!uri) return

        List<SymbolInfo> newSymbols = IndexBuilder.buildSymbols(uri, classNodes)
        List<LocalSymbolInfo> newLocals = IndexBuilder.buildLocals(uri, classNodes)

        while (true) {
            IndexSnapshot expected = projectIndex.getSnapshot()
            Map<String, SymbolInfo> newByDescriptor = new HashMap<>(expected.byDescriptor)
            Map<String, List<SymbolInfo>> newByUri = new HashMap<>(expected.byUri)

            // Evict old descriptors for this URI
            Iterator<Map.Entry<String, SymbolInfo>> it = newByDescriptor.entrySet().iterator()
            while (it.hasNext()) {
                if (it.next().value.fileUri == uri) {
                    it.remove()
                }
            }

            // Insert new symbols
            for (SymbolInfo sym : newSymbols) {
                newByDescriptor.put(sym.descriptor, sym)
            }

            // Update positional map
            if (newSymbols.isEmpty()) {
                newByUri.remove(uri)
            } else {
                newByUri.put(uri, newSymbols)
            }

            IndexSnapshot next = new IndexSnapshot(newByDescriptor, newByUri)
            if (projectIndex.compareAndCommit(expected, next)) {
                break
            }
        }

        // Evict and update local cache and docs after successful index commit
        methodScopeCache.evict(uri)
        if (!newLocals.isEmpty()) {
            methodScopeCache.putLocals(uri, newLocals)
        }
        groovydocCache.evictFile(uri)
    }

    /**
     * Rebuild index symbols and locals for a bulk collection of files.
     */
    void rebuildAll(Map<String, List<ClassNode>> allNodes) {
        while (true) {
            IndexSnapshot expected = projectIndex.getSnapshot()
            Map<String, SymbolInfo> newByDescriptor = new HashMap<>(expected.byDescriptor)
            Map<String, List<SymbolInfo>> newByUri = new HashMap<>(expected.byUri)
            Map<String, List<LocalSymbolInfo>> allLocals = new HashMap<>()

            for (Map.Entry<String, List<ClassNode>> entry : allNodes.entrySet()) {
                String uri = entry.key
                List<ClassNode> classNodes = entry.value

                List<SymbolInfo> newSymbols = IndexBuilder.buildSymbols(uri, classNodes)
                List<LocalSymbolInfo> newLocals = IndexBuilder.buildLocals(uri, classNodes)
                allLocals.put(uri, newLocals)

                // Evict old descriptors for this URI
                Iterator<Map.Entry<String, SymbolInfo>> it = newByDescriptor.entrySet().iterator()
                while (it.hasNext()) {
                    if (it.next().value.fileUri == uri) {
                        it.remove()
                    }
                }

                // Insert new symbols
                for (SymbolInfo sym : newSymbols) {
                    newByDescriptor.put(sym.descriptor, sym)
                }

                // Update positional map
                if (newSymbols.isEmpty()) {
                    newByUri.remove(uri)
                } else {
                    newByUri.put(uri, newSymbols)
                }
            }

            IndexSnapshot next = new IndexSnapshot(newByDescriptor, newByUri)
            if (projectIndex.compareAndCommit(expected, next)) {
                // Apply local cache and doc evictions for all modified files
                for (Map.Entry<String, List<LocalSymbolInfo>> entry : allLocals.entrySet()) {
                    String uri = entry.key
                    List<LocalSymbolInfo> newLocals = entry.value

                    methodScopeCache.evict(uri)
                    if (!newLocals.isEmpty()) {
                        methodScopeCache.putLocals(uri, newLocals)
                    }
                    groovydocCache.evictFile(uri)
                }
                break
            }
        }
    }

    /**
     * Evict a file completely from the index and other caches.
     */
    void evictFile(String uri) {
        if (!uri) return

        while (true) {
            IndexSnapshot expected = projectIndex.getSnapshot()
            Map<String, SymbolInfo> newByDescriptor = new HashMap<>(expected.byDescriptor)
            Map<String, List<SymbolInfo>> newByUri = new HashMap<>(expected.byUri)

            // Remove descriptors
            Iterator<Map.Entry<String, SymbolInfo>> it = newByDescriptor.entrySet().iterator()
            while (it.hasNext()) {
                if (it.next().value.fileUri == uri) {
                    it.remove()
                }
            }

            newByUri.remove(uri)

            IndexSnapshot next = new IndexSnapshot(newByDescriptor, newByUri)
            if (projectIndex.compareAndCommit(expected, next)) {
                break
            }
        }

        methodScopeCache.evict(uri)
        groovydocCache.evictFile(uri)
    }
}
