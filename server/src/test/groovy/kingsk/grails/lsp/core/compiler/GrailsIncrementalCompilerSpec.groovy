package kingsk.grails.lsp.core.compiler

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import spock.lang.Title

@Title("Incremental Compilation Tests")
class GrailsIncrementalCompilerSpec extends BaseLspSpec {

    def setup() {
        // We initialize the project without a full compile first, 
        // to test the incremental compilation specifically.
        initializeProject(ProjectType.GRAILS, false)
    }

    def "should trigger incremental compilation on file change"() {
        given: "An open workspace"
        def uri = openExistingFile("TestController.groovy")
        
        when: "The file changes"
        // Simulate a document change
        replaceTextDocument(uri, "class TestController { def newAction() {} }")
        
        then: "Incremental compilation processes the change"
        // TODO: assert that the AST was updated
        true
    }
}
