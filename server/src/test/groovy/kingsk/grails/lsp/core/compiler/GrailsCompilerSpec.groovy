package kingsk.grails.lsp.core.compiler

import kingsk.grails.lsp.model.TextFile
import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import spock.lang.Narrative
import spock.lang.Title

/**
 * Tests for the GrailsCompiler
 */
@Title("Grails Compiler Tests")
@Narrative("""
    These tests verify that the Grails compiler correctly compiles
    various Grails artifacts without errors.
""")
class GrailsCompilerSpec extends BaseLspSpec {
	
	def setup() {
		initializeProject(ProjectType.GRAILS, false)
	}
	
	def "[COMPILER] should compile HomeController.groovy without errors"() {
		given: "A HomeController source file"
		String content = """
            package com.example
            
            class HomeController {
                def index() {
                    render view: "index"
                }
            }
        """
		String uri = openTextDocument("HomeController.groovy", content)
		TextFile homeController = grailsService.fileTracker.getTextFile(uri)
		
		when: "Compiling the source file"
		grailsService.compiler.compileSourceFile(homeController)
		
		then: "There should be no compilation errors"
		grailsService.compiler.errorCollectorOrNull != null
		!grailsService.compiler.errorCollectorOrNull.hasErrors()
	}
	
	def "[COMPILER] should compile UrlMappings.groovy without errors"() {
		given: "A UrlMappings source file"
		String content = """
            package com.example
            
            class UrlMappings {
                static mappings = {
                    "/\$controller/\$action?/\$id?(.format)?" {
                        constraints {
                            // apply constraints here
                        }
                    }
                    
                    "/"(view: "/index")
                    "500"(view: '/error')
                    "404"(view: '/notFound')
                }
            }
        """
		String uri = openTextDocument("UrlMappings.groovy", content)
		TextFile urlMappings = grailsService.fileTracker.getTextFile(uri)
		
		when: "Compiling the source file"
		grailsService.compiler.compileSourceFile(urlMappings)
		
		then: "There should be no compilation errors"
		grailsService.compiler.errorCollectorOrNull != null
		!grailsService.compiler.errorCollectorOrNull.hasErrors()
	}
	
	def "[COMPILER] should handle incremental compilation"() {
		given: "A VehicleController source file"
		String content = """
            package com.example
            
            class VehicleController {
                def index() {
                    [vehicles: Vehicle.list()]
                }
                
                def show(Long id) {
                    respond Vehicle.get(id)
                }
            }
        """
		String uri = openTextDocument("VehicleController.groovy", content)
		TextFile vehicleController = grailsService.fileTracker.getTextFile(uri)
		
		when: "Compiling the source file"
		grailsService.compiler.compileSourceFile(vehicleController)
		
		and: "Getting the source unit"
		def unit = grailsService.compiler.getSourceUnit(vehicleController)
		
		then: "There should be no compilation errors"
		grailsService.compiler.errorCollectorOrNull != null
		!grailsService.compiler.errorCollectorOrNull.hasErrors()
		
		and: "The source unit should be available"
		unit != null
	}
	
	def "[COMPILER] should compile BookController.groovy without errors"() {
		given: "A BookController source file"
		String content = """
            package com.example
            
            class BookController {
                def index() {
                    [books: Book.list()]
                }
                
                def show(Long id) {
                    respond Book.get(id)
                }
                
                def create() {
                    respond new Book(params)
                }
                
                def save(Book book) {
                    book.save flush: true
                    redirect action: "show", id: book.id
                }
            }
        """
		String uri = openTextDocument("BookController.groovy", content)
		TextFile bookController = grailsService.fileTracker.getTextFile(uri)
		
		when: "Compiling the source file"
		grailsService.compiler.compileSourceFile(bookController)
		
		then: "There should be no compilation errors"
		grailsService.compiler.errorCollectorOrNull != null
		!grailsService.compiler.errorCollectorOrNull.hasErrors()
	}
	
	def "[COMPILER] should detect compilation errors"() {
		given: "A source file with syntax errors"
		String content = """
            package com.example
            
            class ErrorController {
                def index() {
                    // Missing closing brace
                    if (true {
                        render "Hello"
                    }
                }
            }
        """
		String uri = openTextDocument("ErrorController.groovy", content)
		TextFile errorController = grailsService.fileTracker.getTextFile(uri)
		
		when: "Compiling the source file"
		grailsService.compiler.compileSourceFile(errorController)
		
		then: "There should be compilation errors"
		grailsService.compiler.errorCollectorOrNull != null
		grailsService.compiler.errorCollectorOrNull.hasErrors()
	}
}