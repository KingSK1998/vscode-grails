package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.enums.CodeLensMode
import kingsk.grails.lsp.model.enums.GrailsArtifactType
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.grails.GrailsArtefactUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.TextDocumentIdentifier

import java.util.concurrent.CompletableFuture

/**
 * Provides code lens functionality for Grails artifacts.
 */
@Slf4j
@CompileStatic
class GrailsCodeLensProvider extends BaseProvider {

    GrailsCodeLensProvider(GrailsService service) {
        super(service)
    }

    /**
     * Provides code lenses for a document based on the configured mode.
     */
    CompletableFuture<List<? extends CodeLens>> provideCodeLens(TextDocumentIdentifier textDocument) {
        def mode = config.codeLensMode
        if (mode == CodeLensMode.OFF) {
            log.debug "[CODE_LENS] Code lens is disabled (OFF mode)"
            return CompletableFuture.completedFuture([] as List<CodeLens>)
        }

        String uri = TextFile.normalizePath(textDocument.uri)
        log.debug "[CODE_LENS] Providing code lens for document: $uri with mode: $mode"

        def classNodes = visitor.getClassNodes(uri)
        if (!classNodes) {
            log.debug "[CODE_LENS] No class nodes found in document: $uri"
            return CompletableFuture.completedFuture([] as List<CodeLens>)
        }

        List<CodeLens> codeLenses = []
        classNodes.each { classNode ->
            def artifactType = GrailsArtefactUtils.getGrailsArtifactType(classNode, uri)
            log.debug "[CODE_LENS] Found artifact type: $artifactType for class: ${classNode.name}"

            addBasicCodeLenses(codeLenses, classNode, artifactType, uri, mode)

            if (mode >= CodeLensMode.ADVANCED) {
                addAdvancedCodeLenses(codeLenses, classNode, artifactType, uri)
            }

            if (mode == CodeLensMode.FULL) {
                addFullCodeLenses(codeLenses, classNode, uri)
            }
        }

        log.debug "[CODE_LENS] Returning ${codeLenses.size()} code lenses"
        CompletableFuture.completedFuture(codeLenses)
    }

    static CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
        def data = unresolved.data as Map
        if (!data) return CompletableFuture.completedFuture(unresolved)

        String type = (String) (data.type ?: "")
        String label = ""
        Command command = null

        switch (type) {
            case "test":
                def action = (String) data.action
                label = action == "debug" ? "Debug" : "Run"
                command = new Command(label, "grailsLsp.runTest", [data.path, data.method, data.action])
                break
            case "main":
                def action = (String) data.action
                label = action == "debug" ? "Debug" : "Run"
                command = new Command(label, "grailsLsp.runApp", [data.path, data.action])
                break
            case "controller":
                label = "Show Route"
                command = new Command(label, "grailsLsp.showRoute", [data.path, data.method])
                break
            case "mapping":
                label = "URL Mapping"
                command = new Command(label, "grailsLsp.previewMapping", [data.path, data.method])
                break
            case "domain":
                label = "CRUD Operations"
                command = new Command(label, "grailsLsp.showCurdOperations", [data.path, data.className])
                break
            case "injection":
                label = "Injected: ${data.service}"
                command = new Command(label, "grailsLsp.openService", [data.service])
                break
            case "override":
                label = "@Overridden"
                command = new Command(label, "grailsLsp.openParentMethod", [data.path, data.method])
                break
            case "reference":
                label = "References"
                command = new Command(label, "grailsLsp.findReferences", [data.path, data.method])
                break
            case "super":
                label = "Go to Super"
                command = new Command(label, "grailsLsp.goToSuperMethod", [data.path, data.method])
                break
            case "refCount":
                int refCount = 0
                label = "$refCount References"
                command = new Command(label, "grailsLsp.showReferences", [data.path, data.method])
                break
            default:
                command = new Command("Details", "grailsLsp.defaultAction", [data.path, data.method])
        }
        unresolved.command = command
        CompletableFuture.completedFuture(unresolved)
    }

    private static void addBasicCodeLenses(List<CodeLens> codeLenses, ClassNode classNode,
                                           GrailsArtifactType artifactType, String uri, CodeLensMode mode) {
        if (mode < CodeLensMode.BASIC) return

        if (artifactType == GrailsArtifactType.SPOCK_TEST || artifactType == GrailsArtifactType.JUNIT_TEST) {
            addTestRunnerLenses(codeLenses, classNode, uri)
        } else {
            addMethodReferenceLenses(codeLenses, classNode, uri)
        }

        addServiceInjectionLenses(codeLenses, classNode, uri)
    }

    private static void addTestRunnerLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.methods.each { method ->
            if (method.synthetic || !isTestMethod(method)) return

            def runLens = ASTUtils.astNodeToCodeLens(method, [
                type     : "test",
                action   : "run",
                className: classNode.nameWithoutPackage,
                method   : method.name,
                path     : uri
            ], "Run")

            def debugLens = ASTUtils.astNodeToCodeLens(method, [
                type     : "test",
                action   : "debug",
                className: classNode.nameWithoutPackage,
                method   : method.name,
                path     : uri
            ], "Debug")

            if (runLens) codeLenses << runLens
            if (debugLens) codeLenses << debugLens
        }
    }

    private static boolean isTestMethod(MethodNode method) {
        !method.synthetic &&
            (method.name.startsWith("test") ||
                method.name.contains("should") ||
                method.name.contains("when") ||
                method.name.contains("given"))
    }

    private static void addMethodReferenceLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.methods.each { method ->
            if (method.synthetic || method.private || method.name == "<init>") return

            def lens = ASTUtils.astNodeToCodeLens(method, [
                type  : "reference",
                method: method.name,
                path  : uri
            ])
            if (lens) codeLenses << lens
        }
    }

    private static void addServiceInjectionLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.fields.findAll {
            !it.synthetic && (it.name.endsWith("Service") || it.type.name.endsWith("Service"))
        }.each { field ->
            def lens = ASTUtils.astNodeToCodeLens(field, [
                type   : "injection",
                service: field.type.name,
                path   : uri
            ], "Injection")
            if (lens) codeLenses << lens
        }
    }

    private static void addAdvancedCodeLenses(List<CodeLens> codeLenses, ClassNode classNode, GrailsArtifactType artifactType, String uri) {
        if (artifactType == GrailsArtifactType.URL_MAPPINGS) addUrlMappingLenses(codeLenses, classNode, uri)
        if (artifactType == GrailsArtifactType.CONTROLLER) addControllerRouteLenses(codeLenses, classNode, uri)
        if (artifactType == GrailsArtifactType.DOMAIN) addDomainClassLenses(codeLenses, classNode, uri)
        if (artifactType == GrailsArtifactType.APPLICATION && ASTUtils.hasMainMethod(classNode)) addApplicationRunnerLenses(codeLenses, classNode, uri)
    }

    private static void addUrlMappingLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.methods.each { method ->
            if (method.synthetic) return

            def lens = ASTUtils.astNodeToCodeLens(method, [
                type  : "mapping",
                action: "preview",
                method: method.name,
                path  : uri
            ], "URL Mapping")
            if (lens) codeLenses << lens
        }
    }

    private static void addControllerRouteLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.methods.findAll {
            !it.synthetic && !it.isPrivate() && it.name != "<init>" &&
                !it.name.startsWith("get") && !it.name.startsWith("set")
        }.each { method ->
            def lens = ASTUtils.astNodeToCodeLens(method, [
                type  : "controller",
                action: "showRoute",
                method: method.name,
                path  : uri
            ], "Show Route")
            if (lens) codeLenses << lens
        }
    }

    private static void addDomainClassLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        def lens = ASTUtils.astNodeToCodeLens(classNode, [
            type     : "domain",
            action   : "showCrud",
            className: classNode.name,
            path     : uri
        ], "CRUD Operations")
        if (lens) codeLenses << lens
    }

    private static void addApplicationRunnerLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        def runLens = ASTUtils.astNodeToCodeLens(classNode, [
            type     : "main",
            action   : "run",
            className: classNode.name,
            path     : uri
        ], "Run")

        def debugLens = ASTUtils.astNodeToCodeLens(classNode, [
            type     : "main",
            action   : "debug",
            className: classNode.name,
            path     : uri
        ], "Debug")

        if (runLens) codeLenses << runLens
        if (debugLens) codeLenses << debugLens
    }

    private static void addFullCodeLenses(List<CodeLens> codeLenses, ClassNode classNode, String uri) {
        classNode.methods.findAll { !it.synthetic && ASTUtils.isOverriddenMethod(it, classNode) }.each { method ->
            def lens = ASTUtils.astNodeToCodeLens(method, [
                type  : "override",
                method: method.name,
                path  : uri
            ], "@Overridden")
            if (lens) codeLenses << lens
        }

        classNode.methods.findAll { !it.synthetic && !it.private && it.name != "<init>" }.each { method ->
            def lens = ASTUtils.astNodeToCodeLens(method, [
                type  : "refCount",
                method: method.name,
                path  : uri
            ])
            if (lens) codeLenses << lens
        }
    }
}
