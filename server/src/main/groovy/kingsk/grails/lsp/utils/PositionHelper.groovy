package kingsk.grails.lsp.utils

import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.messages.WarningMessage
import org.eclipse.lsp4j.Position

@CompileStatic
class PositionHelper {
	
	static final Comparator<Position> COMPARATOR = Comparator
			.comparingInt { Position p -> p.line }
			.thenComparingInt { Position p -> p.character }
	
	static int compareStartPositions(ASTNode a, ASTNode b) {
		def aStart = ASTUtils.astNodeToRange(a)?.start ?: zeroPosition()
		def bStart = ASTUtils.astNodeToRange(b)?.start ?: zeroPosition()
		return COMPARATOR.reversed().compare(aStart, bStart)
	}
	
	static int compareEndPositions(ASTNode a, ASTNode b) {
		def aEnd = ASTUtils.astNodeToRange(a)?.end ?: zeroPosition()
		def bEnd = ASTUtils.astNodeToRange(b)?.end ?: zeroPosition()
		return COMPARATOR.compare(aEnd, bEnd)
	}
	
	static boolean isValidPosition(Position p) {
		return p?.line >= 0 && p?.character >= 0
	}
	
	static Position zeroPosition() {
		return new Position(0, 0)
	}
	
	static Position warningToPosition(WarningMessage warning) {
		if (!warning?.context) return null
		return fromGroovyPosition(warning.context.startLine, warning.context.startColumn)
	}
	
	/**
	 * Converts a Groovy position to a LSP position.
	 * Groovy uses 1-based line/column numbers, LSP uses 0-based.
	 * @param groovyLine Groovy line number (1-based, or 0 for first line, -1 for invalid)
	 * @param groovyColumn Groovy column number (1-based)
	 * @return LSP Position (0-based) or null if invalid
	 */
	static Position fromGroovyPosition(int groovyLine, int groovyColumn) {
		if (groovyLine < 0) return null
		
		// Handle Groovy position conversion:
		// - Groovy line 0 stays as LSP line 0
		// - Groovy line 1+ becomes LSP line (groovyLine - 1)
		// - Groovy column 0 stays as LSP column 0  
		// - Groovy column 1+ becomes LSP column (groovyColumn - 1)
		int lspLine = (groovyLine == 0) ? 0 : groovyLine - 1
		int lspColumn = (groovyColumn == 0) ? 0 : groovyColumn - 1
		
		return new Position(lspLine, lspColumn)
	}
	
	/**
	 * Calculates the character offset in the content for the given position.
	 * Handles different line ending types: \n, \r, \r\n
	 *
	 * @param content The text to calculate the offset in
	 * @param position The position (line, character) to convert to an offset
	 * @return The character offset, or -1 if the position is invalid or out of bounds
	 */
	static int getOffset(String content, Position position) {
		if (!content || !position || position.line < 0 || position.character < 0) {
			return -1
		}
		
		int targetLine = position.line
		int targetCharacter = position.character
		int length = content.length()
		int currentLine = 0
		int offset = 0
		
		// Find the start of the target line
		while (offset < length && currentLine < targetLine) {
			char c = content.charAt(offset)
			if (c == '\n') {
				offset++
				currentLine++
			} else if (c == '\r') {
				// Handle CRLF and standalone CR
				if (offset + 1 < length && content.charAt(offset + 1) == '\n') {
					offset += 2 // Skip CRLF
				} else {
					offset++ // Skip standalone CR
				}
				currentLine++
			} else {
				offset++
			}
		}
		
		// Check if target line exists
		if (currentLine != targetLine) {
			return -1
		}
		
		// Find the end of the current line to determine line length
		int lineStart = offset
		int lineEnd = offset
		while (lineEnd < length) {
			char c = content.charAt(lineEnd)
			if (c == '\n' || c == '\r') break
			lineEnd++
		}
		
		// Clamp character position to line length
		int lineLength = lineEnd - lineStart
		int charIndex = Math.min(targetCharacter, lineLength)
		
		return lineStart + charIndex
	}
	
	/**
	 * Calculates the Position (line, character) for a given character offset in the string.
	 * Handles different line ending types: \n, \r, \r\n
	 *
	 * @param content The text to calculate the position in
	 * @param offset The character offset to convert to a position
	 * @return The Position, or null if the offset is invalid or out of bounds
	 */
	static Position getPosition(String content, int offset) {
		if (!content || offset < 0 || offset > content.length()) {
			return null
		}
		
		// Handle empty string
		if (content.isEmpty()) {
			return null
		}
		
		int line = 0
		int lineStart = 0
		int i = 0
		
		while (i < offset) {
			char c = content.charAt(i)
			if (c == '\r') {
				// Handle CRLF and standalone CR
				if (i + 1 < content.length() && content.charAt(i + 1) == '\n') {
					// CRLF case
					if (offset == i + 1) {
						// Offset points to '\n' in CRLF, treat as end of current line
						return new Position(line, i - lineStart)
					}
					i += 2
				} else {
					// Standalone CR
					i++
				}
				line++
				lineStart = i
			} else if (c == '\n') {
				// Standalone LF
				line++
				i++
				lineStart = i
			} else {
				i++
			}
		}
		
		int character = offset - lineStart
		return new Position(line, Math.max(0, character))
	}
	
	/**
	 * Gets the text content of a specific line.
	 * @param sourceText The source text
	 * @param lineNumber The line number (0-based)
	 * @return The line text, or empty string if line doesn't exist
	 */
	static String getLineText(String sourceText, int lineNumber) {
		if (!sourceText || lineNumber < 0) return ''
		
		String[] lines = sourceText.split('\r?\n', -1)
		if (lineNumber >= lines.length) return ''
		
		return lines[lineNumber]
	}
	
	/**
	 * Gets the character offset of the start of a specific line.
	 * @param source The source text
	 * @param lineNumber The line number (0-based)
	 * @return The offset of the line start, or -1 if invalid
	 */
	static int getLineStartOffset(String source, int lineNumber) {
		if (!source || lineNumber < 0) return -1
		
		if (lineNumber == 0) return 0
		
		int currentLine = 0
		int offset = 0
		int length = source.length()
		
		while (offset < length && currentLine < lineNumber) {
			char c = source.charAt(offset)
			if (c == '\n') {
				offset++
				currentLine++
			} else if (c == '\r') {
				if (offset + 1 < length && source.charAt(offset + 1) == '\n') {
					offset += 2 // Skip CRLF
				} else {
					offset++ // Skip standalone CR
				}
				currentLine++
			} else {
				offset++
			}
		}
		
		return currentLine == lineNumber ? offset : -1
	}
}