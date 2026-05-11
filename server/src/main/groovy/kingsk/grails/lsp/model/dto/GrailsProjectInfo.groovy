package kingsk.grails.lsp.model.dto

import kingsk.grails.lsp.model.types.GrailsArtifactInfo

class GrailsProjectInfo {
    String id
    String name
    String rootPath

    String grailsVersion
    String groovyVersion
    String javaVersion
    String gradleVersion

    Map<String, List<GrailsArtifactInfo>> artifacts = [:]
    Map<String, Object> metadata = [:]  // free-form: dependencies, profiles, etc.
}
