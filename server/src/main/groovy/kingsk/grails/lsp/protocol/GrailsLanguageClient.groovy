package kingsk.grails.lsp.protocol

import kingsk.grails.lsp.protocol.dto.ProjectDTO
import kingsk.grails.lsp.protocol.dto.ProjectPatchDTO
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient

interface GrailsLanguageClient extends LanguageClient {

    @JsonNotification("grails/projectUpdated")
    void projectUpdated(ProjectDTO dto)

    @JsonNotification("grails/allProjects")
    void notifyAllProjects(List<ProjectDTO> dtos)

    @JsonNotification("grails/projectPatched")
    void projectPatched(ProjectPatchDTO patch);

    @JsonNotification("grails/projectsDiscovered")
    void notifyProjectsDiscovered(List<ProjectDTO> projects)

}
