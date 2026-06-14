package kingsk.grails.lsp.context

import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.protocol.dto.ProjectDTO

interface ProjectContext {
    GrailsProject getProject()
    Map<String, GrailsProject> getProjects()
    String getActiveProjectUri()
    void setActiveProjectUri(String uri)
    ProjectDTO getProjectInfo(String projectDir)
    void notifyAllProjects()
    void addProject(GrailsProject project)
    void removeProject(String projectDir)
    void updateProject(GrailsProject project, String projectDir)
    GrailsProject getProjectForUri(String uri)
}