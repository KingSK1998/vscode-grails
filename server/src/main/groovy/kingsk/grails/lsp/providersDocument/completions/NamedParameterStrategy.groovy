package kingsk.grails.lsp.providersDocument.completions

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.CompletionTarget
import kingsk.grails.lsp.providersDocument.CompletionRequest
import kingsk.grails.lsp.utils.ScopeHelper
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ReturnStatement
import org.eclipse.lsp4j.Position

/**
 * Provides named parameter completions for Grails DSL methods like render, redirect, etc.
 * Detects "methodName " pattern and provides context-appropriate named parameters from actual method signatures.
 */
@Slf4j
@CompileStatic
class NamedParameterStrategy extends BaseCompletionStrategy {

    NamedParameterStrategy(CompletionRequest request) {
        super(request)
    }

    @Override
    int getPriority() { return 87 }

    @Override
    CompletionTarget target() { return CompletionTarget.OFFSET }

    @Override
    boolean canHandle(ASTNode node) {
        // Only handle specific AST node types that could represent method contexts
        if (!(node instanceof MethodCallExpression ||
            node instanceof VariableExpression ||
            node instanceof BlockStatement)) {
            return false
        }

        // Use text-based detection for the actual logic
        //return detectNamedParameterContext()
        return true
    }

    @Override
    void provideCompletions(ASTNode node) {
//        String methodName = extractMethodNameFromContext()
//        if (!methodName) return

        //logDebug("Providing named parameter completions for method: %s", methodName)

        // Find methods and add their parameters
//        addParametersFromResolvedMethods(methodName)
        addParametersFromResolvedMethods(node)
    }

    /**
     * Detects if cursor is positioned after "methodName " (word + space)
     */
    /**
     * Detects if cursor is positioned after "methodName " (word + space)
     */
    private boolean detectNamedParameterContext(ASTNode node) {
        Position pos = request.position
        String lineText = request.file.textAtLine(pos.line)

        // Safety checks
        if (!lineText || pos.character <= 0 || pos.character > lineText.length()) {
            return false
        }

        // Pattern: "methodName " where cursor is positioned after the space
        // The prefix should be empty (we're at start of next token)
        if (!request.prefix.isEmpty()) return false

        // Check if previous character is a space
        int prevCharIndex = pos.character - 1
        if (prevCharIndex < 0 || prevCharIndex >= lineText.length()) {
            return false
        }

        if (lineText.charAt(prevCharIndex) != ' ') {
            return false
        }

        // Check if there's a method name before the space
        return extractMethodNameFromAST(node) != null
    }

/**
 * Extracts method name from AST structure instead of text parsing
 */
    private String extractMethodNameFromAST(ASTNode node) {
        try {
            // Case 1: Direct MethodCallExpression
            if (node instanceof MethodCallExpression) {
                return ((MethodCallExpression) node).methodAsString
            }

            // Case 2: VariableExpression (render without parentheses)
            if (node instanceof VariableExpression) {
                return ((VariableExpression) node).name
            }

            // Case 3: BlockStatement containing ReturnStatement with VariableExpression
            if (node instanceof BlockStatement) {
                BlockStatement block = (BlockStatement) node
                if (!block.statements || block.statements.isEmpty()) return null

                // Look for ReturnStatement with VariableExpression
                def firstStatement = block.statements[0]
                if (firstStatement instanceof ReturnStatement) {
                    ReturnStatement returnStmt = (ReturnStatement) firstStatement
                    if (returnStmt.expression instanceof VariableExpression) {
                        VariableExpression varExpr = (VariableExpression) returnStmt.expression
                        return varExpr.name
                    }
                }
            }

            return null
        } catch (Exception e) {
            logDebug("Error extracting method name from AST: %s", e.message)
            return null
        }
    }

    /**
     * Resolves methods and extracts parameters using existing infrastructure
     */
    private void addParametersFromResolvedMethods(String methodName) {
        // Find methods in current scope instead of creating synthetic calls
        List<MethodNode> methods = findMethodsInScope(methodName)

        if (!methods) {
            logDebug("No methods resolved for: %s", methodName)
            return
        }

        logDebug("Found %d method overloads", methods.size())

        // Process all method parameters
        methods.each { MethodNode method ->
            processMethodParameters(method)
        }
    }

    private void addParametersFromResolvedMethods(ASTNode node) {
        if (node instanceof BlockStatement) {
            BlockStatement block = (BlockStatement) node
            if (!block.statements || block.statements.isEmpty()) return

            ClassNode classType
            // Look for ReturnStatement with VariableExpression
            def firstStatement = block.statements[0]
            if (firstStatement instanceof ReturnStatement) {
                ReturnStatement returnStmt = (ReturnStatement) firstStatement
                if (returnStmt.expression instanceof VariableExpression) {
                    VariableExpression varExpr = (VariableExpression) returnStmt.expression
                    classType = getTypeOf(varExpr)
                }
            }
            if (!classType) return
        }
    }

    /**
     * Find methods by name in current scope using existing utilities
     */
    private List<MethodNode> findMethodsInScope(String methodName) {
        List<MethodNode> foundMethods = []

        // Check current class methods
        request.getCurrentClass()?.methods?.findAll { it.name == methodName }?.each {
            foundMethods.add(it)
        }

        // Check all methods in scope using ScopeHelper
        def scopeItems = ScopeHelper.collectScopeItems(request.offsetNode, request.visitor,
            ScopeHelper.CollectionType.MEMBERS_ONLY)

        scopeItems.members.methods.findAll { it.name == methodName }.each {
            foundMethods.add(it)
        }

        return foundMethods.unique() // Remove duplicates
    }

    /**
     * Process all parameters from a method and add appropriate completions
     */
    private void processMethodParameters(MethodNode method) {
        if (!method.parameters) return

        logDebug("Processing method: %s(%s)", method.name,
            method.parameters.collect { it.name }.join(', '))

        method.parameters.each { Parameter param ->
            addParameterCompletion(param, method)
        }
    }

    /**
     * Add completion for a single parameter - simplified using existing infrastructure
     */
    private void addParameterCompletion(Parameter param, MethodNode method) {
        if (!param) return

        logDebug("Adding parameter completion: %s : %s", param.name, param.type?.nameWithoutPackage)

        // Use the existing infrastructure - automatically handles deduplication and filtering
        request.addCompletion(param)
    }

}
