package kingsk.grails.lsp.providers.document

import kingsk.grails.lsp.test.CompletionTestSpec
import kingsk.grails.lsp.test.ProjectType
import kingsk.grails.lsp.utils.completion.CompletionUtil
import org.codehaus.groovy.ast.ASTNode

import org.eclipse.lsp4j.Position

class DiagnosticSpec extends CompletionTestSpec {

    def setup() {
        initializeProject(ProjectType.GRAILS)
    }

    def "diagnose classloader and artifact type"() {
        given:
        String content = """package com.example
class TestController {
    def index() {
        render 
    }
}"""
        // Use a path that matches Grails controller convention
        String uri = openTextDocument("grails-app/controllers/com/example/DiagnosticController.groovy", content)
        int line = 3
        int col = 15
        String lineText = content.readLines()[line]

        when:
        def loader = grailsService.compiler.classLoader
        boolean hasControllerTrait = false
        try {
            Class.forName('grails.artefact.Controller', true, loader)
            hasControllerTrait = true
        } catch (Exception ignored) {}

        def items = getCompletionItems(uri, line, col)
        def artifactType = kingsk.grails.lsp.utils.grails.GrailsArtefactUtils.getGrailsArtifactType(null, uri)

        then:
        println "DIAGNOSTIC_CL: hasControllerTrait=${hasControllerTrait}"
        println "DIAGNOSTIC_CL: artifactType=${artifactType}"
        println "DIAGNOSTIC_CL: items count=${items.size()}"
        
        true
    }
}
