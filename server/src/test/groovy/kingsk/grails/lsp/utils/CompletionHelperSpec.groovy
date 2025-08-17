package kingsk.grails.lsp.utils

import spock.lang.Specification
import spock.lang.Title

/**
 * Tests for the CompletionUtil helper class
 */
@Title("Completion Helper Utilities Tests")
class CompletionHelperSpec extends Specification {
	
	def "should extract prefix '#result' from line '#line' at cursor #cursor"() {
		expect: "The extracted prefix matches the expected result"
		CompletionUtil.extractPrefixFromLine(line, cursor) == result
		
		where:
		line                                | cursor || result
		"foo.bar"                           | 7      || "bar"
		"def abc"                           | 7      || "abc"
		"return"                            | 3      || "ret"
		"   abc"                            | 2      || ""
		"   abc"                            | 5      || "ab"
		"   abc"                            | 6      || "abc"
		""                                  | 0      || ""
		"test."                             | 5      || ""
		"a.b.c"                             | 5      || "c"
		
		// Complex identifier
		"this.someProperty"                 | 17     || "someProperty"
		"obj.method().prop"                 | 17     || "prop"
		"package.Class.CONSTANT"            | 22     || "CONSTANT"
		"map['key'].value"                  | 16     || "value"
		
		// Edge cases
		"test"                              | 100    || ""
		null                                | 0      || ""
		""                                  | 0      || ""
		"test"                              | -1     || ""
		
		// Dummy completion cases
		"var.__GRAILS_DUMMY_COMPLETION__"   | 27     || "__GRAILS_DUMMY_COMPLETION__"
		"new __GRAILS_DUMMY_COMPLETION__()" | 27     || "__GRAILS_DUMMY_COMPLETION__"
		"var.__GRAILS_DUMMY_COMPLETION__."  | 28     || "__GRAILS_DUMMY_COMPLETION__"
		"__GRAILS_DUMMY_COMPLETION__"       | 23     || "__GRAILS_DUMMY_COMPLETION__"
		"obj.__GRAILS_DUMMY_COMPLETION__"   | 27     || "__GRAILS_DUMMY_COMPLETION__"
	}
}