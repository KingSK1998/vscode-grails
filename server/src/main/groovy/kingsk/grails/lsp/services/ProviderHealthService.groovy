package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@Slf4j
@CompileStatic
class ProviderHealthService {
    private final Map<String, ProviderMetrics> providerMetrics = new ConcurrentHashMap<>()

    ProviderMetrics getMetrics(String providerName) {
        providerMetrics.computeIfAbsent(providerName) { new ProviderMetrics(it) }
    }

    void recordRequest(String providerName, long durationMs, boolean success) {
        ProviderMetrics metrics = getMetrics(providerName)
        metrics.requestCount.incrementAndGet()
        metrics.totalDurationMs.addAndGet(durationMs)
        if (!success) {
            metrics.errorCount.incrementAndGet()
        }
        if (durationMs > metrics.maxLatencyMs.get()) {
            metrics.maxLatencyMs.set(durationMs)
        }
        long min = metrics.minLatencyMs.get()
        if (min == 0 || durationMs < min) {
            metrics.minLatencyMs.set(durationMs)
        }
    }

    void recordError(String providerName) {
        getMetrics(providerName).errorCount.incrementAndGet()
    }

    Map<String, ProviderHealthStatus> getHealthStatus() {
        Map<String, ProviderHealthStatus> status = [:]
        for (Map.Entry<String, ProviderMetrics> entry : providerMetrics.entrySet()) {
            ProviderMetrics m = entry.getValue()
            long count = m.requestCount.get()
            double avgLatency = count > 0 ? (double) m.totalDurationMs.get() / count : 0
            double errorRate = count > 0 ? (double) m.errorCount.get() / count * 100 : 0
            String health = errorRate > 10 ? "UNHEALTHY" : errorRate > 5 ? "DEGRADED" : "HEALTHY"
            status[entry.key] = new ProviderHealthStatus(
                entry.key, count, avgLatency, m.minLatencyMs.get(), m.maxLatencyMs.get(),
                m.errorCount.get(), errorRate, health
            )
        }
        status
    }

    void reset() {
        providerMetrics.clear()
    }

    static class ProviderMetrics {
        final String providerName
        final AtomicLong requestCount = new AtomicLong(0)
        final AtomicLong errorCount = new AtomicLong(0)
        final AtomicLong totalDurationMs = new AtomicLong(0)
        final AtomicLong minLatencyMs = new AtomicLong(0)
        final AtomicLong maxLatencyMs = new AtomicLong(0)

        ProviderMetrics(String providerName) {
            this.providerName = providerName
        }
    }

    static class ProviderHealthStatus {
        final String providerName
        final long requestCount
        final double avgLatencyMs
        final long minLatencyMs
        final long maxLatencyMs
        final long errorCount
        final double errorRate
        final String health

        ProviderHealthStatus(String providerName, long requestCount, double avgLatencyMs,
                            long minLatencyMs, long maxLatencyMs, long errorCount,
                            double errorRate, String health) {
            this.providerName = providerName
            this.requestCount = requestCount
            this.avgLatencyMs = avgLatencyMs
            this.minLatencyMs = minLatencyMs
            this.maxLatencyMs = maxLatencyMs
            this.errorCount = errorCount
            this.errorRate = errorRate
            this.health = health
        }
    }
}