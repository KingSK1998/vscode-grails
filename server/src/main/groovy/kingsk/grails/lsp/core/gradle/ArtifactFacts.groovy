package kingsk.grails.lsp.core.gradle

import groovy.transform.CompileStatic

import java.io.Serializable
import java.util.Collections
import java.util.Set

/**
 * Immutable facts extracted from a library artifact (JAR).
 * Facts are keyed by artifact content fingerprint (SHA-256) per INV-DISC-002.
 */
@CompileStatic
class ArtifactFacts implements Serializable {
    private static final long serialVersionUID = 1L

    final String fingerprint
    final String coordinates
    final Set<String> classNames
    final Set<String> packageNames
    final long fileSize
    final long extractedAt

    ArtifactFacts(String fingerprint, String coordinates, Set<String> classNames, Set<String> packageNames, long fileSize, long extractedAt) {
        this.fingerprint = fingerprint
        this.coordinates = coordinates
        this.classNames = Collections.unmodifiableSet(classNames ?: Collections.emptySet() as Set<String>)
        this.packageNames = Collections.unmodifiableSet(packageNames ?: Collections.emptySet() as Set<String>)
        this.fileSize = fileSize
        this.extractedAt = extractedAt
    }

    boolean containsClass(String className) {
        return classNames.contains(className)
    }

    boolean containsPackage(String packageName) {
        return packageNames.contains(packageName)
    }
}

