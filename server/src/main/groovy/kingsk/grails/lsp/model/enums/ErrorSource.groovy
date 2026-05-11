package kingsk.grails.lsp.model.enums

import groovy.transform.CompileStatic

@CompileStatic
enum ErrorSource {
    GRADLE_SERVICE,
    LANGUAGE_SERVER,
    AST_SERVICE,
    FILE_TRACKER,
    CONFIGURATION,
    GENERAL
}
