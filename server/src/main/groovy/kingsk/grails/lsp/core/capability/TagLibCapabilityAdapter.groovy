package kingsk.grails.lsp.core.capability

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.core.visitor.GrailsASTVisitor
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.PropertyNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.Collections

/**
 * Capability adapter for Grails TagLib conventions.
 *
 * Implements:
 * 1. Detection: TagLib traits and project TagLib classes under grails-app/taglib/.
 * 2. Inputs: TagLib ClassNodes, tag closures/methods, and declared namespaces.
 * 3. Derivations: Tag completions for default 'g' namespace and custom namespaces.
 * 4. Applicability: Controllers, views, GSP, and TagLib files in Grails projects.
 * 5. Invalidation: TagLib AST changes.
 * 6. Limits: Static AST inspection without runtime tag evaluation.
 */
@Slf4j
@CompileStatic
class TagLibCapabilityAdapter implements GrailsCapabilityAdapter {

    public static final TagLibCapabilityAdapter INSTANCE = new TagLibCapabilityAdapter()

    private static final List<String> STANDARD_GSP_TAGS = Collections.unmodifiableList([
        'link', 'form', 'textField', 'passwordField', 'select', 'submitButton',
        'checkBox', 'radio', 'hiddenField', 'message', 'render', 'each',
        'if', 'else', 'elseif', 'include', 'formatDate', 'formatNumber',
        'createLink', 'resource', 'paginate', 'uploadForm'
    ])

    @Override
    String getCapabilityName() {
        return "TagLib"
    }

    @Override
    String getDetectionEvidence() {
        return "grails.artefact.TagLib or classes in grails-app/taglib/"
    }

    @Override
    String getInputsDescription() {
        return "Default 'g' namespace conventions, TagLib ClassNodes, static namespace definitions, and closure tag definitions"
    }

    @Override
    String getDerivationDescription() {
        return "Expands GSP tags for namespace (g. or custom) from standard tags and project TagLib definitions"
    }

    @Override
    String getApplicabilityDescription() {
        return "Grails projects with TagLib artifacts; applicable in controllers, taglibs, and GSP views"
    }

    @Override
    String getInvalidationDescription() {
        return "Evaluated per RequestContext snapshot; immediately updates when TagLib files change"
    }

    @Override
    String getLimitsDescription() {
        return "Pure static AST analysis of tag methods and closures; zero runtime execution of tag closures"
    }

    @Override
    boolean isApplicable(RequestContext ctx, String uri = null) {
        if (ctx == null) return false
        return ctx.grailsProject()?.isGrailsProject ?: false
    }

    /**
     * Gets tag completions for a given namespace (e.g. "g" or custom namespace).
     */
    List<CompletionItem> getTagCompletions(String namespace = "g", RequestContext ctx = null) {
        List<CompletionItem> items = []
        Set<String> seen = new HashSet<>()

        if (namespace == "g" || namespace == null || namespace.isEmpty()) {
            for (String tag : STANDARD_GSP_TAGS) {
                CompletionItem item = new CompletionItem(tag)
                item.kind = CompletionItemKind.Method
                item.detail = "GSP tag"
                items.add(item)
                seen.add(tag)
            }
        }

        // Also check project TagLibs if AST is available
        if (ctx != null) {
            def visitor = ctx.ast() as GrailsASTVisitor
            if (visitor != null) {
                for (ClassNode classNode : visitor.classNodes) {
                    if (GrailsUtils.isTagLibClass(classNode, visitor.getURI(classNode))) {
                        String taglibNamespace = getTagLibNamespace(classNode)
                        if (taglibNamespace == (namespace ?: "g")) {
                            for (PropertyNode prop : classNode.properties) {
                                if (prop.name != 'class' && prop.name != 'metaClass' && prop.name != 'namespace' && !seen.contains(prop.name)) {
                                    CompletionItem item = new CompletionItem(prop.name)
                                    item.kind = CompletionItemKind.Method
                                    item.detail = "GSP tag (${classNode.nameWithoutPackage})"
                                    items.add(item)
                                    seen.add(prop.name)
                                }
                            }
                            for (MethodNode method : classNode.methods) {
                                if (method.isPublic() && !method.isStatic() && !method.name.startsWith('$') && !seen.contains(method.name)) {
                                    CompletionItem item = new CompletionItem(method.name)
                                    item.kind = CompletionItemKind.Method
                                    item.detail = "GSP tag (${classNode.nameWithoutPackage})"
                                    items.add(item)
                                    seen.add(method.name)
                                }
                            }
                        }
                    }
                }
            }
        }

        return items
    }

    private String getTagLibNamespace(ClassNode classNode) {
        FieldNode nsField = classNode.getField('namespace')
        if (nsField != null && nsField.hasInitialExpression()) {
            return nsField.initialExpression.text
        }
        PropertyNode nsProp = classNode.getProperty('namespace')
        if (nsProp != null && nsProp.hasInitialExpression()) {
            return nsProp.initialExpression.text
        }
        return "g"
    }
}

