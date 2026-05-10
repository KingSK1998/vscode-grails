package kingsk.grails.lsp.protocol.dto

import groovy.transform.CompileStatic

@CompileStatic
class ProjectDTO implements Serializable {
    String id
    String rootPath
    String name
    String type

    String grailsVersion
    String groovyVersion
    String javaVersion
    String pluginVersion

    List<DependencyDTO> dependencies = []
    // TODO: extend later
    ArtifactDTO artifact
}
