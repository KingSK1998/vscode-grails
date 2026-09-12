package kingsk.grails.lsp.core.gradle

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.cache.ThreadSafeLruCache

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Collections
import java.util.Enumeration
import java.util.HashSet
import java.util.Map
import java.util.Set
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.jar.JarEntry
import java.util.jar.JarFile

/**
 * Thread-safe cache of immutable artifact facts keyed by content fingerprint.
 * Satisfies INV-DISC-002: separates shared immutable artifact facts from project membership.
 * Two projects or source sets referencing identical bytes share extracted facts,
 * while modified artifacts at the same coordinates invalidate by content fingerprint.
 */
@Slf4j
@CompileStatic
class ArtifactFactCache {
    public static final int EXTRACTOR_VERSION = 1
    private static final int DEFAULT_CAPACITY = 500

    private static final ArtifactFactCache INSTANCE = new ArtifactFactCache(DEFAULT_CAPACITY)

    private final ThreadSafeLruCache<String, ArtifactFacts> lruCache
    private final Map<String, FingerprintCacheEntry> pathFingerprintCache = new ConcurrentHashMap<>()
    private final AtomicLong hitCount = new AtomicLong(0L)
    private final AtomicLong missCount = new AtomicLong(0L)

    static ArtifactFactCache getInstance() {
        return INSTANCE
    }

    ArtifactFactCache(int capacity = DEFAULT_CAPACITY) {
        this.lruCache = new ThreadSafeLruCache<>(capacity, 3600000L, 60000L)
    }

    /**
     * Computes the SHA-256 content fingerprint of a file.
     * Uses length + lastModified cache check to accelerate repeated queries for unchanged files.
     */
    String computeFingerprint(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null

        String path = file.absolutePath
        long lastMod = file.lastModified()
        long len = file.length()

        FingerprintCacheEntry entry = pathFingerprintCache.get(path)
        if (entry != null && entry.lastModified == lastMod && entry.length == len) {
            return entry.sha256
        }

        String sha256 = calculateSha256(file)
        if (sha256 != null) {
            pathFingerprintCache.put(path, new FingerprintCacheEntry(lastMod, len, sha256))
        }
        return sha256
    }

    /**
     * Get or extract facts for a given jar file.
     * If the jar content is unchanged, returns cached facts (reuse).
     * If the jar content changed at the same coordinates, invalidates by content.
     */
    ArtifactFacts getOrExtract(File jarFile, String coordinates = null) {
        if (jarFile == null || !jarFile.exists() || !jarFile.isFile()) return null

        String fingerprint = computeFingerprint(jarFile)
        if (fingerprint == null) return null

        ArtifactFacts cached = lruCache.getIfPresent(fingerprint)
        if (cached != null) {
            hitCount.incrementAndGet()
            return cached
        }

        missCount.incrementAndGet()
        ArtifactFacts extracted = extractFacts(jarFile, fingerprint, coordinates)
        if (extracted != null) {
            lruCache.put(fingerprint, extracted)
        }
        return extracted
    }

    ArtifactFacts getByFingerprint(String fingerprint) {
        if (!fingerprint) return null
        return lruCache.getIfPresent(fingerprint)
    }

    private ArtifactFacts extractFacts(File jarFile, String fingerprint, String coordinates) {
        Set<String> classNames = new HashSet<>()
        Set<String> packageNames = new HashSet<>()

        try {
            new JarFile(jarFile).withCloseable { JarFile jf ->
                Enumeration<JarEntry> entries = jf.entries()
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement()
                    if (!entry.isDirectory() && entry.name.endsWith(".class")) {
                        String name = entry.name
                        String className = name.substring(0, name.length() - 6).replace('/' as char, '.' as char)
                        classNames.add(className)
                        int lastDot = className.lastIndexOf("." as String)
                        if (lastDot > 0) {
                            packageNames.add(className.substring(0, lastDot))
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[ARTIFACT] Failed to extract facts from JAR ${jarFile.name}: ${e.message}")
            return null
        }

        String coords = coordinates ?: jarFile.name
        return new ArtifactFacts(
            fingerprint,
            coords,
            Collections.unmodifiableSet(classNames),
            Collections.unmodifiableSet(packageNames),
            jarFile.length(),
            System.currentTimeMillis()
        )
    }

    private static String calculateSha256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256")
            new FileInputStream(file).withCloseable { FileInputStream fis ->
                byte[] buffer = new byte[8192]
                int read
                while ((read = fis.read(buffer)) > 0) {
                    digest.update(buffer, 0, read)
                }
            }
            byte[] hash = digest.digest()
            StringBuilder sb = new StringBuilder(hash.length * 2)
            for (byte b : hash) {
                sb.append(String.format("%02x", b))
            }
            return sb.toString()
        } catch (Exception e) {
            log.warn("[ARTIFACT] Failed to compute SHA-256 for ${file.name}: ${e.message}")
            return null
        }
    }

    void invalidate(String fingerprint) {
        if (fingerprint) {
            lruCache.remove(fingerprint)
        }
    }

    void clear() {
        lruCache.clear()
        pathFingerprintCache.clear()
        hitCount.set(0L)
        missCount.set(0L)
    }

    int size() {
        return lruCache.size()
    }

    long getHitCount() {
        return hitCount.get()
    }

    long getMissCount() {
        return missCount.get()
    }

    @CompileStatic
    private static class FingerprintCacheEntry {
        final long lastModified
        final long length
        final String sha256

        FingerprintCacheEntry(long lastModified, long length, String sha256) {
            this.lastModified = lastModified
            this.length = length
            this.sha256 = sha256
        }
    }
}

