package kingsk.grails.lsp.utils

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.codehaus.groovy.control.messages.WarningMessage
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

class RangeHelper {
	static boolean contains(Range range, Position position) {
		return (PositionHelper.COMPARATOR.compare(position, range.start) >= 0 &&
				PositionHelper.COMPARATOR.compare(position, range.end) <= 0)
	}
	
	static boolean isPositionWithinNode(ASTNode node, Position position) {
		Range range = ASTUtils.astNodeToRange(node)
		return range && contains(range, position)
	}
	
	static Range zeroRange() {
		Position zero = PositionHelper.zeroPosition()
		return new Range(zero, zero)
	}
	
	static Range warningToRange(WarningMessage warning) {
		if (!warning?.context) return null
		Position startPos = PositionHelper.warningToPosition(warning)
		Position endPos = PositionHelper.fromGroovyPosition(
				startPos.line,
				warning.context.rootText.length() + startPos.character
		)
		return new Range(startPos, endPos)
	}
	
	static Range syntaxErrorToRange(SyntaxErrorMessage errorMessage) {
		if (!errorMessage) return null
		def start = PositionHelper.fromGroovyPosition(errorMessage.cause.startLine, errorMessage.cause.startColumn)
		if (!start) return null
		def end = PositionHelper.fromGroovyPosition(errorMessage.cause.endLine, errorMessage.cause.endColumn) ?: start
		return new Range(start, end)
	}
	
	//	static boolean intersect(Range r1, Range r2) {
	//		return contains(r1, r2.start) || contains(r1, r2.end)
	//	}
	
	//	static String getSubstring(String string, Range range) {
	//		return getSubstring(string, range, 0)
	//	}
	
	//	static String getSubstring(String string, Range range, int maxLines) {
	//		def lines = string.split('\n')
	//
	//		def startLine = range.start.line
	//		def startChar = range.start.character
	//
	//		def endLine = range.end.line
	//		def endChar = range.end.character
	//
	//		def lineCount = 1 + (endLine - startLine)
	//
	//		if (maxLines > 0 && (lineCount > maxLines)) {
	//			endLine = startLine + maxLines - 1
	//			endChar = 0
	//		}
	//
	//		int maxLineBreaks = endLine - startLine
	//		int endCharStart = (maxLineBreaks > 0) ? 0 : startChar
	//		def result = null
	//		if (maxLineBreaks > 0) {
	//			result = lines[0..<maxLineBreaks]
	//		}
	
	//		def result = lines[startLine..endLine].collect { line, index ->
	//			if (index == 0) {
	//				line[startChar..-1]
	//			} else if (index == endLine - startLine) {
	//				line[0..endChar - 1]
	//			} else {
	//				line
	//			}
	//		}.join('\n')
	//		return result
	//	}
	static String getSubstring(String string, Range range, int maxLines) {
		BufferedReader reader = new BufferedReader(new StringReader(string))
		StringBuilder builder = new StringBuilder()
		Position start = range.getStart()
		Position end = range.getEnd()
		int startLine = start.getLine()
		int startChar = start.getCharacter()
		int endLine = end.getLine()
		int endChar = end.getCharacter()
		int lineCount = 1 + (endLine - startLine)
		if (maxLines > 0 && lineCount > maxLines) {
			endLine = startLine + maxLines - 1
			endChar = 0
		}
		try {
			for (int i = 0; i < startLine; i++) {
				// ignore these lines
				reader.readLine()
			}
			for (int i = 0; i < startChar; i++) {
				// ignore these characters
				reader.read()
			}
			int endCharStart = startChar
			int maxLineBreaks = endLine - startLine
			if (maxLineBreaks > 0) {
				endCharStart = 0
				int readLines = 0
				while (readLines < maxLineBreaks) {
					char character = (char) reader.read()
					if (character == '\n') {
						readLines++
					}
					builder.append(character)
				}
			}
			// the remaining characters on the final line
			for (int i = endCharStart; i < endChar; i++) {
				builder.append((char) reader.read())
			}
		} catch (IOException ignored) {
			return null
		}
		try {
			reader.close()
		} catch (IOException ignored) {
		}
		return builder.toString()
	}
}
