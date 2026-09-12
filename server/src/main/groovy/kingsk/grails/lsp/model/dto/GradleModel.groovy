package kingsk.grails.lsp.model.dto

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@CompileStatic
@Immutable
class GradleModel implements Serializable {
    private static final long serialVersionUID = 2L

    String name
    String group
    String version
    String grailsVersion
    String groovyVersion
    boolean isGrailsProject
    File rootDirectory
    Set<File> sourceDirectories
    Set<File> testDirectories
    Set<DependencyNode> dependencies
    Map<String, SourceSetModel> sourceSets
}
