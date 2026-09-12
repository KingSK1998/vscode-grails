package kingsk.grails.lsp.model.dto

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@CompileStatic
@ToString(includeNames = true)
@EqualsAndHashCode
class ProjectDependencyEdge implements Serializable {
    private static final long serialVersionUID = 1L

    /** Gradle project path, e.g. ":core" or ":plugins:auth" */
    String projectPath

    /** Build root directory of the referenced project (supports composite builds / included builds) */
    File buildRoot

    /** Project directory of the referenced project */
    File projectDirectory

    /** Target project name */
    String projectName

    /** Target project group */
    String group

    /** Target source set if scoped, defaults to null (meaning main / project-level) */
    String sourceSetName

    ProjectDependencyEdge() {}

    ProjectDependencyEdge(
        String projectPath,
        File buildRoot,
        File projectDirectory,
        String projectName = null,
        String group = null,
        String sourceSetName = null
    ) {
        this.projectPath = projectPath
        this.buildRoot = buildRoot
        this.projectDirectory = projectDirectory
        this.projectName = projectName
        this.group = group
        this.sourceSetName = sourceSetName
    }
}

