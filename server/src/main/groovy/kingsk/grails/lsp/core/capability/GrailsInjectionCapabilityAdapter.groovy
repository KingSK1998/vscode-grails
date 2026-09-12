package kingsk.grails.lsp.core.capability

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.utils.ast.MemberExtractor
import kingsk.grails.lsp.utils.completion.CompletionUtil
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.Collections

/**
 * Capability adapter for Grails dependency injection, controller, and service conventions.
 *
 * Implements:
 * 1. Detection: Grails artifact traits and conventions in project.
 * 2. Inputs: Controller, Service, TagLib ClassNodes, AST field definitions, and project service registry.
 * 3. Derivations: Injected properties (params, request, session, etc.), controller methods (render, redirect),
 *    params helper methods, injected service method resolution, and command object validation.
 * 4. Applicability: Scoped to Grails artifacts in Grails projects.
 * 5. Invalidation: Project build model, classpath, or AST revisions.
 * 6. Limits: Convention-based name matching without runtime Spring container execution.
 */
@Slf4j
@CompileStatic
class GrailsInjectionCapabilityAdapter implements GrailsCapabilityAdapter {

    public static final GrailsInjectionCapabilityAdapter INSTANCE = new GrailsInjectionCapabilityAdapter()

    private static final List<String> CONTROLLER_PROPERTIES = Collections.unmodifiableList([
        'params', 'request', 'response', 'session', 'flash',
        'servletContext', 'grailsApplication', 'actionName',
        'controllerName', 'webRequest'
    ])

    private static final List<String> CONTROLLER_METHODS = Collections.unmodifiableList([
        'render', 'redirect', 'forward', 'chain', 'withFormat',
        'bindData', 'respond'
    ])

    private static final List<String> RENDER_PARAMETERS = Collections.unmodifiableList([
        'view', 'model', 'text', 'status', 'template', 'collection', 'contentType'
    ])

    private static final List<String> PARAMS_METHODS = Collections.unmodifiableList([
        'get', 'containsKey', 'keySet', 'values', 'size',
        'entrySet', 'list', 'int', 'long', 'boolean', 'date'
    ])

    private static final List<String> VALIDATEABLE_METHODS = Collections.unmodifiableList([
        'validate', 'hasErrors', 'errors', 'clearErrors'
    ])

    @Override
    String getCapabilityName() {
        return "GrailsInjection"
    }

    @Override
    String getDetectionEvidence() {
        return "grails.artefact.Controller or grails.artefact.Service on classpath, or artifact location"
    }

    @Override
    String getInputsDescription() {
        return "Controller, Service, TagLib ClassNodes, AST field definitions, and service registry"
    }

    @Override
    String getDerivationDescription() {
        return "Injects web scopes (params, request, session), controller methods, render arguments, service conventions, and command object methods"
    }

    @Override
    String getApplicabilityDescription() {
        return "Grails controllers, services, and tag libraries within Grails projects"
    }

    @Override
    String getInvalidationDescription() {
        return "Evaluated per RequestContext snapshot; invalidated on class, property, or dependency update"
    }

    @Override
    String getLimitsDescription() {
        return "Static convention matching for services (<name>Service) without running Spring ApplicationContext (zero Spring container startup)"
    }

    @Override
    boolean isApplicable(RequestContext ctx, String uri = null) {
        if (ctx == null) return false
        return ctx.grailsProject()?.isGrailsProject ?: false
    }

    /**
     * Gets injected property completions for a Controller.
     */
    List<CompletionItem> getControllerProperties() {
        List<CompletionItem> items = []
        for (String p : CONTROLLER_PROPERTIES) {
            CompletionItem item = new CompletionItem(p)
            item.kind = CompletionItemKind.Property
            item.detail = "Grails Injected Property"
            items.add(item)
        }
        return items
    }

    /**
     * Gets controller methods (render, redirect, etc.).
     */
    List<CompletionItem> getControllerMethods() {
        List<CompletionItem> items = []
        for (String m : CONTROLLER_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "Grails controller method"
            items.add(item)
        }
        return items
    }

    /**
     * Gets parameter completions for render call (view, model, text, status, etc.).
     */
    List<CompletionItem> getRenderParameters() {
        List<CompletionItem> items = []
        for (String p : RENDER_PARAMETERS) {
            CompletionItem item = new CompletionItem(p)
            item.kind = CompletionItemKind.Keyword
            item.detail = "Grails controller parameter / Grails controller method"
            items.add(item)
        }
        return items
    }

    /**
     * Gets completions for `params.` access in a controller.
     */
    List<CompletionItem> getParamsCompletions() {
        List<CompletionItem> items = []
        for (String m : PARAMS_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "Controller params"
            items.add(item)
        }
        return items
    }

    /**
     * Resolves completions for an injected service property in a controller.
     * Matches property name (e.g. "test", "testService", "personService") to known service ClassNodes.
     */
    List<CompletionItem> resolveInjectedServiceCompletions(String propertyName, RequestContext ctx) {
        if (!propertyName || ctx == null) return []

        // Derive candidate service names:
        // "test" -> "TestService", "testService" -> "TestService", "personService" -> "PersonService"
        String baseName = propertyName.endsWith("Service") ? propertyName.substring(0, propertyName.length() - 7) : propertyName
        String expectedSimpleName = baseName.capitalize() + "Service"

        ClassNode serviceClass = null
        def visitor = ctx.ast() as GrailsASTVisitor
        if (visitor != null) {
            for (ClassNode cn : visitor.classNodes) {
                if (cn.nameWithoutPackage == expectedSimpleName || cn.nameWithoutPackage.equalsIgnoreCase(expectedSimpleName)) {
                    serviceClass = cn
                    break
                }
            }
        }

        if (serviceClass == null && ctx.classLoader() != null) {
            // Try loading from project classloader
            List<String> candidatePackages = ["", "services.", "com.example.", "service."]
            for (String pkg : candidatePackages) {
                try {
                    Class<?> clazz = Class.forName(pkg + expectedSimpleName, false, ctx.classLoader())
                    serviceClass = org.codehaus.groovy.ast.ClassHelper.make(clazz)
                    break
                } catch (Throwable ignored) {
                }
            }
        }

        if (serviceClass == null) return []

        List<CompletionItem> items = []
        boolean isServiceTransactional = GrailsUtils.isTransactional(serviceClass)

        for (MethodNode method : serviceClass.methods) {
            if (method.isPublic() && !method.isStatic() && !method.name.startsWith('$') && method.name != 'class') {
                CompletionItem item = new CompletionItem(method.name)
                item.kind = CompletionItemKind.Method
                boolean methodTx = isServiceTransactional || method.annotations?.any {
                    it.classNode.name in ['Transactional', 'grails.gorm.transactions.Transactional']
                }
                if (methodTx) {
                    item.detail = "Injected service: ${expectedSimpleName} (@Transactional)"
                } else {
                    item.detail = "Injected service: ${expectedSimpleName}"
                }
                items.add(item)
            }
        }

        return items
    }

    /**
     * Gets completions for command objects implementing Validateable.
     */
    List<CompletionItem> getValidateableCompletions(ClassNode cmdClass) {
        List<CompletionItem> items = []
        for (String m : VALIDATEABLE_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "Command object method"
            items.add(item)
        }

        if (cmdClass != null) {
            for (PropertyNode prop : cmdClass.properties) {
                if (prop.name != 'class' && prop.name != 'metaClass' && !prop.isStatic()) {
                    CompletionItem item = new CompletionItem(prop.name)
                    item.kind = CompletionItemKind.Property
                    item.detail = "Command object property"
                    items.add(item)
                }
            }
        }

        return items
    }

    /**
     * Checks if a ClassNode is a Validateable command object.
     */
    boolean isValidateable(ClassNode classNode) {
        if (classNode == null) return false
        if (classNode.name.endsWith("Command")) return true
        if (classNode.interfaces?.any { it.name in ['grails.validation.Validateable', 'org.grails.datastore.gorm.GormValidateable'] }) return true
        if (classNode.annotations?.any { it.classNode.name in ['grails.validation.Validateable', 'Validateable'] }) return true
        return false
    }
}

