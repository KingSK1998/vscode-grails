package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import kingsk.grails.lsp.protocol.dto.ProjectDTO
import kingsk.grails.lsp.protocol.dto.ProjectPatchDTO

@CompileStatic
class ProjectDiffUtil {

    static ProjectPatchDTO diff(ProjectDTO oldP, ProjectDTO newP) {
        if (!oldP) return null

        Map<String, Object> changes = [:]

        if (oldP.grailsVersion != newP.grailsVersion)
            changes.put("grailsVersion", newP.grailsVersion)

        if (oldP.groovyVersion != newP.groovyVersion)
            changes.put("groovyVersion", newP.groovyVersion)

        if (oldP.pluginVersion != newP.pluginVersion)
            changes.put("pluginVersion", newP.pluginVersion)

        if (oldP.dependencies != newP.dependencies)
            changes.put("dependencies", newP.dependencies)

        if (oldP.artifact != newP.artifact)
            changes.put("artifactCounts", newP.artifact)

        if (changes.isEmpty()) return null

        ProjectPatchDTO patch = new ProjectPatchDTO()
        patch.projectId = newP.id
        patch.changes = changes

        return patch
    }
}