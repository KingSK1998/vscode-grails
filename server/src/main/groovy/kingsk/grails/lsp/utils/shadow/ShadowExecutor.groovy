package kingsk.grails.lsp.utils.shadow

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.function.BiConsumer
import java.util.function.Supplier

@Slf4j
@CompileStatic
class ShadowExecutor {
    
    // Dedicated pool so challengers never block LSP completion/response threads
    private static final ExecutorService shadowPool = 
        Executors.newFixedThreadPool(2, { Runnable r -> 
            Thread t = new Thread(r, "shadow-challenger")
            t.daemon = true
            return t
        } as ThreadFactory)
        
    static void shutdown() {
        shadowPool.shutdownNow()
    }

    static <T> CompletableFuture<T> shadow(
            String requestName,
            Supplier<CompletableFuture<T>> legacyPath,
            Supplier<CompletableFuture<T>> challengerPath,
            BiConsumer<T, T> comparator) {
        
        CompletableFuture<T> legacy = legacyPath.get()
        
        // chain futures instead of blocking thread with .join()
        legacy.thenAcceptAsync({ T legacyResult ->
            challengerPath.get()
                .thenAccept({ T challengerResult ->
                    try { 
                        comparator.accept(legacyResult, challengerResult) 
                    } catch (Exception e) { 
                        log.error("[SHADOW] Comparator failed for ${requestName}", e) 
                    }
                })
                .exceptionally({ Throwable e -> 
                    log.error("[SHADOW] Challenger crashed for ${requestName}", e)
                    return null 
                })
        }, shadowPool)
        
        return legacy
    }
}
