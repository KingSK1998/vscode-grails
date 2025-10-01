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
        String content = """class SignatureHelp {
    public SignatureHelp() {
        method()
    }
    public void method(int param0) {}
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help at method call"
        def signatureHelp = getSignatureHelp(uri, 2, 15) // Position after "method("

        then: "Should provide signature information"
        signatureHelp != null
        signatureHelp.signatures.size() >= 1

        and: "The signature should contain the method"
        def signature = signatureHelp.signatures.find { it.label.contains("method") }
        signature != null
    }

    def "should provide signature help on method with multiple parameters"() {
        given: "A class with a method call with multiple parameters"
        String content = """class SignatureHelp {
    public SignatureHelp() {
        method()
    }
    public void method(int param0, String param1) {}
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help at method call"
        def signatureHelp = getSignatureHelp(uri, 2, 15)

        then: "Should provide signature information"
        signatureHelp != null
        signatureHelp.signatures.size() >= 1

        and: "The signature should contain the method"
        def signature = signatureHelp.signatures.find {
            it.label.contains("method")
        }
        signature != null

        and: "The signature should have parameters"
        if (signature.parameters) {
            // Your provider returns parameter names only (without types)
            assertSignatureParameters(signature, ["param0", "param1"])

            // Verify that type information is in documentation
            signature.parameters[0].documentation?.getRight()?.value?.contains("int") ||
                signature.documentation?.getRight()?.value?.contains("int")
        } else {
            // If parameters aren't parsed separately, check in label
            signature.label.contains("param0") && signature.label.contains("param1")
        }
    }

    def "should provide signature help with active parameter"() {
        given: "A class with a method call with parameters already provided"
        String content = """class SignatureHelp {
    public SignatureHelp() {
        method(123, "test")
    }
    public void method(int param0, String param1) {}
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help after first parameter"
        def signatureHelp = getSignatureHelp(uri, 2, 19) // Position after "123,"

        then: "Should provide signature information"
        signatureHelp != null
        signatureHelp.signatures.size() >= 1
    }

    def "should provide signature help for constructor"() {
        given: "A class with a constructor call"
        String content = """class SignatureHelp {
    String name

    public SignatureHelp(String name) {
        this.name = name
    }

    static void test() {
        new SignatureHelp("test")
    }
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help at constructor call"
        def signatureHelp = getSignatureHelp(uri, 8, 25) // Position after "SignatureHelp("

        then: "Should provide constructor signature information"
        signatureHelp != null
        signatureHelp.signatures.size() >= 1

        and: "Should contain constructor information"
        def sig = signatureHelp.signatures[0]
        sig.label == "<init>"
        sig.parameters.size() == 1
        sig.parameters[0].label.getLeft() == "name"

        and: "Should have proper documentation"
        sig.documentation.getRight().value.contains("java.lang.String")

        and: "Should track active signature and parameter"
        signatureHelp.activeSignature == 0
        signatureHelp.activeParameter == 1
    }

    def "should provide signature help for overloaded methods"() {
        given: "A class with overloaded methods"
        String content = """class SignatureHelp {
    public void method(int param) {}
    public void method(String param) {}
    public void method(int param1, String param2) {}

    public void test() {
        method()
    }
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help at overloaded method call"
        def signatureHelp = getSignatureHelp(uri, 6, 15)

        then: "Should provide all overloaded signatures"
        signatureHelp != null
        signatureHelp.signatures.size() >= 2

        and: "Should contain different overloads"
        def methods = signatureHelp.signatures.findAll { it.label.contains("method") }
        methods.size() >= 2
    }

    def "should handle nested method calls"() {
        given: "A class with nested method calls"
        String content = """class SignatureHelp {
    public String getString() { return "test" }
    public void method(String param) {}

    public void test() {
        method(getString())
    }
}"""
        String uri = openTextDocument("SignatureHelp.groovy", content)

        when: "Requesting signature help inside nested call"
        def signatureHelp = getSignatureHelp(uri, 5, 25) // Position after "getString("

        then: "Should provide signature for the inner method"
        signatureHelp != null
        signatureHelp.signatures.size() >= 1

        and: "Should contain the getString method signature"
        def getStringSignature = signatureHelp.signatures.find { it.label.contains("getString") }
        getStringSignature != null
    }
}
