package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.CancellationException

@Slf4j
@CompileStatic
class CancellationService {
    private final Map<String, Set<CancellationToken>> activeRequests = new ConcurrentHashMap<>()
    private final AtomicLong requestIdCounter = new AtomicLong(0)

    /**
     * Creates a new cancellation token for a request.
     * @param uri The document URI this request is for
     * @return A new CancellationToken that can be checked and cancelled
     */
    CancellationToken createToken(String uri) {
        if (!uri) return CancellationToken.none
        String requestId = "${uri}:${requestIdCounter.incrementAndGet()}"
        CancellationToken token = new CancellationToken(requestId, uri)
        Set<CancellationToken> tokenSet = activeRequests.computeIfAbsent(uri) {
            Collections.synchronizedSet(new HashSet<CancellationToken>())
        }
        synchronized (tokenSet) {
            tokenSet.add(token)
        }
        log.debug("[CANCEL] Created token {} for URI: {}", requestId, uri)
        return token
    }

    /**
     * Registers a token for an existing request ID
     */
    void register(String requestId, String uri, CancellationToken token) {
        if (!uri || !token) return
        Set<CancellationToken> tokenSet = activeRequests.computeIfAbsent(uri) {
            Collections.synchronizedSet(new HashSet<CancellationToken>())
        }
        synchronized (tokenSet) {
            tokenSet.add(token)
        }
        log.debug("[CANCEL] Registered token {} for URI: {}", requestId, uri)
    }

    /**
     * Cancels all active requests for a given document URI.
     * Called when a document is closed.
     * @param uri The document URI to cancel requests for
     * @return Number of requests cancelled
     */
    int cancelForUri(String uri) {
        if (!uri) return 0
        Set<CancellationToken> tokens = activeRequests.remove(uri)
        if (tokens) {
            int count = 0
            for (CancellationToken token : tokens) {
                token.cancel()
                count++
            }
            log.info("[CANCEL] Cancelled {} requests for URI: {}", count, uri)
            return count
        }
        return 0
    }

    /**
     * Cancels all active requests for all documents.
     * Called during server shutdown.
     * @return Number of requests cancelled
     */
    int cancelAll() {
        int totalCancelled = 0
        for (Map.Entry<String, Set<CancellationToken>> entry : activeRequests.entrySet()) {
            for (CancellationToken token : entry.getValue()) {
                token.cancel()
                totalCancelled++
            }
        }
        activeRequests.clear()
        if (totalCancelled > 0) {
            log.info("[CANCEL] Cancelled {} total requests", totalCancelled)
        }
        return totalCancelled
    }

    /**
     * Gets the number of active requests for a URI.
     */
    int getActiveRequestCount(String uri) {
        Set<CancellationToken> tokens = activeRequests.get(uri)
        return tokens ? tokens.size() : 0
    }

    /**
     * Gets total active request count.
     */
    int getTotalActiveRequestCount() {
        int total = 0
        for (Set<CancellationToken> tokens : activeRequests.values()) {
            total += tokens.size()
        }
        return total
    }

    /**
     * Cleanup a completed token (call after request completes).
     */
    void cleanup(CancellationToken token) {
        if (!token || !token.uri) return
        Set<CancellationToken> tokens = activeRequests.get(token.uri)
        if (tokens) {
            tokens.remove(token)
            if (tokens.isEmpty()) {
                activeRequests.remove(token.uri)
            }
        }
    }

    Map<String, Object> getStats() {
        [
            activeUris: activeRequests.size(),
            totalRequests: getTotalActiveRequestCount()
        ]
    }

    static class CancellationToken {
        private final String requestId
        private final String uri
        private volatile boolean cancelled = false

        CancellationToken(String requestId, String uri) {
            this.requestId = requestId
            this.uri = uri
        }

        String getRequestId() { requestId }
        String getUri() { uri }
        boolean isCancelled() { cancelled }

        void cancel() {
            cancelled = true
        }

        void checkCancellation() {
            if (cancelled) {
                throw new CancellationException("Request ${requestId} was cancelled")
            }
        }

        static final CancellationToken none = new CancellationToken("none", null) {
            @Override
            void cancel() {}

            @Override
            void checkCancellation() {}
        }
    }
}