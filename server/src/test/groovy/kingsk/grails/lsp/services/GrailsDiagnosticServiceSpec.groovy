package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.DiagnosticsTestSpec
import kingsk.grails.lsp.test.ProjectType
import org.eclipse.lsp4j.DiagnosticSeverity

/**
 * Tests for the GrailsDiagnosticService
 */
class GrailsDiagnosticServiceSpec extends DiagnosticsTestSpec {
	
	def setup() {
		initializeProject(ProjectType.DUMMY)
	}
	
	def "should report syntax errors in groovy files"() {
		given: "A file with syntax errors"
		String content = """
            package example
            
            class Product {
                String name
                BigDecimal price
                
                def method() {
                    // Missing closing brace
                    if (true) {
                        println "test"
                    // Missing closing brace
                }
        """
		
		when: "Opening the file (diagnostics are automatically published)"
		String uri = openTextDocument("Product.groovy", content)
		def diagnostics = waitForDiagnostics(uri)
		
		then: "Should report syntax errors"
		diagnostics.size() > 0
		// Just verify we get some diagnostics for now
		diagnostics.any { it.severity == DiagnosticSeverity.Error }
	}
	
	def "should report compilation errors"() {
		given: "A file with compilation errors"
		String content = """
            package example
            
            class Customer {
                String name
                UnknownType email  // Unknown type
                
                def method() {
                    undefinedVariable.someMethod()  // Undefined variable
                }
            }
        """
		
		when: "Opening the file (diagnostics are automatically published)"
		String uri = openTextDocument("Customer.groovy", content)
		def diagnostics = waitForDiagnostics(uri)
		
		then: "Should report compilation errors"
		diagnostics.size() > 0
		// Just verify we get some diagnostics
		diagnostics.any { it.message.contains("unable to resolve class") || it.message.contains("undefinedVariable") }
	}
	
	def "should handle valid groovy code without errors"() {
		given: "A valid groovy class"
		String content = """
            package example
            
            class ProductController {
                def index() {
                    render "Product List"
                }
                
                def show(Long id) {
                    def product = findProduct(id)
                    if (!product) {
                        redirect(action: "index")
                    }
                    [product: product]
                }
                
                private findProduct(Long id) {
                    return null // stub implementation
                }
            }
        """
		
		when: "Opening the file (diagnostics are automatically published)"
		String uri = openTextDocument("ProductController.groovy", content)
		def diagnostics = waitForDiagnostics(uri, 2000) // shorter timeout for valid code
		
		then: "Should not report any errors"
		diagnostics.isEmpty() || diagnostics.every { it.severity != DiagnosticSeverity.Error }
	}
}