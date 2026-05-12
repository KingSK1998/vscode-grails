package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import io.github.classgraph.ClassInfo
import io.github.classgraph.PackageInfo
import io.github.classgraph.ScanResult
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.services.DiscoveryService
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ImportNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind

/**
 * Handles completions for import statements
 */
@CompileStatic
class ImportStrategy extends BaseCompletionStrategy {

    ImportStrategy(CompletionRequest request) { super(request) }

    @Override
    int getPriority() { return 95 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(ASTNode node) { return node instanceof ImportNode }

    /**
     * Inside import statements like <code>import java.ut</code>
     * @param node The ImportNode node to complete
     */
    @Override
    void provideCompletions(ASTNode node) {
        ScanResult scanResult = DiscoveryService.getClassGraphScanResult(request.file.uri)
        if (scanResult) {
            String prefix = request.prefix ?: ""
            int pkgCount = 0
            for (PackageInfo pkgInfo : scanResult.packageInfo) {
                if (pkgCount >= 50) break
                if (pkgInfo.name.startsWith(prefix)) {
                    CompletionItem item = new CompletionItem(pkgInfo.name)
                    item.kind = CompletionItemKind.Module
                    request.addCompletion(item)
                    pkgCount++
                }
            }

            int clsCount = 0
            for (ClassInfo classInfo : scanResult.allClasses) {
                if (clsCount >= 100) break
                String className = classInfo.name
                String simpleName = classInfo.simpleName
                if (className.startsWith(prefix) || simpleName.startsWith(prefix)) {
                    CompletionItem item = new CompletionItem(className)
                    item.kind = classInfo.isInterface() ? CompletionItemKind.Interface :
                        (classInfo.isEnum() ? CompletionItemKind.Enum : CompletionItemKind.Class)
                    item.detail = classInfo.packageName
                    item.insertText = className
                    request.addCompletion(item)
                    clsCount++
                }
            }
        }

        addPackageCompletions(request)
        addClassCompletions(request)
        if (request.isGrailsProject) {
            addGrailsImportCompletions(request)
        }
    }

    private static void addPackageCompletions(CompletionRequest request) {
        List<String> commonPackages = [
            'java.lang', 'java.util', 'java.io', 'java.net',
            'groovy.lang', 'groovy.util', 'groovy.transform'
        ]

        if (request.isGrailsProject) {
            commonPackages.addAll([
                'grails.', 'org.grails.', 'grails.web.', 'grails.gorm.',
                'grails.artefact.', 'grails.plugin.'
            ])
        }

        commonPackages.each { pkg ->
            CompletionItem item = new CompletionItem(pkg)
            item.kind = CompletionItemKind.Module
            item.detail = 'Package'
            request.addCompletion(item)
        }
    }

    private static void addClassCompletions(CompletionRequest request) {
        DiscoveryService.getJavaLangTypes().each { cls ->
            CompletionItem item = new CompletionItem(cls)
            item.kind = CompletionItemKind.Class
            item.detail = 'Java Lang Class'
            request.addCompletion(item)
        }

        DiscoveryService.getJavaUtilTypes().each { cls ->
            CompletionItem item = new CompletionItem(cls)
            item.kind = CompletionItemKind.Class
            item.detail = 'Java Util Class'
            request.addCompletion(item)
        }

        DiscoveryService.getGroovyTypeNodes().each { clsNode ->
            CompletionItem item = new CompletionItem(clsNode.nameWithoutPackage)
            item.kind = CompletionItemKind.Class
            item.detail = 'Groovy Class'
            request.addCompletion(item)
        }
    }

    private static void addGrailsImportCompletions(CompletionRequest request) {
        List<String> grailsImports = [
            'grails.validation.Validateable',
            'grails.artefact.Controller',
            'grails.artefact.Service',
            'grails.artefact.DomainClass',
            'grails.artefact.TagLib',
            'grails.gorm.transactions.Transactional',
            'grails.util.GrailsNameUtils',
            'grails.core.GrailsApplication',
            'grails.web.api.WebAttributes'
        ]

        grailsImports.each { cls ->
            CompletionItem item = new CompletionItem(cls)
            item.kind = CompletionItemKind.Class
            item.detail = 'Grails class'
            request.addCompletion(item)
        }
    }
}