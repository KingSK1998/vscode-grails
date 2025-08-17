package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.CompletionTestSpec
import kingsk.grails.lsp.test.ProjectType

/**
 * Tests for the Grails-specific completion provider
 */
class GrailsCompletionProviderSpec extends CompletionTestSpec {
	
	def setup() {
		initializeProject(ProjectType.GRAILS)
	}
	
	def "should complete controller actions"() {
		given: "A controller class with a render statement"
		String content = """
            class TestController {
                def index() {
                    render
                }
            }
        """
		String uri = openTextDocument("TestController.groovy", content)
		
		when: "Requesting completions after render"
		def items = getCompletionItems(uri, 2, 20)
		
		then: "Should provide controller-specific completions"
		items.size() > 0
		assertContainsItem(items, "view")
		assertContainsItem(items, "model")
		
		and: "The items should have Grails-specific details"
		def viewItem = assertContainsItem(items, "view")
		viewItem.detail.contains("Grails controller method")
	}
	
	def "should complete GORM methods"() {
		given: "A domain class with a dynamic finder reference"
		String content = """
            class TestDomain {
                String name
                static constraints = {
                    name blank: false
                }
                
                static testFinder() {
                    find
                }
            }
        """
		String uri = openTextDocument("TestDomain.groovy", content)
		
		when: "Requesting completions after find"
		def items = getCompletionItems(uri, 6, 21)
		
		then: "Should provide GORM-specific completions"
		items.size() > 0
		assertContainsItem(items, "findAllByName")
		assertContainsItem(items, "findByName")
		
		and: "The items should have GORM-specific details"
		def finderItem = assertContainsItem(items, "findByName")
		finderItem.detail.contains("GORM dynamic finder")
	}
	
	def "should complete dependency injected services"() {
		given: "A service class"
		String serviceContent = """
            class TestService {
                void serviceMethod() {}
            }
        """
		String serviceUri = openTextDocument("TestService.groovy", serviceContent)
		
		and: "A controller with a service reference"
		String controllerContent = """
            class DIController {
                def test
                
                def index() {
                    test.
                }
            }
        """
		String controllerUri = openTextDocument("DIController.groovy", controllerContent)
		
		when: "Requesting completions after service reference"
		def items = getCompletionItems(controllerUri, 4, 21)
		
		then: "Should provide service-specific completions"
		items.size() > 0
		assertContainsItem(items, "serviceMethod")
		
		and: "The items should have service-specific details"
		def methodItem = assertContainsItem(items, "serviceMethod")
		methodItem.detail.contains("Injected service")
	}
	
	def "should handle incomplete code gracefully"() {
		given: "A class with incomplete code"
		String content = """
            class IncompleteClass {
                void test() {
                    new File().
                }
            }
        """
		String uri = openTextDocument("IncompleteCode.groovy", content)
		
		when: "Requesting completions after incomplete code"
		def items = getCompletionItems(uri, 2, 25)
		
		then: "Should provide reasonable completions"
		items.size() > 5
		assertContainsItem(items, "eachFile")
		assertContainsItem(items, "exists")
	}
	
	def "should return empty completions for invalid positions"() {
		given: "An empty file"
		String uri = openTextDocument("EmptyFile.groovy", "")
		
		when: "Requesting completions at an invalid position"
		def items = getCompletionItems(uri, 0, 0)
		
		then: "Should return empty completions"
		items.isEmpty()
	}
	
	def "should complete within acceptable time"() {
		given: "A complex class with nested blocks"
		String content = """
            class PerformanceTest {
                void testPerf() {
                    (1..10).each {
                        it.times {
                            println "Test"
                        }
                    }
                }
            }
        """
		String uri = openTextDocument("PerfTest.groovy", content)
		
		when: "Requesting completions"
		long start = System.currentTimeMillis()
		getCompletionItems(uri, 3, 25)
		long duration = System.currentTimeMillis() - start
		
		then: "Should complete within acceptable time"
		duration < 500 // Milliseconds
	}
}