const fs = require('fs');
const path = require('path');

const baseDir = "d:/Grails_Framework_Support_Extension/vscode-gng-support/server/src/main/groovy/kingsk/grails/lsp";
const strategiesDir = path.join(baseDir, "providers", "completions", "strategies");

function walkDir(dir, callback) {
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? walkDir(dirPath, callback) : callback(path.join(dir, f));
    });
}

// 1. Fix Completion Strategies
walkDir(strategiesDir, function(filePath) {
    if (filePath.endsWith(".groovy")) {
        let text = fs.readFileSync(filePath, 'utf8');
        const originalText = text;
        
        text = text.replace(/request\.service\.compiler/g, "request.compilationContext.compiler");
        text = text.replace(/request\.service\.visitor/g, "request.compilationContext.visitor");
        text = text.replace(/request\.service\.discoveryService/g, "request.providerContext.discoveryService");
        text = text.replace(/request\.service\.fileTracker/g, "request.compilationContext.fileTracker");
        text = text.replace(/request\.service/g, "request.projectContext"); // Catch remaining service usages and map to projectContext where it was project.
        
        if (originalText !== text) {
            fs.writeFileSync(filePath, text);
            console.log(`Updated ${path.basename(filePath)}`);
        }
    }
});

// 2. Fix GrailsCompletionProvider
const completionProviderPath = path.join(baseDir, "providers", "document", "GrailsCompletionProvider.groovy");
let completionText = fs.readFileSync(completionProviderPath, 'utf8');
completionText = completionText.replace(/project\.isGrailsProject, providerContext, compilationContext, projectContext/g, "project.isGrailsProject, _providerContext, _compilationContext, _projectContext");
fs.writeFileSync(completionProviderPath, completionText);
console.log("Updated GrailsCompletionProvider");

