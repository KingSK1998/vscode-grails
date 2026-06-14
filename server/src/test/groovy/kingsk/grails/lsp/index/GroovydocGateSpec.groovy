package kingsk.grails.lsp.index

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import spock.lang.Specification
import groovy.util.logging.Slf4j

@Slf4j
class GroovydocGateSpec extends Specification {

    def "verify if Groovydoc is extracted by Groovy 4 compiler"() {
        given:
        String source = '''
            package com.example

            /**
             * This is a class level groovydoc.
             */
            class TestClass {
                /**
                 * This is a method level groovydoc.
                 * @return something
                 */
                String testMethod() {
                    return "test"
                }
            }
        '''

        when: "compiling the source"
        CompilerConfiguration config = new CompilerConfiguration()
        // Try enabling groovydoc parsing if it's an optimization option
        config.getOptimizationOptions().put(CompilerConfiguration.GROOVYDOC, true)

        CompilationUnit cu = new CompilationUnit(config)
        cu.addSource("TestClass.groovy", source)
        cu.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)

        ClassNode classNode = cu.getAST().getClasses().find { it.name == "com.example.TestClass" }
        MethodNode methodNode = classNode?.getMethods("testMethod")?.first()

        then: "we can observe if groovydoc is present"
        def classDoc = classNode?.getGroovydoc()?.content
        def methodDoc = methodNode?.getGroovydoc()?.content
        
        log.info("ClassNode Groovydoc: {}", classDoc)
        log.info("MethodNode Groovydoc: {}", methodDoc)

        expect:
        true
    }
}
