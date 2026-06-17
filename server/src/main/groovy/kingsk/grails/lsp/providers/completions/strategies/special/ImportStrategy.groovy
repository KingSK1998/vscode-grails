package kingsk.grails.lsp.providers.completions.strategies.special

import groovy.transform.CompileStatic
import io.github.classgraph.ClassInfo
import io.github.classgraph.PackageInfo
import io.github.classgraph.ScanResult
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.context.RequestContext
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

    @Override
    int getPriority() { return 95 }

    @Override
    boolean canHandle(CompletionRequest request, RequestContext ctx) {
        return request.offsetNode instanceof ImportNode
    }

    @Override
    List<CompletionItem> provideCompletions(CompletionRequest request, RequestContext ctx) {
        List<CompletionItem> completions = []
        ScanResult scanResult = DiscoveryService.getClassGraphScanResult(request.uri)
        if (scanResult) {
            String prefix = request.prefix ?: ""
            int pkgCount = 0
            for (PackageInfo pkgInfo : scanResult.packageInfo) {
                if (pkgCount >= 50) break
                if (pkgInfo.name.startsWith(prefix)) {
                    CompletionItem item = new CompletionItem(pkgInfo.name)
                    item.kind = CompletionItemKind.Module
                    completions.add(item)
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
                    completions.add(item)
                    clsCount++
                }
            }
        }

        addPackageCompletions(request, completions)
        addClassCompletions(request, completions)
        if (request.isGrailsProject) {
            addGrailsImportCompletions(request, completions)
        }
        return completions
    }

    private static void addPackageCompletions(CompletionRequest request, List<CompletionItem> completions) {
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
            completions.add(item)
        }
    }

    private static void addClassCompletions(CompletionRequest request, List<CompletionItem> completions) {
        DiscoveryService.getJavaLangTypes().each { cls ->
            CompletionItem item = new CompletionItem(cls)
            item.kind = CompletionItemKind.Class
            item.detail = 'Java Lang Class'
            completions.add(item)
        }

        DiscoveryService.getJavaUtilTypes().each { cls ->
            CompletionItem item = new CompletionItem(cls)
            item.kind = CompletionItemKind.Class
            item.detail = 'Java Util Class'
            completions.add(item)
        }

        DiscoveryService.getGroovyTypeNodes().each { clsNode ->
            CompletionItem item = new CompletionItem(clsNode.nameWithoutPackage)
            item.kind = CompletionItemKind.Class
            item.detail = 'Groovy Class'
            completions.add(item)
        }
    }

    private static void addGrailsImportCompletions(CompletionRequest request, List<CompletionItem> completions) {
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
            completions.add(item)
        }
    }
}
