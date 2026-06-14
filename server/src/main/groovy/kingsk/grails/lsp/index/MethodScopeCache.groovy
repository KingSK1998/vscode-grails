package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.concurrent.ConcurrentHashMap

@CompileStatic
class MethodScopeCache {
    private final Map<String, List<LocalSymbolInfo>> cache = new ConcurrentHashMap<>()
    
    void putLocals(String uri, List<LocalSymbolInfo> locals) {
        cache.put(uri, Collections.unmodifiableList(locals))
    }
    
    void evict(String uri) {
        cache.remove(uri)
    }
    
    LocalSymbolInfo getLocalAt(String uri, Position pos) {
        List<LocalSymbolInfo> locals = cache[uri]
        if (!locals) return null
        
        LocalSymbolInfo bestMatch = null
        for (LocalSymbolInfo local : locals) {
            if (isPositionInRange(pos, local.range)) {
                if (bestMatch == null || isSmallerRange(local.range, bestMatch.range)) {
                    bestMatch = local
                }
            }
        }
        return bestMatch
    }
    
    private static boolean isPositionInRange(Position pos, Range range) {
        if (pos.line < range.start.line || pos.line > range.end.line) return false
        if (pos.line == range.start.line && pos.character < range.start.character) return false
        if (pos.line == range.end.line && pos.character > range.end.character) return false
        return true
    }
    
    private static boolean isSmallerRange(Range r1, Range r2) {
        int l1 = r1.end.line - r1.start.line
        int l2 = r2.end.line - r2.start.line
        if (l1 != l2) return l1 < l2
        return (r1.end.character - r1.start.character) < (r2.end.character - r2.start.character)
    }
}
