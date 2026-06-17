package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile

import java.util.concurrent.ConcurrentHashMap

@Slf4j
@CompileStatic
class WorkspaceManager {
    private final GrailsService grailsService
    private final Map<String, ProjectContextImpl> contexts = new ConcurrentHashMap<>()
    private String activeProjectUri

    WorkspaceManager(GrailsService grailsService) {
        this.grailsService = grailsService
    }

    void addProject(GrailsProject project) {
        if (!project?.rootDirectory) return
        String uri = TextFile.normalizePath(project.rootDirectory.toURI().toString())
        if (!contexts.containsKey(uri)) {
            log.info("[WORKSPACE] Adding project context for: \${uri}")
            contexts.put(uri, new ProjectContextImpl(project, grailsService))
            if (!activeProjectUri) activeProjectUri = uri
        } else {
            contexts.get(uri).updateProject(project, uri)
        }
    }

    void removeProject(String uri) {
        String normalizedUri = TextFile.normalizePath(uri)
        def ctx = contexts.remove(normalizedUri)
        if (ctx) {
            log.info("[WORKSPACE] Removing project context: \${normalizedUri}")
            ctx.hibernate()
        }
        if (activeProjectUri == normalizedUri) {
            activeProjectUri = contexts.keySet().findResult { it }
        }
    }

    ProjectContextImpl getProjectForUri(String uri) {
        if (!uri) return getDefaultProject()
        String normalizedUri = TextFile.normalizePath(uri)
        
        // Find the longest matching root
        def match = contexts.values()
            .findAll { normalizedUri.startsWith(TextFile.normalizePath(it.project.rootDirectory.toURI().toString())) }
            .sort { -it.project.rootDirectory.absolutePath.length() }
            .find { it }
            
        return (ProjectContextImpl) (match ?: getDefaultProject())
    }

    ProjectContextImpl getDefaultProject() {
        return activeProjectUri ? contexts.get(activeProjectUri) : null
    }

    Collection<ProjectContextImpl> getAllContexts() {
        return contexts.values()
    }

    List<GrailsProject> getAllProjects() {
        return contexts.values().collect { it.project }
    }
}
