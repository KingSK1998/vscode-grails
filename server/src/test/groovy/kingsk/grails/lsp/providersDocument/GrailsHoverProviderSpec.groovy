package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.TextDocumentIdentifier

/**
 * Tests for GrailsHoverProvider functionality
 */
class GrailsHoverProviderSpec extends BaseLspSpec {
	
	def setup() {
		initializeProject(ProjectType.GRAILS, false)
	}
	
	def "should provide hover information for Grails controller class"() {
		given: "A HomeController file"
		String content = """package demo

class HomeController {
    def index() {
        render view: "index"
    }
}"""
		String uri = openTextDocument("HomeController.groovy", content)
		
		when: "Requesting hover information for class name"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(2, 6))
		// Position on "HomeController"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("HomeController")
	}
	
	def "should provide hover information for Grails service class"() {
		given: "A BookService file"
		String content = """package demo

class BookService {
    def getBook(Long id) {
        return Book.get(id)
    }
}"""
		String uri = openTextDocument("BookService.groovy", content)
		
		when: "Requesting hover information for service class"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(2, 6))
		// Position on "BookService"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("BookService")
	}
	
	def "should provide hover information for Grails domain class"() {
		given: "A Book domain class"
		String content = """package demo

class Book {
    String title
    String author
    Date dateCreated
    
    static constraints = {
        title blank: false
        author blank: false
    }
}"""
		String uri = openTextDocument("Book.groovy", content)
		
		when: "Requesting hover information for domain class"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(2, 6))
		// Position on "Book"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("Book")
	}
	
	def "should provide hover information for method"() {
		given: "A service with a method"
		String content = """package demo

class BookService {
    /**
     * Gets a book by its ID
     * @param id the book ID
     * @return the book instance
     */
    def getBook(Long id) {
        return Book.get(id)
    }
}"""
		String uri = openTextDocument("BookService.groovy", content)
		
		when: "Requesting hover information for method"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(8, 8))
		// Position on "getBook"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("getBook")
	}
	
	def "should provide hover information for property"() {
		given: "A domain class with properties"
		String content = """package demo

class Book {
    String title
    String author
    Date dateCreated
}"""
		String uri = openTextDocument("Book.groovy", content)
		
		when: "Requesting hover information for property"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(3, 11))
		// Position on "title"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("title")
	}
	
	def "should return null for invalid position"() {
		given: "A simple class"
		String content = """package demo

class TestClass {
}"""
		String uri = openTextDocument("TestClass.groovy", content)
		
		when: "Requesting hover information for empty space"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(10, 50))
		// Invalid position
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should return null"
		result == null
	}
	
	def "should handle Groovy-specific syntax in hover"() {
		given: "A Groovy class with closures"
		String content = """package demo

class GroovyService {
    def processItems = { items ->
        items.each { item ->
            println item
        }
    }
}"""
		String uri = openTextDocument("GroovyService.groovy", content)
		
		when: "Requesting hover information for closure property"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(3, 8))
		// Position on "processItems"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide hover information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight() != null
		result.contents.getRight().value != null
	}
	
	def "should provide Grails-specific documentation for controller"() {
		given: "A controller with Grails features"
		String content = """package demo

class UserController {
    static allowedMethods = [save: "POST", update: "PUT"]
    static defaultAction = "list"
    
    def list() {
        [users: User.list()]
    }
}"""
		String uri = openTextDocument("UserController.groovy", content)
		
		when: "Requesting hover information for controller"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(2, 6))
		// Position on "UserController"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide Grails-specific information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("UserController")
	}
	
	def "should provide Grails-specific documentation for service"() {
		given: "A transactional service"
		String content = """package demo

import grails.gorm.transactions.Transactional

@Transactional
class UserService {
    def saveUser(User user) {
        user.save(flush: true)
    }
}"""
		String uri = openTextDocument("UserService.groovy", content)
		
		when: "Requesting hover information for service"
		def textDocument = new TextDocumentIdentifier(uri)
		def hoverParams = new HoverParams(textDocument, pos(5, 6))
		// Position on "UserService"
		def result = waitForFuture(grailsService.document.hover(hoverParams))
		
		then: "Should provide Grails-specific information"
		result != null
		result.contents != null
		result.contents.right
		result.contents.getRight().value != null
		result.contents.getRight().value.contains("UserService")
	}
}