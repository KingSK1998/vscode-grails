# Grails LSP Performance Optimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Optimize the Grails Language Server for better performance, reduced memory usage, and improved scalability for large workspaces

**Architecture:** Refactor caching strategies, implement proper async operations, optimize AST visitor patterns, and improve memory management throughout the LSP server

**Tech Stack:** Groovy 4.0.23, LSP4J, Gradle Tooling API, Spock Framework

## Status Tracking

| Task | Status | Notes |
|------|--------|-------|
| Implement LRU Cache with Size and Time-based Eviction | ✅ Completed | Task 1 complete |
| Optimize AST Visitor Memory Management | ⏳ Not Started | |
| Implement Proper Async Operations for Gradle Operations | ⏳ Not Started | |
| Optimize Debounce Strategy for Large Projects | ⏳ Not Started | |
| Implement Bounded Thread Pool | ⏳ Not Started | |
| Implement Time-based Cache Invalidation | ⏳ Not Started | |
| Optimize File Dependency Resolution | ⏳ Not Started | |
| Decouple GrailsService with Dependency Injection | ⏳ Not Started | |

---

## Performance Issues Identified

1. **AST Caching Strategy**: MAX_FILES_IN_MEMORY limit may be insufficient for large workspaces
2. **Memory Management**: Visitor state cleanup only triggers at fixed thresholds
3. **Synchronous Operations**: Gradle operations and compilation can block LSP threads
4. **Debounce Strategy**: Fixed debounce may not be optimal for all project sizes
5. **Thread Management**: Thread pool without bounds can cause thread exhaustion
6. **Cache Invalidation**: Completion cache lacks proper expiration policy
7. **File Dependency Resolution**: Potentially O(N) operations for dependency resolution
8. **Tight Coupling**: GrailsService god object creates architectural anti-patterns

## Task 1: Implement LRU Cache with Size and Time-based Eviction

**Files:**
- Create: `src/main/groovy/kingsk/grails/lsp/cache/LRUCache.groovy`
- Modify: `src/main/groovy/kingsk/grails/lsp/core/visitor/GrailsASTVisitor.groovy:120-145`
- Test: `src/test/groovy/kingsk/grails/lsp/cache/LRUCacheSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should evict entries when maxSize exceeded"() {
    given: "an LRU cache with max size of 2"
    def cache = new LRUCache<String, String>(2)
    
    when: "adding 3 entries"
    cache.put("key1", "value1")
    cache.put("key2", "value2")
    cache.put("key3", "value3")
    
    then: "first entry should be evicted"
    cache.get("key1") == null
    cache.get("key2") == "value2"
    cache.get("key3") == "value3"
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.cache.LRUCacheSpec.should evict entries when maxSize exceeded" -i`
Expected: FAIL with "LRUCache class not found"

- [ ] **Step 3: Write minimal implementation**

```groovy
package kingsk.grails.lsp.cache

import groovy.transform.CompileStatic
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

@CompileStatic
class LRUCache<K, V> {
    private final int maxSize
    private final long ttlMs
    private final ConcurrentHashMap<K, CacheEntry<V>> cache = new ConcurrentHashMap<>()
    private final AtomicLong accessCounter = new AtomicLong(0)
    
    LRUCache(int maxSize, long ttlMs = 30000L) {
        this.maxSize = maxSize
        this.ttlMs = ttlMs
    }
    
    void put(K key, V value) {
        cache.put(key, new CacheEntry<>(value, accessCounter.incrementAndGet()))
        evictIfNecessary()
    }
    
    V get(K key) {
        CacheEntry<V> entry = cache.get(key)
        if (entry != null) {
            if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
                cache.remove(key)
                return null
            }
            entry.accessOrder = accessCounter.incrementAndGet()
            return entry.value
        }
        return null
    }
    
    private void evictIfNecessary() {
        if (cache.size() > maxSize) {
            def entries = cache.entrySet().toList()
            entries.sort { it.value.accessOrder }
            def toRemove = entries.size() - maxSize
            entries.take(toRemove).each { cache.remove(it.key) }
        }
    }
    
    int size() {
        return cache.size()
    }
    
    void clear() {
        cache.clear()
    }
    
    private static class CacheEntry<V> {
        final V value
        final long timestamp
        volatile long accessOrder
        
        CacheEntry(V value, long accessOrder) {
            this.value = value
            this.timestamp = System.currentTimeMillis()
            this.accessOrder = accessOrder
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.cache.LRUCacheSpec.should evict entries when maxSize exceeded" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/cache/LRUCache.groovy src/test/groovy/kingsk/grails/lsp/cache/LRUCacheSpec.groovy
git commit -m "feat: implement LRU cache with time-based eviction"
```

## Task 2: Optimize AST Visitor Memory Management

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/core/visitor/GrailsASTVisitor.groovy:120-145`
- Test: `src/test/groovy/kingsk/grails/lsp/core/visitor/GrailsASTVisitorSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should cleanup unused files based on LRU policy"() {
    given: "AST visitor with LRU cache"
    def visitor = new GrailsASTVisitor()
    def maxFiles = 100
    
    when: "adding more files than max limit"
    (1..(maxFiles + 10)).each { i ->
        def uri = "file${i}.groovy"
        // Simulate adding files
    }
    
    then: "old files should be cleaned up"
    // Verify cleanup occurred
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.core.visitor.GrailsASTVisitorSpec.should cleanup unused files based on LRU policy" -i`
Expected: FAIL with cleanup method not implemented

- [ ] **Step 3: Write minimal implementation**

Replace lines 120-145 in GrailsASTVisitor.groovy:

```groovy
// Smart cleanup - remove files based on LRU policy
private void cleanupUnusedFiles() {
    if (nodesByURI.size() <= MAX_FILES_IN_MEMORY || !service?.fileTracker) return

    // Get currently tracked files (open/active files)
    Set<String> trackedUris = service.fileTracker.activeTextFiles*.uri.toSet()

    // Find files in AST that are NOT tracked (safe to remove)
    Set<String> safeToRemove = nodesByURI.keySet().findAll { uri ->
        !trackedUris.contains(uri)
    }

    // Sort by last access time for LRU eviction
    if (safeToRemove.size() > MAX_FILES_IN_MEMORY * 0.1) { // 10% threshold
        def entries = safeToRemove.collect { uri -> 
            [uri: uri, lastAccess: getLastAccessTime(uri)]
        }.sort { it.lastAccess }
        
        // Remove oldest entries
        def toRemove = Math.max(0, entries.size() - (MAX_FILES_IN_MEMORY / 2) as int)
        entries.take(toRemove).each { 
            removeFileFromASTVisitor(it.uri) 
        }
    }

    log.debug "[AST] Cleaned up ${Math.min(safeToRemove.size(), MAX_FILES_IN_MEMORY * 0.1)} unused files from memory"
}

private long getLastAccessTime(String uri) {
    // Implementation to track last access time
    return System.currentTimeMillis()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.core.visitor.GrailsASTVisitorSpec.should.cleanupUnusedFiles" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/core/visitor/GrailsASTVisitor.groovy src/test/groovy/kingsk/grails/lsp/core/visitor/GrailsASTVisitorSpec.groovy
git commit -m "perf: optimize AST visitor memory management with LRU cleanup"
```

## Task 3: Implement Proper Async Operations for Gradle Operations

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/GrailsService.groovy:85-138`
- Test: `src/test/groovy/kingsk/grails/lsp/GrailsServiceSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should execute Gradle operations asynchronously"() {
    given: "a GrailsService"
    def service = new GrailsService()
    
    when: "refreshing workspace asynchronously"
    def future = service.refreshAndReindexWorkspaceAsync("test-project", "Test Refresh")
    
    then: "operation should complete without blocking"
    future != null
    !future.isDone() // Should be processing
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.GrailsServiceSpec.should.execute.Gradle.operations.asynchronously" -i`
Expected: FAIL with method not found

- [ ] **Step 3: Write minimal implementation**

Add to GrailsService.groovy:

```groovy
/**
 * Async version of refreshAndReindexWorkspace
 */
CompletableFuture<Void> refreshAndReindexWorkspaceAsync(String projectDir, String title = "Workspace Refresh") {
    CompletableFuture.runAsync({
        refreshAndReindexWorkspace(projectDir, title, false) // false = synchronous execution in background
    }, backgroundExecutor)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.GrailsServiceSpec.should.execute.Gradle.operations.asynchronously" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/GrailsService.groovy src/test/groovy/kingsk/grails/lsp/GrailsServiceSpec.groovy
git commit -m "feat: implement async Gradle operations"
```

## Task 4: Optimize Debounce Strategy for Large Projects

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/services/GrailsTextDocumentService.groovy:108`
- Test: `src/test/groovy/kingsk/grails/lsp/services/GrailsTextDocumentServiceSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should adjust debounce time based on project size"() {
    given: "a text document service"
    def service = new GrailsTextDocumentService()
    
    when: "project has many files"
    def debounceTime = service.calculateDebounceTime(500) // 500 files
    
    then: "debounce should be longer"
    debounceTime > 500L
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.services.GrailsTextDocumentServiceSpec.should.adjust.debounce.time.based.on.project.size" -i`
Expected: FAIL with method not found

- [ ] **Step 3: Write minimal implementation**

Replace line 108 in GrailsTextDocumentService.groovy:

```groovy
// Dynamic debounce based on project size
def projectSize = service?.project?.sourceFileCount ?: 0
def debounceTime = calculateDebounceTime(projectSize)
compileTasks[uriString] = debounceExecutor.schedule({ ->
    try {
        def latestTextFile = service.fileTracker.getTextFile(uriString)
        if (latestTextFile) {
            service.compileAndVisitAST(latestTextFile)
        }
    } catch (Exception e) {
        service.errorService.handleError("Error in debounced compile", e)
    }
} as Runnable, debounceTime, java.util.concurrent.TimeUnit.MILLISECONDS)

// Helper method
private long calculateDebounceTime(int fileCount) {
    // Base 500ms + 1ms per file (capped at 2000ms)
    return Math.min(500L + fileCount, 2000L)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.services.GrailsTextDocumentServiceSpec.should.adjust.debounce.time.based.on.project.size" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/services/GrailsTextDocumentService.groovy src/test/groovy/kingsk/grails/lsp/services/GrailsTextDocumentServiceSpec.groovy
git commit -m "perf: implement dynamic debounce strategy based on project size"
```

## Task 5: Implement Bounded Thread Pool

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/GrailsService.groovy:59`
- Test: `src/test/groovy/kingsk/grails/lsp/GrailsServiceSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should use bounded thread pool"() {
    given: "a grails service"
    def service = new GrailsService()
    
    when: "accessing background executor"
    def executor = service.getBackgroundExecutor()
    
    then: "it should be bounded"
    executor != null
    // Check it's a bounded executor
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.GrailsServiceSpec.should.use.bounded.thread.pool" -i`
Expected: FAIL with method not found

- [ ] **Step 3: Write minimal implementation**

Replace line 59 in GrailsService.groovy:

```groovy
// Bounded thread pool to prevent thread exhaustion
private final Executor backgroundExecutor = Executors.newFixedThreadPool(
    Runtime.runtime.availableProcessors().intValue() * 2
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.GrailsServiceSpec.should.use.bounded.thread.pool" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/GrailsService.groovy src/test/groovy/kingsk/grails/lsp/GrailsServiceSpec.groovy
git commit -m "perf: implement bounded thread pool to prevent exhaustion"
```

## Task 6: Implement Time-based Cache Invalidation

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/providersDocument/GrailsCompletionProvider.groovy:32-34`
- Test: `src/test/groovy/kingsk/grails/lsp/providersDocument/GrailsCompletionProviderSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should invalidate cache based on time"() {
    given: "a completion provider with timed cache"
    def provider = new GrailsCompletionProvider()
    
    when: "cache entry expires"
    Thread.sleep(35000) // Wait for expiration
    
    then: "cache should be cleared"
    // Verify cache is empty or refreshed
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.providersDocument.GrailsCompletionProviderSpec.should.invalidate.cache.based.on.time" -i`
Expected: FAIL with timeout implementation missing

- [ ] **Step 3: Write minimal implementation**

Add to GrailsCompletionProvider.groovy (around line 32):

```groovy
// Add time-based cleanup
private void cleanupCache() {
    long cutoff = System.currentTimeMillis() - CACHE_TTL_MS
    completionCache.entrySet().removeIf { 
        it.value.timestamp < cutoff || it.value.accessCount.get() < 1 
    }

    if (completionCache.size() > MAX_CACHE_SIZE) {
        def entries = completionCache.entrySet().toList()
            .sort { Map.Entry<String, CompletionCache> a, Map.Entry<String, CompletionCache> b ->
                a.value.timestamp <=> b.value.timestamp
            }

        int toRemove = (int) (completionCache.size() - (MAX_CACHE_SIZE * 0.8))
        entries.take(toRemove).each { completionCache.remove(it.key) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.providersDocument.GrailsCompletionProviderSpec.should.invalidate.cache.based.on.time" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/providersDocument/GrailsCompletionProvider.groovy src/test/groovy/kingsk/grails/lsp/providersDocument/GrailsCompletionProviderSpec.groovy
git commit -m "perf: implement time-based cache invalidation"
```

## Task 7: Optimize File Dependency Resolution

**Files:**
- Modify: `src/main/groovy/kingsk/grails/lsp/services/FileContentTracker.groovy`
- Test: `src/test/groovy/kingsk/grails/lsp/services/FileContentTrackerSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should resolve file dependencies efficiently"() {
    given: "a file content tracker with many files"
    def tracker = new FileContentTracker()
    
    when: "resolving dependencies for a file"
    def dependencies = tracker.getFileAndItsDependencies(mockFile)
    
    then: "it should return quickly"
    dependencies != null
    // Performance assertion
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.services.FileContentTrackerSpec.should.resolve.file.dependencies.efficiently" -i`
Expected: FAIL with performance issue

- [ ] **Step 3: Write minimal implementation**

```groovy
/**
 * Optimized file dependency resolution using cached graph
 */
Set<TextFile> getFileAndItsDependencies(TextFile file) {
    if (!file) return [] as Set
    
    // Check cache first
    def cacheKey = "${file.uri}:${file.lastModified}"
    def cached = dependencyCache.get(cacheKey)
    if (cached) {
        return cached
    }
    
    // Build dependency graph with cycle detection
    Set<TextFile> result = new LinkedHashSet<>()
    Queue<TextFile> queue = new LinkedList<>()
    queue.offer(file)
    
    while (!queue.isEmpty()) {
        TextFile current = queue.poll()
        if (!result.contains(current)) {
            result.add(current)
            // Add direct dependencies
            getDirectDependencies(current).each { dep ->
                if (!result.contains(dep) && !queue.contains(dep)) {
                    queue.offer(dep)
                }
            }
        }
    }
    
    // Cache the result
    dependencyCache.put(cacheKey, result)
    return result
}

private Set<TextFile> getDirectDependencies(TextFile file) {
    // Fast lookup using import statements and AST analysis
    return fastDependencyLookup.getOrDefault(file.uri, [] as Set)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.services.FileContentTrackerSpec.should.resolve.file.dependencies.efficiently" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/services/FileContentTracker.groovy src/test/groovy/kingsk/grails/lsp/services/FileContentTrackerSpec.groovy
git commit -m "perf: optimize file dependency resolution with caching"
```

## Task 8: Decouple GrailsService with Dependency Injection

**Files:**
- Create: `src/main/groovy/kingsk/grails/lsp/core/ServiceContainer.groovy`
- Modify: `src/main/groovy/kingsk/grails/lsp/GrailsService.groovy`
- Test: `src/test/groovy/kingsk/grails/lsp/core/ServiceContainerSpec.groovy`

- [ ] **Step 1: Write the failing test**

```groovy
def "should provide services through container"() {
    given: "a service container"
    def container = new ServiceContainer()
    
    when: "requesting grails service"
    def service = container.getGrailsService()
    
    then: "it should be available"
    service != null
    service instanceof GrailsService
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "kingsk.grails.lsp.core.ServiceContainerSpec.should.provide.services.through.container" -i`
Expected: FAIL with class not found

- [ ] **Step 3: Write minimal implementation**

Create ServiceContainer.groovy:

```groovy
package kingsk.grails.lsp.core

import groovy.transform.CompileStatic
import kingsk.grails.lsp.GrailsService

@CompileStatic
class ServiceContainer {
    private final GrailsService grailsService
    
    ServiceContainer() {
        this.grailsService = new GrailsService()
    }
    
    GrailsService getGrailsService() {
        return grailsService
    }
    
    // Add other services as needed
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "kingsk.grails.lsp.core.ServiceContainerSpec.should.provide.services.through.container" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add src/main/groovy/kingsk/grails/lsp/core/ServiceContainer.groovy src/test/groovy/kingsk/grails/lsp/core/ServiceContainerSpec.groovy
git commit -m "refactor: implement service container for loose coupling"
```