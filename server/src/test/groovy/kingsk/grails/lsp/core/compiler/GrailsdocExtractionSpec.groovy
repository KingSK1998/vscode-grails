package kingsk.grails.lsp.core.compiler

import spock.lang.Specification
import kingsk.grails.lsp.GrailsService
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.SourceUnit

class GrailsdocExtractionSpec extends Specification {

    def "should extract groovydoc from ClassNode and MethodNode"() {
        given: "A Groovy class with groovydoc comments"
        String content = """
/**
 * This is the class groovydoc.
 */
class DocTestClass {
    /**
     * This is the method groovydoc.
     */
    void doSomething() {}
}
"""
        File tempFile = File.createTempFile("DocTestClass", ".groovy")
        tempFile.text = content
        tempFile.deleteOnExit()
        
        GrailsService mockService = Mock(GrailsService)
        mockService.project >> new kingsk.grails.lsp.model.dto.GrailsProject(
            excludeDirectories: []
        )
        GrailsCompiler compiler = new GrailsCompiler(mockService)
        
        // Ensure compiler uses standard config for isolated compile
        compiler.updateCompilerOptions()
        CompilerConfiguration configuration = compiler.compilerConfig
        compiler.classLoader = new GroovyClassLoader(this.class.classLoader, configuration)

        when: "The file is compiled using existing GrailsCompiler"
        def uri = tempFile.toURI().toString()
        compiler.compilationUnit = new kingsk.grails.lsp.core.compiler.GrailsCU(configuration, null, compiler.classLoader)
        compiler.compilationUnit.addSource("DocTestClass.groovy", content)
        compiler.compilationUnit.compile(org.codehaus.groovy.control.Phases.INSTRUCTION_SELECTION)
        
        def sourceUnit = compiler.compilationUnit.iterator().next()
        def moduleNode = sourceUnit.getAST()
        
        then: "Compiler configuration should be reported"
        println "Compiler Configuration:"
        println "- runtimeGroovydocEnabled: " + configuration.runtimeGroovydocEnabled
        println "- optimizationOptions: " + configuration.optimizationOptions
        
        and: "ClassNode groovydoc should be present"
        ClassNode classNode = moduleNode.classes.find { it.name == 'DocTestClass' }
        assert classNode != null
        def classDoc = classNode.groovydoc
        println "Class Groovydoc: \${classDoc?.content}"
        assert classDoc != null
        assert classDoc.content.contains("This is the class groovydoc")

        and: "MethodNode groovydoc should be present"
        MethodNode methodNode = classNode.methods.find { it.name == 'doSomething' }
        assert methodNode != null
        def methodDoc = methodNode.groovydoc
        println "Method Groovydoc: \${methodDoc?.content}"
        assert methodDoc != null
        assert methodDoc.content.contains("This is the method groovydoc")
    }
}
