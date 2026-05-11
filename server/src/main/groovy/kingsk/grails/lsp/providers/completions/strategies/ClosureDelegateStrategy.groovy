package kingsk.grails.lsp.providers.completions.strategies

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.enums.CompletionTarget
import kingsk.grails.lsp.providers.completions.BaseCompletionStrategy
import kingsk.grails.lsp.providers.completions.CompletionRequest
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.eclipse.lsp4j.CompletionItem

/**
 * Handles completions for Closure Delegates inside DSLs, e.g., obj.with { | }
 */
@Slf4j
@CompileStatic
class ClosureDelegateStrategy extends BaseCompletionStrategy {

    ClosureDelegateStrategy(CompletionRequest request) { super(request) }

    @Override
    int getPriority() { return 65 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(ASTNode node) {
        return getEnclosingClosureMethodCall(node) != null || getEnclosingClosureField(node) != null
    }

    @Override
    void provideCompletions(ASTNode node) {
        MethodCallExpression methodCall = getEnclosingClosureMethodCall(node)
        if (methodCall) {
            ClassNode delegateType = resolveDelegateType(methodCall)
            if (delegateType) {
                log.debug("Found closure delegate type from method call: ${delegateType.name}")
                org.codehaus.groovy.ast.expr.VariableExpression dummyObj = new org.codehaus.groovy.ast.expr.VariableExpression("it", delegateType)
                addMemberCompletions(dummyObj)
            }
            return
        }

        def fieldClosure = getEnclosingClosureField(node)
        if (fieldClosure) {
            String fieldName = fieldClosure.name
            ClassNode domainClass = request.getCurrentClass()
            if (domainClass) {
                if (fieldName == 'constraints') {
                    // Inject domain properties as they can be constrained
                    domainClass.properties.each { org.codehaus.groovy.ast.PropertyNode p ->
                        CompletionItem item = new CompletionItem(p.name)
                        item.kind = org.eclipse.lsp4j.CompletionItemKind.Property
                        item.detail = "Domain property constraint"
                        item.insertText = (p.name + "(nullable: false)").toString()
                        request.addCompletion(item)
                    }
                } else if (fieldName == 'mapping') {
                    // Standard GORM mapping DSL keywords
                    ['table', 'version', 'cache', 'id', 'columns', 'autoTimestamp', 'sort', 'datasource'].each { String m ->
                        CompletionItem item = new CompletionItem(m)
                        item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
                        item.detail = "GORM Mapping DSL"
                        item.insertText = (m + " ").toString()
                        request.addCompletion(item)
                    }
                } else if (fieldName == 'namedQueries') {
                    // Provide basic criteria keywords
                    ['eq', 'ne', 'like', 'ilike', 'gt', 'lt', 'ge', 'le', 'between', 'inList', 'isNull', 'isNotNull'].each { String m ->
                        CompletionItem item = new CompletionItem(m)
                        item.kind = org.eclipse.lsp4j.CompletionItemKind.Method
                        item.detail = "GORM Criteria Method"
                        item.insertText = (m + "('')").toString()
                        request.addCompletion(item)
                    }
                }
            }
        }
    }

    private org.codehaus.groovy.ast.FieldNode getEnclosingClosureField(ASTNode node) {
        ASTNode current = node
        while (current != null) {
            if (current instanceof ClosureExpression) {
                ASTNode parent = getParentOf(current)
                if (parent instanceof org.codehaus.groovy.ast.FieldNode) {
                    return parent as org.codehaus.groovy.ast.FieldNode
                }
            }
            current = getParentOf(current)
        }
        return null
    }

    private MethodCallExpression getEnclosingClosureMethodCall(ASTNode node) {
        ASTNode current = node
        while (current != null) {
            if (current instanceof ClosureExpression) {
                ASTNode parent = getParentOf(current)
                if (parent instanceof org.codehaus.groovy.ast.expr.ArgumentListExpression) {
                    ASTNode grandParent = getParentOf(parent)
                    if (grandParent instanceof MethodCallExpression) {
                        return grandParent
                    }
                }
            }
            current = getParentOf(current)
        }
        return null
    }

    private ClassNode resolveDelegateType(MethodCallExpression methodCall) {
        String methodName = methodCall.methodAsString
        
        // Handle common groovy methods like 'with', 'tap'
        if (methodName in ['with', 'tap']) {
            return getTypeOf(methodCall.objectExpression)
        }
        
        // Handle Grails DSLs
        if (methodName in ['createCriteria', 'where', 'withCriteria']) {
            return getTypeOf(methodCall.objectExpression) // The domain class
        }
        
        // Custom DSL handling based on simple heuristics:
        // if it's called on an object, assume that object is the delegate sometimes, but not always.
        // Actually, without explicit @DelegatesTo, it's hard. 
        // We'll just rely on object type for known DSLs.
        
        // For Grails controllers/services, certain blocks have known delegates
        if (methodName == 'mapping') {
            // maybe mapping block
        }
        
        return null
    }
}
