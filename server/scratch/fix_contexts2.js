const fs = require('fs');
const path = require('path');

const baseDir = "d:/Grails_Framework_Support_Extension/vscode-gng-support/server/src/main/groovy/kingsk/grails/lsp";

// 1. Fix BaseCompletionStrategy
const baseCompletionStrategyPath = path.join(baseDir, "providers", "completions", "BaseCompletionStrategy.groovy");
let text = fs.readFileSync(baseCompletionStrategyPath, 'utf8');
text = text.replace(/request\.service\.discoveryService/g, "request.providerContext.discoveryService");
fs.writeFileSync(baseCompletionStrategyPath, text);

// 2. Fix GrailsSnippetStrategy
const snippetStrategyPath = path.join(baseDir, "providers", "completions", "strategies", "snippet", "GrailsSnippetStrategy.groovy");
let text2 = fs.readFileSync(snippetStrategyPath, 'utf8');
text2 = text2.replace(/request\.projectContext\.config/g, "request.providerContext.config");
fs.writeFileSync(snippetStrategyPath, text2);

// 3. Fix BaseProvider variables to be protected
const baseProviderPath = path.join(baseDir, "providers", "document", "BaseProvider.groovy");
let text3 = fs.readFileSync(baseProviderPath, 'utf8');
text3 = text3.replace(/private final kingsk\.grails\.lsp\.context\.ProviderContext _providerContext/g, "protected final kingsk.grails.lsp.context.ProviderContext providerContext");
text3 = text3.replace(/private final kingsk\.grails\.lsp\.context\.CompilationContext _compilationContext/g, "protected final kingsk.grails.lsp.context.CompilationContext compilationContext");
text3 = text3.replace(/private final kingsk\.grails\.lsp\.context\.ProjectContext _projectContext/g, "protected final kingsk.grails.lsp.context.ProjectContext projectContext");
text3 = text3.replace(/this\._providerContext = providerContext/g, "this.providerContext = providerContext");
text3 = text3.replace(/this\._compilationContext = compilationContext/g, "this.compilationContext = compilationContext");
text3 = text3.replace(/this\._projectContext = projectContext/g, "this.projectContext = projectContext");
text3 = text3.replace(/_compilationContext\./g, "compilationContext.");
text3 = text3.replace(/_providerContext\./g, "providerContext.");
text3 = text3.replace(/_projectContext\./g, "projectContext.");
fs.writeFileSync(baseProviderPath, text3);

// 4. Fix GrailsCompletionProvider to use providerContext
const completionProviderPath = path.join(baseDir, "providers", "document", "GrailsCompletionProvider.groovy");
let text4 = fs.readFileSync(completionProviderPath, 'utf8');
text4 = text4.replace(/_providerContext, _compilationContext, _projectContext/g, "providerContext, compilationContext, projectContext");
fs.writeFileSync(completionProviderPath, text4);

console.log("Fixed BaseProvider, BaseCompletionStrategy, GrailsSnippetStrategy, GrailsCompletionProvider");
