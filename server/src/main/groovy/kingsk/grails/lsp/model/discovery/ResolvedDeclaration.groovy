package kingsk.grails.lsp.model.discovery

import groovy.transform.CompileStatic
import groovy.transform.Immutable

/**
 * Represents a resolved declaration (method, field, property, or type) along with its provenance.
 * Conforms to R2-04 and docs/specs/library-discovery.md.
 */
@Immutable
@CompileStatic
class ResolvedDeclaration {
    String name                   // simple member or type name
    String kind                   // "METHOD", "FIELD", "PROPERTY", "TYPE"
    String returnType             // return type or property/field type
    List<String> parameterTypes   // parameter types (if method)
    List<String> genericTypes     // generic type arguments (if any)
    boolean isStatic
    DeclarationProvenance provenance
}

