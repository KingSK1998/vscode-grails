package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import org.eclipse.lsp4j.ReferenceContext
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import spock.lang.Title

@Title("References Provider Tests")
class GrailsReferenceProviderSpec extends BaseLspSpec {

    GrailsReferenceProvider provider

    def setup() {
        initializeProject(ProjectType.GRAILS, true)
        provider = new GrailsReferenceProvider(grailsService)
    }

    def "should find references for controller action"() {
        given: "A document with a controller action"
        def uri = openExistingFile("TestController.groovy")
        
        when: "Requesting references"
        def result = provider.provideReferences(
            new TextDocumentIdentifier(uri),
            pos(5, 8),
            new ReferenceContext(false)
        ).get()
        
        then: "References are found"
        // TODO: Complete assertions once test project has references
        true
    }
}
