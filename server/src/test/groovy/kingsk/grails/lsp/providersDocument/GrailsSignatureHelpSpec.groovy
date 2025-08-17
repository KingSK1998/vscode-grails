package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.ProjectType
import kingsk.grails.lsp.test.SignatureHelpTestSpec

/**
 * Tests for the GrailsSignatureHelpProvider
 */
class GrailsSignatureHelpSpec extends SignatureHelpTestSpec {
	
	def setup() {
		initializeProject(ProjectType.DUMMY)
	}
	
	def "should provide signature help on method call"() {
		given: "A class with a method call"
		String content = """
            class SignatureHelp {
              public SignatureHelp() {
                method(
              }
              public void method(int param0) {}
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help at method call"
		def signatureHelp = getSignatureHelp(uri, 2, 11)
		
		then: "Should provide signature information"
		signatureHelp != null
		signatureHelp.signatures.size() == 1
		
		and: "The signature should have the correct label"
		def signature = assertContainsSignature(signatureHelp, "public void method(int param0)")
		
		and: "The signature should have the correct parameters"
		assertSignatureParameters(signature, ["int param0"])
		
		and: "The active signature and parameter should be correct"
		assertActiveSignatureAndParameter(signatureHelp, 0, 0)
	}
	
	def "should provide signature help on method with multiple parameters"() {
		given: "A class with a method call with multiple parameters"
		String content = """
            class SignatureHelp {
              public SignatureHelp() {
                method(
              }
              public void method(int param0, String param1) {}
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help at method call"
		def signatureHelp = getSignatureHelp(uri, 2, 11)
		
		then: "Should provide signature information"
		signatureHelp != null
		signatureHelp.signatures.size() == 1
		
		and: "The signature should have the correct label"
		def signature = assertContainsSignature(signatureHelp, "public void method(int param0, String param1)")
		
		and: "The signature should have the correct parameters"
		assertSignatureParameters(signature, ["int param0", "String param1"])
		
		and: "The active signature and parameter should be correct"
		assertActiveSignatureAndParameter(signatureHelp, 0, 0)
	}
	
	def "should provide signature help with active parameter"() {
		given: "A class with a method call with parameters already provided"
		String content = """
            class SignatureHelp {
              public SignatureHelp() {
                method(123,
              }
              public void method(int param0, String param1) {}
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help after first parameter"
		def signatureHelp = getSignatureHelp(uri, 2, 15)
		
		then: "Should provide signature information"
		signatureHelp != null
		signatureHelp.signatures.size() == 1
		
		and: "The signature should have the correct label"
		def signature = assertContainsSignature(signatureHelp, "public void method(int param0, String param1)")
		
		and: "The signature should have the correct parameters"
		assertSignatureParameters(signature, ["int param0", "String param1"])
		
		and: "The active parameter should be the second one"
		assertActiveSignatureAndParameter(signatureHelp, 0, 1)
	}
	
	def "should provide signature help for constructor"() {
		given: "A class with a constructor call"
		String content = """
            class SignatureHelp {
              String name
              
              public SignatureHelp(String name) {
                this.name = name
              }
              
              static void test() {
                new SignatureHelp(
              }
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help at constructor call"
		def signatureHelp = getSignatureHelp(uri, 8, 23)
		
		then: "Should provide constructor signature information"
		signatureHelp != null
		signatureHelp.signatures.size() >= 1
		
		and: "Should contain the custom constructor"
		def constructorSignature = signatureHelp.signatures.find { it.label.contains("SignatureHelp(String name)") }
		constructorSignature != null
	}
	
	def "should provide signature help for overloaded methods"() {
		given: "A class with overloaded methods"
		String content = """
            class SignatureHelp {
              public void method(int param) {}
              public void method(String param) {}
              public void method(int param1, String param2) {}
              
              public void test() {
                method(
              }
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help at overloaded method call"
		def signatureHelp = getSignatureHelp(uri, 6, 11)
		
		then: "Should provide all overloaded signatures"
		signatureHelp != null
		signatureHelp.signatures.size() >= 2
		
		and: "Should contain the different overloads"
		def intSignature = signatureHelp.signatures.find { it.label.contains("method(int param)") }
		def stringSignature = signatureHelp.signatures.find { it.label.contains("method(String param)") }
		intSignature != null
		stringSignature != null
	}
	
	def "should handle nested method calls"() {
		given: "A class with nested method calls"
		String content = """
            class SignatureHelp {
              public String getString() { return "test" }
              public void method(String param) {}
              
              public void test() {
                method(getString(
              }
            }
        """
		String uri = openTextDocument("SignatureHelp.groovy", content)
		
		when: "Requesting signature help inside nested call"
		def signatureHelp = getSignatureHelp(uri, 5, 25)
		
		then: "Should provide signature for the inner method"
		signatureHelp != null
		signatureHelp.signatures.size() >= 1
		
		and: "Should contain the getString method signature"
		def getStringSignature = signatureHelp.signatures.find { it.label.contains("getString()") }
		getStringSignature != null
	}
}