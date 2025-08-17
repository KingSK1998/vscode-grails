package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile
import org.eclipse.lsp4j.*
import spock.lang.Specification

/**
 * Tests for the FileContentTracker service
 */
class FileContentTrackerSpec extends Specification {
	
	private FileContentTracker tracker
	
	def setup() {
		def grailsService = new GrailsService()
		grailsService.project = new GrailsProject()
		tracker = new FileContentTracker(grailsService)
	}
	
	def cleanup() {
		tracker = null
	}
	
	def "should track file opening and closing"() {
		given: "A file URI and content"
		String uri = "file:///test/File.groovy"
		String content = "class File {}"
		
		when: "Opening the file"
		def file = tracker.didOpenFile(createOpenParam(uri, content))
		
		then: "The file should be tracked and marked as open"
		file.uri == TextFile.normalizePath(uri)
		file.text == content
		file.open
		
		when: "Closing the file"
		def closed = tracker.didCloseFile(createCloseParam(uri))
		
		then: "The file should be marked as closed"
		closed.closed
		closed == file
	}
	
	def "should apply full content change on unopened file"() {
		given: "A file URI and new content"
		String uri = "file:///test/File.groovy"
		String newContent = "class File { String name }"
		
		when: "Changing the file content"
		def file = tracker.didChangeFile(createChangeParam(uri, newContent))
		
		then: "The file should have the new content but not be marked as open"
		file.text == newContent
		!file.open
	}
	
	def "should apply full document replacement"() {
		given: "An open file"
		String uri = "file:///test/File.groovy"
		String content = "class File {}"
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Replacing the entire content"
		String newContent = "class File { void method() {} }"
		tracker.didChangeFile(createChangeParam(uri, newContent, 2))
		
		then: "The file content should be updated"
		tracker.getTextFile(uri).text == newContent
	}
	
	def "should apply incremental change to append method"() {
		given: "An open file"
		String uri = "file:///test/File.groovy"
		String content = "class File {}"
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Appending a method"
		String addition = "\n    void hello() {}\n"
		Range range = new Range(new Position(0, 12), new Position(0, 12))
		tracker.didChangeFile(createChangeParam(uri, addition, 1, range))
		
		then: "The method should be appended correctly"
		tracker.getTextFile(uri).text == content.substring(0, 12) + addition + "}"
	}
	
	def "should delete content via range"() {
		given: "An open file with content"
		String uri = "file:///test/File.groovy"
		String content = "class File { void method() {} }"
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Deleting a range of content"
		Range range = new Range(new Position(0, 13), new Position(0, 30))
		tracker.didChangeFile(createChangeParam(uri, "", 2, range))
		
		then: "The content should be deleted"
		tracker.getTextFile(uri).text == "class File { }"
	}
	
	def "should ignore empty content changes"() {
		given: "An open file"
		String uri = "file:///test/File.groovy"
		String content = "class File {}"
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Making an empty change"
		Range range = new Range(new Position(0, 0), new Position(0, 0))
		tracker.didChangeFile(createChangeParam(uri, "", 3, range))
		
		then: "The content should remain unchanged"
		tracker.getTextFile(uri).text == content
	}
	
	def "should apply multiple incremental changes correctly"() {
		given: "An open file"
		String uri = "file:///test/File.groovy"
		String content = "class File {}"
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Making multiple changes"
		def changes = [
				new TextDocumentContentChangeEvent(
						new Range(new Position(0, 12), new Position(0, 12)),
						"\n  String name\n"
				),
				new TextDocumentContentChangeEvent(
						new Range(new Position(0, 6), new Position(0, 10)),
						"MyClass"
				)
		]
		
		tracker.didChangeFile(createChangeParamsWithMultipleChanges(uri, changes))
		
		then: "All changes should be applied correctly"
		tracker.getTextFile(uri).text == "class MyClass {\n  String name\n}"
	}
	
	def "should resolve dependencies between files"() {
		given: "Two related files"
		String uriA = "file:///test/ClassA.groovy"
		String contentA = "class ClassA {}"
		
		String uriB = "file:///test/ClassB.groovy"
		String contentB = "class ClassB extends ClassA {}"
		
		when: "Opening both files"
		def fileA = tracker.didOpenFile(createOpenParam(uriA, contentA))
		def fileB = tracker.didOpenFile(createOpenParam(uriB, contentB))
		
		then: "Dependencies should be correctly identified"
		!tracker.getFileAndItsDependencies(fileA).contains(fileB)
		tracker.getFileAndItsDependencies(fileB).contains(fileA)
	}
	
	def "should insert correctly at start of line with CRLF endings"() {
		given: "A file with CRLF line endings"
		String uri = "file:///test/File.groovy"
		StringBuilder builder = new StringBuilder()
		builder.append("class Completion {\r\n")
		builder.append("  public Completion() {\r\n")
		builder.append("    String localVar\r\n")
		builder.append("    localVar.\r\n")
		builder.append("  }\r\n")
		builder.append("}")
		String content = builder.toString()
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Inserting a comment at the start of a line"
		String insertion = "// injected comment\r\n"
		Range range = new Range(new Position(3, 0), new Position(3, 0))
		tracker.didChangeFile(createChangeParam(uri, insertion, 5, range))
		
		then: "The comment should be inserted correctly"
		StringBuilder expected = new StringBuilder()
		expected.append("class Completion {\r\n")
		expected.append("  public Completion() {\r\n")
		expected.append("    String localVar\r\n")
		expected.append("// injected comment\r\n")
		expected.append("    localVar.\r\n")
		expected.append("  }\r\n")
		expected.append("}")
		tracker.getTextFile(uri).text == expected.toString()
	}
	
	def "should delete content across CRLF boundary correctly"() {
		given: "A file with CRLF line endings"
		String uri = "file:///test/File.groovy"
		StringBuilder builder = new StringBuilder()
		builder.append("class Completion {\r\n")
		builder.append("  def foo = 1\r\n")
		builder.append("  def bar = 2\r\n")
		builder.append("}")
		String content = builder.toString()
		tracker.didOpenFile(createOpenParam(uri, content))
		
		when: "Deleting content across a line boundary"
		Range range = new Range(new Position(1, 13), new Position(2, 13))
		tracker.didChangeFile(createChangeParam(uri, "", 2, range))
		
		then: "The content should be deleted correctly"
		StringBuilder expected = new StringBuilder()
		expected.append("class Completion {\r\n")
		expected.append("  def foo = 1\r\n")
		expected.append("}")
		tracker.getTextFile(uri).text == expected.toString()
	}
	
	//	def "should handle file with mixed line endings"() {
	//		given: "A file with mixed line endings"
	//		String uri = "file:///test/MixedEndings.groovy"
	//		String content = "class MixedEndings {\n  def method1() {}\r\n  def method2() {}\n}"
	//		tracker.didOpenFile(createOpenParam(uri, content))
	//
	//		when: "Inserting content between lines with different endings"
	//		String insertion = "  def newMethod() {}\n"
	//		Range range = new Range(new Position(1, 17), new Position(1, 17))
	//		tracker.didChangeFile(createChangeParam(uri, insertion, 1, range))
	//
	//		then: "The content should be inserted correctly"
	//		String expected = "class MixedEndings {\n  def method1() {}\r\n  def newMethod() {}\n  def method2() {}\n}"
	//		tracker.getTextFile(uri).text == expected
	//	}
	
	//================= Utility Methods ==================
	private static def createOpenParam(String uri, String content) {
		return new DidOpenTextDocumentParams(doc(uri, content))
	}
	
	private static def createChangeParam(String uri, String newContent, int version = 1, Range range = null) {
		return new DidChangeTextDocumentParams(vdoc(uri, version), [event(range, newContent)])
	}
	
	private static def vdoc(String uri, int version) {
		return new VersionedTextDocumentIdentifier(uri, version)
	}
	
	private static def createChangeParamsWithMultipleChanges(String uri, List<TextDocumentContentChangeEvent> changes) {
		return new DidChangeTextDocumentParams(vdoc(uri, 1), changes)
	}
	
	private static def event(Range range, String content) {
		return new TextDocumentContentChangeEvent(range, content)
	}
	
	private static def createCloseParam(String uri) {
		return new DidCloseTextDocumentParams(idf(uri))
	}
	
	private static def idf(String uri) {
		return new TextDocumentIdentifier(uri)
	}
	
	private static def doc(String uri, String content) {
		return new TextDocumentItem(uri, "groovy", 0, content)
	}
	
}