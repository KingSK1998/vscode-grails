package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.util.Collections

@CompileStatic
class IndexSnapshot {
    static final IndexSnapshot EMPTY = new IndexSnapshot([:], [:])
    
    // Primary lookup: descriptor → SymbolInfo (cross-file, O(1))
    final Map<String, SymbolInfo> byDescriptor
    
    // Positional lookup: uri → sorted list for binary search by position
    final Map<String, List<SymbolInfo>> byUri
    
    IndexSnapshot(Map<String, SymbolInfo> byDescriptor, Map<String, List<SymbolInfo>> byUri) {
        this.byDescriptor = Collections.unmodifiableMap(byDescriptor)
        this.byUri = Collections.unmodifiableMap(byUri)
    }
    
    SymbolInfo getSymbolAt(String uri, Position pos) {
        List<SymbolInfo> symbols = byUri[uri]
        if (!symbols) return null
        
        SymbolInfo bestMatch = null
        // Iterate through all symbols for the file to find the smallest containing range.
        // File-level symbol lists are small (typically < 100 entries), making O(N) scan extremely fast.
        for (SymbolInfo symbol : symbols) {
            if (isPositionInRange(pos, symbol.range)) {
                if (bestMatch == null || isSmallerRange(symbol.range, bestMatch.range)) {
                    bestMatch = symbol
                }
            }
        }
        return bestMatch
    }
    
    SymbolInfo getSymbolByDescriptor(String descriptor) {
        return byDescriptor[descriptor]
    }
    
    List<SymbolInfo> getSymbolsForFile(String uri) {
        return byUri[uri] ?: []
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
