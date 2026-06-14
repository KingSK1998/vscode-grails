import java.nio.file.*
import java.util.regex.*

def baseDir = Paths.get("d:/Grails_Framework_Support_Extension/vscode-gng-support/server/src/main/groovy/kingsk/grails/lsp")

def providersDir = baseDir.resolve("providers")

def updateBaseProvider = { ->
    def bpFile = providersDir.resolve("document/BaseProvider.groovy")
    def content = bpFile.toFile().text
    content = content.replaceAll("import kingsk.grails.lsp.GrailsService\n", "")
    content = content.replaceAll(/private final GrailsProjectGetter _projectGetter\s+private final GrailsService _service\s+BaseProvider\(ProviderContext providerContext, CompilationContext compilationContext, GrailsProjectGetter projectGetter, GrailsService service\) \{\s+this\._providerContext = providerContext\s+this\._compilationContext = compilationContext\s+this\._projectGetter = projectGetter\s+this\._service = service\s+\}\s+BaseProvider\(GrailsService service\) \{\s+this\(service as ProviderContext, service as CompilationContext, \{ service\.project \}, service\)\s+\}/, 
        '''private final kingsk.grails.lsp.context.ProjectContext _projectContext

    BaseProvider(ProviderContext providerContext, CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        this._providerContext = providerContext
        this._compilationContext = compilationContext
        this._projectContext = projectContext
    }''')
    
    content = content.replaceAll(/protected GrailsDiagnosticService getDiagnostics\(\)\s+\{ _service\.diagnostics \}/, "protected GrailsDiagnosticService getDiagnostics()  { _providerContext.diagnostics }")
    content = content.replaceAll(/protected GrailsProject getProject\(\)\s+\{ _projectGetter\.getProject\(\) \}/, "protected GrailsProject getProject()                { _projectContext.project }")
    content = content.replaceAll(/protected GrailsService getService\(\)\s+\{ _service \}/, "")
    content = content.replaceAll(/protected <T> T withReadLock\(groovy\.lang\.Closure<T> closure\) \{\s+_service\.withReadLock\(closure\)\s+\}/, "protected <T> T withReadLock(groovy.lang.Closure<T> closure) {\n        _compilationContext.withReadLock(closure)\n    }")
    content = content.replaceAll(/interface GrailsProjectGetter \{\s+GrailsProject getProject\(\)\s+\}/, "")
    bpFile.toFile().text = content
    println "Updated BaseProvider"
}

def updateSubclasses = { ->
    Files.walk(providersDir).filter { Files.isRegularFile(it) && it.toString().endsWith(".groovy") && !it.toString().endsWith("ProviderRegistry.groovy") && !it.toString().endsWith("BaseProvider.groovy") }.forEach { file ->
        def text = file.toFile().text
        if (text.contains("extends BaseProvider") || text.contains("implements") || file.fileName.toString().endsWith("Provider.groovy")) {
            def originalText = text
            text = text.replaceAll("import kingsk.grails.lsp.GrailsService\n", "")
            
            // Replace constructor
            def className = file.fileName.toString().replace(".groovy", "")
            
            def regex1 = /(?s)$className\(\s*GrailsService service\s*\)\s*\{\s*super\(service\)\s*\}/
            def replacement1 = """${className}(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }"""
            
            text = text.replaceAll(regex1, replacement1)
            
            // Also some might have `this.service = service`
            def regex2 = /(?s)private final GrailsService service\s*$className\(\s*GrailsService service\s*\)\s*\{\s*this\.service = service\s*\}/
            def replacement2 = """private final kingsk.grails.lsp.context.ProviderContext providerContext
    private final kingsk.grails.lsp.context.CompilationContext compilationContext
    private final kingsk.grails.lsp.context.ProjectContext projectContext

    ${className}(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        this.providerContext = providerContext
        this.compilationContext = compilationContext
        this.projectContext = projectContext
    }"""
            text = text.replaceAll(regex2, replacement2)
            
            // Replace service. usages for those that were directly using service (like TestDiscoveryProvider)
            text = text.replaceAll(/service\.errorService/, "providerContext.errorService")
            text = text.replaceAll(/service\.project/, "projectContext.project")
            text = text.replaceAll(/service\.fileTracker/, "compilationContext.fileTracker")
            text = text.replaceAll(/service\.astService/, "compilationContext.astService")
            text = text.replaceAll(/service\.diagnostics/, "providerContext.diagnostics")
            text = text.replaceAll(/service\.visitor/, "compilationContext.visitor")
            text = text.replaceAll(/service\.compiler/, "compilationContext.compiler")
            
            if (originalText != text) {
                file.toFile().text = text
                println "Updated $className"
            }
        }
    }
}

updateBaseProvider()
updateSubclasses()
