package kingsk.grails.lsp.core.compiler

import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.eclipse.lsp4j.Position
import spock.lang.Title

/**
 * Incremental Compilation Integration Tests
 *
 * Verifies that:
 *  1. AST is correctly updated after an incremental edit.
 *  2. Old nodes are evicted from the visitor for the changed file.
 *  3. Grails artifact cache (ASTService) is evicted and rebuilt after edit.
 *  4. Cross-file symbol caches are cleared so completions don't go stale.
 *  5. Diagnostics are published for the correct URI after a change.
 *  6. Syntax errors do NOT corrupt the global index (last-known-good retained).
 */
@Title("Incremental Compilation Correctness Tests")
class GrailsIncrementalCompilerSpec extends BaseLspSpec {

    def setup() {
        // Initialize project without full compile — incremental-only mode
        initializeProject(ProjectType.GRAILS, false)
    }

    // ─── 1. AST reflects updated content ─────────────────────────────────────

    def "AST contains new method after incremental edit"() {
        given: "Initial controller is compiled"
        String initial = '''
            package com.example
            class TestController {
                def index() { "hello" }
            }
        '''.stripIndent()
        String uri = openTextDocument("TestController.groovy", initial)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)

        and: "New action is added"
        String updated = '''
            package com.example
            class TestController {
                def index()    { "hello" }
                def newAction() { "new" }
            }
        '''.stripIndent()
        replaceTextDocument(uri, updated)
        TextFile updatedFile = grailsService.fileTracker.getTextFile(uri)

        when: "Incremental compilation runs"
        grailsService.compiler.markDirty(updatedFile.uri)
        grailsService.compileAndVisitAST(updatedFile)

        then: "ClassNode for the file includes newAction"
        Set<ClassNode> classNodes = grailsService.visitor.getClassNodes(uri)
        classNodes
        ClassNode controllerNode = classNodes.find { it.nameWithoutPackage == 'TestController' }
        controllerNode
        controllerNode.methods.any { it.name == 'newAction' }
    }

    // ─── 2. Old method nodes no longer present after rename ──────────────────

    def "AST evicts old method nodes after incremental rename"() {
        given: "File compiled with oldAction"
        String initial = '''
            package com.example
            class TestController {
                def oldAction() { "old" }
            }
        '''.stripIndent()
        String uri = openTextDocument("TestController.groovy", initial)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)

        and: "oldAction renamed to renamedAction"
        String updated = '''
            package com.example
            class TestController {
                def renamedAction() { "renamed" }
            }
        '''.stripIndent()
        replaceTextDocument(uri, updated)
        TextFile updatedFile = grailsService.fileTracker.getTextFile(uri)

        when: "Incremental recompilation"
        grailsService.compiler.markDirty(updatedFile.uri)
        grailsService.compileAndVisitAST(updatedFile)

        then: "Visitor classNodes reflect only renamedAction — oldAction must be gone"
        Set<ClassNode> classNodes = grailsService.visitor.getClassNodes(uri)
        ClassNode controllerNode = classNodes?.find { it.nameWithoutPackage == 'TestController' }
        controllerNode
        !controllerNode.methods.any { it.name == 'oldAction' }
        controllerNode.methods.any { it.name == 'renamedAction' }
    }

    // ─── 3. ASTService artifact cache evicted and rebuilt ────────────────────

    def "ASTService controller cache is evicted and rebuilt after incremental edit"() {
        given: "Controller compiled first time"
        String initial = '''
            package com.example.controllers
            class BookController {
                def index() {}
            }
        '''.stripIndent()
        String uri = openTextDocument("BookController.groovy", initial)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)

        and: "ASTService captured the artifact on first compile"
        def controllersAfterFirst = grailsService.astService.grailsControllers[uri]
        assert controllersAfterFirst : "Expected ASTService to detect BookController after first compile"

        when: "File changes — class renamed to no longer match controller naming convention"
        String updated = '''
            package com.example.controllers
            class PlainHelper {
                static String help() { "helper" }
            }
        '''.stripIndent()
        replaceTextDocument(uri, updated)
        TextFile updatedFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compiler.markDirty(updatedFile.uri)
        grailsService.compileAndVisitAST(updatedFile)

        then: "Old controller entry for this URI is evicted from ASTService"
        def controllersAfterEdit = grailsService.astService.grailsControllers[uri]
        !controllersAfterEdit
    }

    // ─── 4. Cross-file completion cache cleared after edit ───────────────────

    def "Completion caches are cleared globally after any incremental edit"() {
        given: "File is compiled"
        String content = '''
            package com.example
            class HomeController {
                def index() {}
            }
        '''.stripIndent()
        String uri = openTextDocument("HomeController.groovy", content)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)

        when: "A different, unrelated method is added"
        String updated = '''
            package com.example
            class HomeController {
                def index()   {}
                def dashboard() {}
            }
        '''.stripIndent()
        replaceTextDocument(uri, updated)
        TextFile updatedFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compiler.markDirty(updatedFile.uri)
        grailsService.compileAndVisitAST(updatedFile)

        then: "No exception thrown — clearCrossFileCaches ran without error"
        // Indirect assertion: the visitor is not empty, and the compilation succeeded
        !grailsService.visitor.isEmpty()
        !grailsService.compiler.errorCollectorOrNull?.hasErrors()
    }

    // ─── 5. Diagnostics published for the correct URI ────────────────────────

    def "Diagnostics are published for the changed URI after incremental recompile"() {
        given: "Valid controller compiled"
        String valid = '''
            package com.example
            class MakeController {
                def list() { [makes: Make.list()] }
            }
        '''.stripIndent()
        String uri = openTextDocument("MakeController.groovy", valid)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)
        mockClient.clear()

        when: "Syntax error introduced"
        String broken = '''
            package com.example
            class MakeController {
                def list() {
                    // unclosed if
                    if (true {
                        render "x"
                    }
                }
            }
        '''.stripIndent()
        replaceTextDocument(uri, broken)
        TextFile brokenFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compiler.markDirty(brokenFile.uri)
        grailsService.compileAndVisitAST(brokenFile)

        then: "Diagnostics were published for the correct URI"
        mockClient.diagnostics
        mockClient.diagnostics.any { it.uri == TextFile.normalizePath(uri) }
    }

    // ─── 6. Syntax errors do not corrupt the global class index ─────────────

    def "Syntax error in one file does not evict class nodes for other files"() {
        given: "Two files compiled — HomeController (healthy) and ErrorController"
        String healthyContent = '''
            package com.example
            class HomeController {
                def index() {}
            }
        '''.stripIndent()
        String healthyUri = openTextDocument("HomeController.groovy", healthyContent)
        TextFile healthyFile = grailsService.fileTracker.getTextFile(healthyUri)
        grailsService.compileAndVisitAST(healthyFile)

        String brokenContent = '''
            package com.example
            class ErrorController {
                def index() {
                    if (true {
                        render "bad"
                    }
                }
            }
        '''.stripIndent()
        String brokenUri = openTextDocument("ErrorController.groovy", brokenContent)
        TextFile brokenFile = grailsService.fileTracker.getTextFile(brokenUri)

        when: "Broken file is compiled incrementally"
        grailsService.compiler.markDirty(brokenFile.uri)
        grailsService.compileAndVisitAST(brokenFile)

        then: "HomeController's class nodes are still present in the visitor"
        Set<ClassNode> healthyNodes = grailsService.visitor.getClassNodes(healthyUri)
        healthyNodes
        healthyNodes.any { it.nameWithoutPackage == 'HomeController' }
    }

    // ─── 7. Position-based lookup returns updated node after edit ────────────

    def "getNodeAtPosition returns a node at the method declaration line after incremental edit"() {
        given: "File compiled"
        String content = '''
            package com.example
            class HomeController {
                def index() { render "home" }
            }
        '''.stripIndent()
        String uri = openTextDocument("HomeController.groovy", content)
        TextFile textFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compileAndVisitAST(textFile)

        when: "Method body updated"
        String updated = '''
            package com.example
            class HomeController {
                def index() { render "updated" }
            }
        '''.stripIndent()
        replaceTextDocument(uri, updated)
        TextFile updatedFile = grailsService.fileTracker.getTextFile(uri)
        grailsService.compiler.markDirty(updatedFile.uri)
        grailsService.compileAndVisitAST(updatedFile)

        then: "AST node is found at line 3 (class body), column 5"
        def node = grailsService.visitor.getNodeAtLineAndColumn(uri, 3, 5)
        node
    }
}
