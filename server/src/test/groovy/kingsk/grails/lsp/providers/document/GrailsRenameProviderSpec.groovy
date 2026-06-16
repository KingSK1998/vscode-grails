package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import spock.lang.Title

@Title("Rename Provider Tests")
class GrailsRenameProviderSpec extends BaseLspSpec {

    GrailsRenameProvider provider

    def setup() {
        initializeProject(ProjectType.GRAILS, true)
        provider = grailsService.providerRegistry.getProvider(GrailsRenameProvider)
    }

    def "should rename controller action"() {
        given: "A document with a controller action"
        def uri = openExistingFile("TestController.groovy")
        
        when: "Requesting rename"
        def result = provider.provideRename(new RenameParams(
            new TextDocumentIdentifier(uri),
            pos(5, 8),
            "newActionName"
        )).get()
        
        then: "Workspace edits are returned"
        // TODO: Complete assertions
        true
    }
}
