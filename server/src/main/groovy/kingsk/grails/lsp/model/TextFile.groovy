package kingsk.grails.lsp.model

import groovy.transform.EqualsAndHashCode

@EqualsAndHashCode(excludes = ['version', 'fileState'])
class TextFile {
	String uri
	String text
	String name
	String nameWithoutExtension
	FileState fileState
	int version = 0
	// text document version (open, change, delete)
	
	private TextFile(String uri, String text) {
		this.uri = uri
		this.text = text
		this.name = extractFileName(uri)
		this.nameWithoutExtension = extractNameWithoutExtension(name)
		this.fileState = FileState.OPENED
	}
	
	private TextFile() {}
	
	static TextFile createForTest(String uri, String text) {
		return new TextFile().tap {
			it.name = extractFileName(uri)
			it.uri = uri
			it.updateText(text)
			it.nameWithoutExtension = extractNameWithoutExtension(uri)
			it.fileState = FileState.OPENED
		}
	}
	
	static TextFile create(String uri, String text) {
		if (!uri) throw new IllegalArgumentException("URI cannot be null or empty")
		return new TextFile(normalizePath(uri), text)
	}
	
	static String extractFileName(String uri) {
		return (uri as File).name
	}
	
	static String normalizePath(String path) {
		if (!path) return null
		if (path.startsWith('file:')) {
			// Handle URI paths
			return new File(new URI(path)).canonicalPath
		}
		// Handle direct file paths
		return new File(path).canonicalPath
	}
	
	static String extractNameWithoutExtension(String name) {
		int idx = name.lastIndexOf('.')
		return (idx >= 0) ? name.substring(0, idx) : name
	}
	
	void markOpened() { fileState = FileState.OPENED }
	
	void markChanged() { fileState = FileState.CHANGED }
	
	void markClosed() { fileState = FileState.CLOSED }
	
	boolean isOpen() { return fileState == FileState.OPENED }
	
	boolean isChanged() { return fileState == FileState.CHANGED }
	
	boolean isClosed() { return fileState == FileState.CLOSED }
	
	void updateText(String newText) {
		this.text = newText
		this.fileState = FileState.CHANGED
	}
	
	void close() {
		this.fileState = FileState.CLOSED
	}
	
	File toFile() {
		return uri ? new File(uri) : null
	}
	
	String textAtLine(int lineIndex) {
		if (lineIndex < 0 || !text) return ""
		def lines = text.readLines()
		if (lineIndex >= lines.size()) return ""
		return lines.get(lineIndex)
	}
	
	@Override
	String toString() {
		return "FileName: $name -- Size: ${text?.length() ?: 0} -- State: $fileState"
	}
}
