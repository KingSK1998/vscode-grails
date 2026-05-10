package kingsk.grails.lsp.protocol.mapper

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.protocol.dto.ArtifactDTO
import kingsk.grails.lsp.protocol.dto.DependencyDTO
import kingsk.grails.lsp.protocol.dto.ProjectDTO

@CompileStatic
class ProjectMapper {

    static ProjectDTO toDTO(GrailsProject project) {
        if (!project) return null

        ProjectDTO dto = new ProjectDTO()
        dto.id = project.rootDirectory?.absolutePath
        dto.rootPath = project.rootDirectory?.absolutePath
        dto.name = project.name
        dto.type = resolveType(project)

        dto.grailsVersion = project.grailsVersion
        dto.groovyVersion = project.groovyVersion
        dto.javaVersion = project.javaVersion
        dto.pluginVersion = project.pluginVersion

        dto.dependencies = project.dependencies?.collect { toDepDTO(it) } ?: []
        dto.artifact = mapArtifacts(project)

        return dto
    }

    private static DependencyDTO toDepDTO(DependencyNode node) {
        return new DependencyDTO(
            group: node.group,
            name: node.name,
            version: node.version,
            scope: node.scope
        )
    }

    private static String resolveType(GrailsProject project) {
        if (project.isGrailsPlugin) return "grails-plugin"
        if (project.isGrailsProject) return "grails"
        return "groovy"
    }

    private static ArtifactDTO mapArtifacts(GrailsProject project) {
        ArtifactDTO dto = new ArtifactDTO()

        dto.controllers = count(project, "controllers")
        dto.services = count(project, "services")
        dto.domains = count(project, "domain")
        dto.views = count(project, "views")
        dto.taglibs = count(project, "taglib")

        return dto
    }

    private static int count(GrailsProject project, String folder) {
        return project.sourceDirectories?.count {
            it.absolutePath.contains(folder)
        } ?: 0
    }
}
