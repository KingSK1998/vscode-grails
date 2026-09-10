package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile
import org.eclipse.lsp4j.*
import spock.lang.Specification

/**
 * Tests for the FileContentTracker service
 */
class FileContentTrackerSpec extends Specification {

	private GrailsService grailsService
	private FileContentTracker tracker
	private File projectRoot

	def setup() {
		grailsService = new GrailsService()
		projectRoot = new File(System.getProperty("user.dir"), "build/test")
		GrailsProject mockProject = new GrailsProject(name: "test", rootDirectory: projectRoot)
		grailsService.workspaceManager.addProject(mockProject)
		tracker = grailsService.fileTracker
	}

	def cleanup() {
		grailsService?.shutdown()
	}

	def "should track an empty opened document"() {
		given: "An empty editor buffer"
		String uri = "file:///test/Empty.groovy"

		when: "Opening the empty document"
		def file = tracker.didOpenFile(createOpenParam(uri, ""), false)

		then: "The empty buffer remains an active tracked overlay"
		file != null
		file.text == ""
		file.open
		tracker.getTextFile(uri).is(file)
	}

	def "should restore the disk-backed FQCN after an editor overlay closes"() {
		given: "A disk source indexed by the current project"
		File sourceDirectory = new File(projectRoot, "src/main/groovy")
		File diskFile = new File(sourceDirectory, "DiskBacked.groovy")
		diskFile.parentFile.mkdirs()
		diskFile.text = "package sample\nclass DiskBacked {}"
		grailsService.workspaceManager.addProject(new GrailsProject(
			name: "test",
			rootDirectory: projectRoot,
			sourceDirectories: [sourceDirectory] as Set
		))
		tracker.resetFQCNDependencies()
		tracker.getAllProjectFiles()
		assert tracker.getFileFromFQCN("sample.DiskBacked").text.contains("class DiskBacked")

		and: "An unsaved overlay has replaced that URI in the dependency index"
		String uri = diskFile.toURI().toString()
		TextFile overlay = tracker.didOpenFile(
			createOpenParam(uri, "package sample\nclass UnsavedOverlay {}"), false
		)
		tracker.updateFileDependenciesForSourceFile(overlay)
		assert tracker.getFileFromFQCN("sample.DiskBacked").text.contains("UnsavedOverlay")

		when: "The editor overlay closes and background cleanup runs"
		tracker.didCloseFile(createCloseParam(uri))
		tracker.clearClosedFileDependencies(uri)

		then: "The index resolves the source that still exists on disk"
		tracker.getFileFromFQCN("sample.DiskBacked").text.contains("class DiskBacked")
		!tracker.getFileFromFQCN("sample.DiskBacked").text.contains("UnsavedOverlay")
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
	def "should apply sequential incremental changes in array order without sorting"() {
		given: "An open file"
		String uri = "file:///test/Order.groovy"
		String content = "hello world"
		tracker.didOpenFile(createOpenParam(uri, content))

		when: "Applying two sequential edits where edit 2 relies on the intermediate text produced by edit 1"
		// Edit 1 inserts " beautiful" at position (0, 5) -> "hello beautiful world" (length 21)
		// Edit 2 inserts "!" at position (0, 21) -> "hello beautiful world!"
		// In the original text, (0, 21) is out of bounds (length 11). LSP 3.17 requires applying sequentially.
		def changes = [
				new TextDocumentContentChangeEvent(
						new Range(new Position(0, 5), new Position(0, 5)),
						" beautiful"
				),
				new TextDocumentContentChangeEvent(
						new Range(new Position(0, 21), new Position(0, 21)),
						"!"
				)
		]
		tracker.didChangeFile(createChangeParamsWithMultipleChanges(uri, changes))

		then: "Both edits are applied sequentially in protocol order"
		tracker.getTextFile(uri).text == "hello beautiful world!"
	}

	def "should reject obsolete versions within the same open generation"() {
		given: "An open file at version 1"
		String uri = "file:///test/Versioned.groovy"
		tracker.didOpenFile(createOpenParam(uri, "initial content"))

		when: "Applying an update with version 2"
		tracker.didChangeFile(createChangeParam(uri, "version 2 content", 2))

		then: "Version 2 is accepted"
		tracker.getTextFile(uri).text == "version 2 content"
		tracker.getTextFile(uri).version == 2

		when: "Applying an obsolete change with version 1"
		tracker.didChangeFile(createChangeParam(uri, "stale version 1 content", 1))

		then: "The obsolete change is rejected and content is preserved"
		tracker.getTextFile(uri).text == "version 2 content"
		tracker.getTextFile(uri).version == 2

		when: "Applying an update with version 3"
		tracker.didChangeFile(createChangeParam(uri, "version 3 content", 3))

		then: "The newer version is accepted"
		tracker.getTextFile(uri).text == "version 3 content"
		tracker.getTextFile(uri).version == 3
	}

	def "should assign a new open generation on reopen and accept reset versions"() {
		given: "A file opened and advanced to version 5"
		String uri = "file:///test/Reopen.groovy"
		def open1 = tracker.didOpenFile(createOpenParam(uri, "version 1"))
		tracker.didChangeFile(createChangeParam(uri, "version 5", 5))
		long gen1 = open1.openGeneration
		assert gen1 > 0
		assert tracker.getTextFile(uri).version == 5

		when: "Closing the file"
		tracker.didCloseFile(createCloseParam(uri))

		then: "The open generation is cleared"
		tracker.getOpenGeneration(uri) == null

		when: "Reopening the file with version 1"
		def open2 = tracker.didOpenFile(createOpenParam(uri, "reopened version 1"))

		then: "A strictly newer open generation is assigned and reset version 1 is accepted"
		open2.openGeneration > gen1
		tracker.getOpenGeneration(uri) == open2.openGeneration
		tracker.getTextFile(uri).text == "reopened version 1"
	}

	def "should apply incremental insert to empty document"() {
		given: "An empty document"
		String uri = "file:///test/EmptyInsert.groovy"
		tracker.didOpenFile(createOpenParam(uri, ""))

		when: "Inserting text at (0, 0)"
		Range range = new Range(new Position(0, 0), new Position(0, 0))
		tracker.didChangeFile(createChangeParam(uri, "class EmptyInsert {}", 1, range))

		then: "The text is inserted correctly"
		tracker.getTextFile(uri).text == "class EmptyInsert {}"
	}

	def "should handle UTF-16 surrogate pairs in incremental changes"() {
		given: "A document containing a 2-code-unit emoji"
		String uri = "file:///test/Emoji.groovy"
		// "class 😀Test {}": "class " has 6 chars. 😀 (\uD83D\uDE00) has 2 chars (offsets 6-8).
		String content = "class \uD83D\uDE00Test {}"
		tracker.didOpenFile(createOpenParam(uri, content))

		when: "Replacing the emoji at (0, 6) to (0, 8) with 'Happy'"
		Range range = new Range(new Position(0, 6), new Position(0, 8))
		tracker.didChangeFile(createChangeParam(uri, "Happy", 1, range))

		then: "The emoji is replaced accurately"
		tracker.getTextFile(uri).text == "class HappyTest {}"
	}

	def "should permanently delete file via didDeleteFile"() {
		given: "A tracked file"
		String uri = "file:///test/Deleted.groovy"
		tracker.didOpenFile(createOpenParam(uri, "class Deleted {}"))
		assert tracker.getTextFile(uri) != null

		when: "Deleting the file"
		boolean result = tracker.didDeleteFile(uri)

		then: "The file is permanently removed from tracking and open generations"
		result
		tracker.getTextFile(uri) == null
		tracker.getOpenGeneration(uri) == null
	}

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
