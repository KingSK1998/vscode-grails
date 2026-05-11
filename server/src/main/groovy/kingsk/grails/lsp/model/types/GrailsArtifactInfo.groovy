package kingsk.grails.lsp.model.types

class GrailsArtifactInfo {
    String name
    String type     // controller | service | domain | view | taglib | interceptor | config | job | utils | i18n | asset | test
    String path     // absolute file path
    String packageName // optional
}
