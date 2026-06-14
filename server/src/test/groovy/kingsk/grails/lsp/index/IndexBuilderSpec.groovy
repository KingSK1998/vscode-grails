package kingsk.grails.lsp.index

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.ast.ClassNode
import spock.lang.Specification
import org.eclipse.lsp4j.SymbolKind
import kingsk.grails.lsp.model.enums.GrailsArtifactType

class IndexBuilderSpec extends Specification {

    def "extracts basic symbols from class"() {
        given:
        String source = '''
            package com.example

            class TestClass {
                String testField
                String testMethod(int x) {
                    def localVar = "hello"
                    return localVar
                }
                def dynamicMethod(args) {}
                def dynamicMethod(args, moreArgs) {}
                def dynamicMethod(args) {} // Duplicate param count, will collide and get suffix
            }
        '''
        
        CompilerConfiguration config = new CompilerConfiguration()
        CompilationUnit cu = new CompilationUnit(config)
        cu.addSource("TestClass.groovy", source)
        cu.compile(org.codehaus.groovy.control.Phases.SEMANTIC_ANALYSIS)
        
        List<ClassNode> classNodes = cu.getAST().getClasses()
        
        when: "building symbols"
        List<SymbolInfo> symbols = IndexBuilder.buildSymbols("file:///test.groovy", classNodes)
        List<LocalSymbolInfo> locals = IndexBuilder.buildLocals("file:///test.groovy", classNodes)
        
        then: "class symbol is extracted"
        SymbolInfo classSymbol = symbols.find { it.kind == SymbolKind.Class }
        classSymbol.descriptor == "com.example.TestClass"
        classSymbol.name == "TestClass"
        classSymbol.fieldType == "Class"
        
        and: "field is extracted"
        SymbolInfo fieldSymbol = symbols.find { it.kind == SymbolKind.Property && it.name == "testField" }
        fieldSymbol.descriptor == "com.example.TestClass#testField"
        fieldSymbol.fieldType == "java.lang.String"
        
        and: "methods are extracted"
        List<SymbolInfo> methods = symbols.findAll { it.kind == SymbolKind.Method }
        methods.size() == 4
        methods.find { it.descriptor == "com.example.TestClass#testMethod(int)" } != null
        methods.find { it.descriptor == "com.example.TestClass#dynamicMethod(java.lang.Object)" } != null
        methods.find { it.descriptor == "com.example.TestClass#dynamicMethod(java.lang.Object,java.lang.Object)" } != null
        methods.find { it.descriptor == "com.example.TestClass#dynamicMethod(java.lang.Object)#1" } != null // collision resolved
        
        and: "locals are extracted"
        locals.size() == 6 // param 'x', local 'localVar', and the 4 'args' / 'moreArgs'
        locals.find { it.name == "x" } != null
        locals.find { it.name == "localVar" } != null
        locals.find { it.name == "args" } != null
    }
}
