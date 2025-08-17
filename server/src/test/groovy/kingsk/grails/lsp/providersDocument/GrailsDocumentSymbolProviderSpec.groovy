package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.DocumentSymbolTestSpec
import kingsk.grails.lsp.test.ProjectType
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.TextDocumentIdentifier

class GrailsDocumentSymbolProviderSpec extends DocumentSymbolTestSpec {
	
	def setup() {
		initializeProject(ProjectType.GROOVY)
	}
	
	def "should open a single file and retrieve document symbols"() {
		given: "A virtual source file with content"
		String content = """
            class DocumentSymbol {
                def testMethod() {
                    println "This is a test method from Test"
                }
            }
        """
		String uri = openTextDocument("DocumentSymbol.groovy", content)
		
		when: "Requesting document symbols"
		def result = getDocumentSymbol(uri)
		
		then: "Document symbols should include the class and method"
		assertClassSymbol(result, "DocumentSymbol")
		assertMethodSymbol(result, "testMethod")
	}
	
	def "should handle file changes"() {
		given: "A virtual source file with content"
		String content = """
            class ChangeTest {
                def originalMethod() {
                    println "Original method"
                }
            }
        """
		String uri = openTextDocument("ChangeTest.groovy", content)
		
		when: "Changing the file content"
		String newContent = """
            class ChangeTest {
                def originalMethod() {
                    println "Original method"
                }
                
                def newMethod() {
                    println "New method"
                }
            }
        """
		replaceTextDocument(uri, newContent, 2)
		
		and: "Requesting updated document symbols"
		def symbol = getDocumentSymbol(uri)
		
		then: "Document symbols should reflect the changes"
		assertClassSymbol(symbol, "ChangeTest")
		assertMethodSymbol(symbol, "originalMethod")
		assertMethodSymbol(symbol, "newMethod")
		symbol.children.size() == 2
	}
	
	def "should handle file closure gracefully"() {
		given: "A virtual source file with content"
		String content = """
            class CloseTest {
                def testMethod() {
                    println "Test method"
                }
            }
        """
		String uri = openTextDocument("CloseTest.groovy", content)
		
		when: "Closing the file"
		closeTextDocument(uri)
		
		and: "Attempting to get document symbols after closure"
		def exception = null
		try {
			waitForFuture(
					grailsService.document.documentSymbol(
							new DocumentSymbolParams(new TextDocumentIdentifier(uri))
					)
			)
		} catch (Exception e) {
			exception = e
		}
		
		then: "Should not crash and handle gracefully"
		exception == null || exception.message?.contains("not found") || exception.message?.contains("closed")
	}
	
	def "should include synthetic Grails fields in controller symbols"() {
		given: "A Grails controller with no explicit fields"
		initializeProject(ProjectType.GRAILS, true)
		String content = """package demo

class HomeController {
    def index() {
        render view: "index"
    }
}"""
		// String uri = openTextDocument("SampleController.groovy", content)
		String uri = openTextDocument("HomeController.groovy", content)
		
		when: "Requesting document symbols"
		def symbol = getDocumentSymbol(uri)
		
		then: "Should include the synthetic fields like 'params', 'request', 'response'"
		assertClassSymbol(symbol, "HomeController")
		assertMethodSymbol(symbol, "index")
		assertPropertySymbol(symbol, "PARAMS")
		assertPropertySymbol(symbol, "REQUEST")
		assertPropertySymbol(symbol, "RESPONSE")
	}
	
}