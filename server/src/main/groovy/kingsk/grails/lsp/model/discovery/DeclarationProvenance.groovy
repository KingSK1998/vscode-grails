package kingsk.grails.lsp.model.discovery

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Tracks the origin, source-set, artifact coordinates, and signature provenance of a resolved declaration.
 * Conforms to R2-04 and docs/specs/library-discovery.md.
 */
@Immutable
@CompileStatic
class DeclarationProvenance {
    String sourceSetName          // e.g. "main", "test"
    String artifactCoordinates    // e.g. "org.apache.groovy:groovy:4.0.23" or "project::my-app"
    String declaringClass         // FQCN e.g. "com.example.Person" or "org.codehaus.groovy.runtime.DefaultGroovyMethods"
    String signature              // full signature e.g. "String getName()" or "void execute(String, int)"
    String originKind             // "SOURCE", "BYTECODE", "TRAIT", "EXTENSION_MODULE"
    String sourceAttachment       // file URI / path if source is attached, null if bytecode-only

    boolean hasSourceAttachment() {
        return sourceAttachment != null && !sourceAttachment.isEmpty()
    }
}

