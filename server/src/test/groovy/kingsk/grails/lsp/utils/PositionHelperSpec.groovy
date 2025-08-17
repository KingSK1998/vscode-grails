package kingsk.grails.lsp.utils

import org.eclipse.lsp4j.Position
import spock.lang.Specification

/**
 * Tests for the PositionHelper utility class
 */
class PositionHelperSpec extends Specification {
	
	def "should compute offset from position"() {
		expect:
		PositionHelper.getOffset(content, new Position(line, character)) == offset
		
		where:
		content               | line | character || offset
		// Invalid cases
		null                  | 0    | 0         || -1
		"abc\ndef\nghi"       | -1   | 0         || -1
		"abc\ndef\nghi"       | 3    | 0         || -1
		// Basic functionality
		"abc\ndef\nghi"       | 0    | 0         || 0
		"abc\ndef\nghi"       | 0    | 3         || 3
		"abc\ndef\nghi"       | 1    | 0         || 4
		"abc\ndef\nghi"       | 2    | 1         || 9
		"abc\ndef\nghi"       | 2    | 10        || 11 // Beyond line length
		// Line endings
		"one\ntwo\nthree"     | 2    | 1         || 9 // Unix
		"one\r\ntwo\r\nthree" | 2    | 1         || 11 // Windows
		// Special cases
		"line1\n\n\tline3"    | 1    | 0         || 6 // Empty line
		"line1\n\n\tline3"    | 2    | 1         || 8 // After tab
	}
	
	def "should compute position from offset"() {
		expect:
		PositionHelper.getPosition(content, offset) == position
		
		where:
		content               | offset || position
		// Invalid cases
		null                  | 0      || null
		""                    | 0      || null
		"abc\ndef\nghi"       | -1     || null
		"abc\ndef\nghi"       | 100    || null
		// Basic functionality
		"abc\ndef\nghi"       | 0      || new Position(0, 0)
		"abc\ndef\nghi"       | 3      || new Position(0, 3)
		"abc\ndef\nghi"       | 4      || new Position(1, 0)
		"abc\ndef\nghi"       | 9      || new Position(2, 1)
		"abc\ndef\nghi"       | 11     || new Position(2, 3)
		// Line endings
		"one\ntwo\nthree"     | 9      || new Position(2, 1) // Unix
		"one\r\ntwo\r\nthree" | 11     || new Position(2, 1) // Windows
		// Edge cases
		"x"                   | 0      || new Position(0, 0)
		"x"                   | 1      || new Position(0, 1)
	}
	
	def "should handle null position input"() {
		expect:
		PositionHelper.getOffset("test", null) == -1
	}
	
	def "should order positions correctly"() {
		expect:
		Integer.signum(PositionHelper.COMPARATOR.compare(pos1, pos2)) == Integer.signum(expectedResult)
		
		where:
		pos1               | pos2               || expectedResult
		new Position(0, 0) | new Position(0, 1) || -1
		new Position(0, 1) | new Position(1, 0) || -1
		new Position(1, 5) | new Position(1, 5) || 0
		new Position(2, 1) | new Position(2, 0) || 1
	}
	
	def "should validate positions"() {
		expect:
		PositionHelper.isValidPosition(position) == valid
		
		where:
		position            || valid
		new Position(0, 0)  || true
		new Position(3, 10) || true
		new Position(-1, 0) || false
		new Position(0, -1) || false
		null                || false
	}
	
	def "should convert Groovy positions to LSP positions"() {
		expect:
		PositionHelper.fromGroovyPosition(groovyLine, groovyCol) == expected
		
		where:
		groovyLine | groovyCol || expected
		-1         | 3         || null
		0          | 0         || new Position(0, 0)
		1          | 1         || new Position(0, 0)
		2          | 5         || new Position(1, 4)
	}
	
	def "should handle utility methods"() {
		expect:
		PositionHelper.zeroPosition() == new Position(0, 0)
		PositionHelper.getLineText("line1\nline2", 1) == "line2"
		PositionHelper.getLineText("line1\nline2", 5) == ""
		PositionHelper.getLineStartOffset("line1\nline2", 1) == 6
	}
}