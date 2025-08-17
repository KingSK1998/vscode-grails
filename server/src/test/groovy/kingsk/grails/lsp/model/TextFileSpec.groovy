package kingsk.grails.lsp.model

import spock.lang.Narrative
import spock.lang.Specification
import spock.lang.Title

/**
 * Tests for the TextFile model class
 */
@Title("TextFile Model Tests")
@Narrative("""
    These tests verify that the TextFile model correctly handles file URIs,
    text content, and validation.
""")
class TextFileSpec extends Specification {
	
	def "should create TextFile with valid uri and text"() {
		given: "A valid URI and text content"
		String uri = "file:///path/to/file.txt"
		String text = "Sample text content"
		
		when: "Creating a TextFile"
		TextFile textFile = TextFile.create(uri, text)
		
		then: "The TextFile should have the correct properties"
		textFile.uri == "D:\\path\\to\\file.txt"
		textFile.name == "file.txt"
		textFile.nameWithoutExtension == "file"
		textFile.text == text
		textFile.fileState == FileState.OPENED
	}
	
	def "should throw IllegalArgumentException when uri is null"() {
		when: "Creating a TextFile with null URI"
		TextFile.create(null, "Some text")
		
		then: "An IllegalArgumentException should be thrown"
		def exception = thrown(IllegalArgumentException)
		exception.message == "URI cannot be null or empty"
	}
	
	def "should throw IllegalArgumentException when uri is empty"() {
		when: "Creating a TextFile with empty URI"
		TextFile.create("", "Some text")
		
		then: "An IllegalArgumentException should be thrown"
		def exception = thrown(IllegalArgumentException)
		exception.message == "URI cannot be null or empty"
	}
	
	def "should handle file path with special characters"() {
		given: "A URI with special characters"
		String uri = "file:///path/to/file%20with%20spaces.txt"
		String text = "Content with special characters: !@#\$%^&*()"
		
		when: "Creating a TextFile"
		TextFile textFile = TextFile.create(uri, text)
		
		then: "The TextFile should handle the special characters correctly"
		textFile.name == "file with spaces.txt"
		textFile.nameWithoutExtension == "file with spaces"
		textFile.text == text
	}
	
	def "should update text content"() {
		given: "A TextFile with initial content"
		TextFile textFile = TextFile.create("file:///path/to/file.txt", "Initial content")
		
		when: "Updating the text content"
		textFile.updateText("Updated content")
		
		then: "The text content should be updated"
		textFile.text == "Updated content"
		textFile.fileState == FileState.CHANGED
	}
	
	def "should close file"() {
		given: "An open TextFile"
		TextFile textFile = TextFile.create("file:///path/to/file.txt", "Content")
		
		when: "Closing the file"
		textFile.close()
		
		then: "The file state should be CLOSED"
		textFile.fileState == FileState.CLOSED
	}
}