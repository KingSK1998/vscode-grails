const fs = require('fs');
const path = require('path');

const baseDir = "d:/Grails_Framework_Support_Extension/vscode-gng-support/server/src/main/groovy/kingsk/grails/lsp";
const providersDir = path.join(baseDir, "providers");

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? walkDir(dirPath, callback) : callback(path.join(dir, f));
    });
}

function updateBaseProvider() {
    const bpFile = path.join(providersDir, "document/BaseProvider.groovy");
    let content = fs.readFileSync(bpFile, 'utf8');
    
    content = content.replace("import kingsk.grails.lsp.GrailsService\n", "");
    
    const oldConstructor = /private final GrailsProjectGetter _projectGetter\s+private final GrailsService _service\s+BaseProvider\(ProviderContext providerContext, CompilationContext compilationContext, GrailsProjectGetter projectGetter, GrailsService service\) \{\s+this\._providerContext = providerContext\s+this\._compilationContext = compilationContext\s+this\._projectGetter = projectGetter\s+this\._service = service\s+\}\s+BaseProvider\(GrailsService service\) \{\s+this\(service as ProviderContext, service as CompilationContext, \{ service\.project \}, service\)\s+\}/;
    const newConstructor = `private final kingsk.grails.lsp.context.ProjectContext _projectContext

    BaseProvider(ProviderContext providerContext, CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        this._providerContext = providerContext
        this._compilationContext = compilationContext
        this._projectContext = projectContext
    }`;
    content = content.replace(oldConstructor, newConstructor);
    
    content = content.replace(/protected GrailsDiagnosticService getDiagnostics\(\)\s+\{ _service\.diagnostics \}/, "protected GrailsDiagnosticService getDiagnostics()  { _providerContext.diagnostics }");
    content = content.replace(/protected GrailsProject getProject\(\)\s+\{ _projectGetter\.getProject\(\) \}/, "protected GrailsProject getProject()                { _projectContext.project }");
    content = content.replace(/protected GrailsService getService\(\)\s+\{ _service \}/, "");
    content = content.replace(/protected <T> T withReadLock\(groovy\.lang\.Closure<T> closure\) \{\s+_service\.withReadLock\(closure\)\s+\}/, "protected <T> T withReadLock(groovy.lang.Closure<T> closure) {\n        _compilationContext.withReadLock(closure)\n    }");
    content = content.replace(/interface GrailsProjectGetter \{\s+GrailsProject getProject\(\)\s+\}/, "");
    
    fs.writeFileSync(bpFile, content);
    console.log("Updated BaseProvider");
}

function updateSubclasses() {
    walkDir(providersDir, function(filePath) {
        if (filePath.endsWith(".groovy") && !filePath.endsWith("ProviderRegistry.groovy") && !filePath.endsWith("BaseProvider.groovy")) {
            let text = fs.readFileSync(filePath, 'utf8');
            const originalText = text;
            
            if (text.includes("extends BaseProvider") || text.includes("implements") || filePath.endsWith("Provider.groovy")) {
                text = text.replace("import kingsk.grails.lsp.GrailsService\n", "");
                
                const className = path.basename(filePath, ".groovy");
                
                const regex1 = new RegExp(`${className}\\(\\s*GrailsService service\\s*\\)\\s*\\{\\s*super\\(service\\)\\s*\\}`, "s");
                const replacement1 = `${className}(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {\n        super(providerContext, compilationContext, projectContext)\n    }`;
                text = text.replace(regex1, replacement1);
                
                const regex2 = new RegExp(`private final GrailsService service\\s*${className}\\(\\s*GrailsService service\\s*\\)\\s*\\{\\s*this\\.service = service\\s*\\}`, "s");
                const replacement2 = `private final kingsk.grails.lsp.context.ProviderContext providerContext\n    private final kingsk.grails.lsp.context.CompilationContext compilationContext\n    private final kingsk.grails.lsp.context.ProjectContext projectContext\n\n    ${className}(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {\n        this.providerContext = providerContext\n        this.compilationContext = compilationContext\n        this.projectContext = projectContext\n    }`;
                text = text.replace(regex2, replacement2);
                
                text = text.replace(/service\.errorService/g, "providerContext.errorService");
                text = text.replace(/service\.project/g, "projectContext.project");
                text = text.replace(/service\.fileTracker/g, "compilationContext.fileTracker");
                text = text.replace(/service\.astService/g, "compilationContext.astService");
                text = text.replace(/service\.diagnostics/g, "providerContext.diagnostics");
                text = text.replace(/service\.visitor/g, "compilationContext.visitor");
                text = text.replace(/service\.compiler/g, "compilationContext.compiler");
                
                if (originalText !== text) {
                    fs.writeFileSync(filePath, text);
                    console.log(`Updated ${className}`);
                }
            }
        }
    });
}

updateBaseProvider();
updateSubclasses();
