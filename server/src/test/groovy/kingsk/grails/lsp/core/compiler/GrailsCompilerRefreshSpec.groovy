package kingsk.grails.lsp.core.compiler

import kingsk.grails.lsp.context.ProjectContext
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile
import org.codehaus.groovy.control.Phases
import spock.lang.Specification

class GrailsCompilerRefreshSpec extends Specification {
    GrailsCompiler compiler

    def setup() {
        GrailsProject project = new GrailsProject(name: 'refresh-test')
        ProjectContext context = Stub(ProjectContext) {
            getProject() >> project
        }
        compiler = new GrailsCompiler(null, context)
    }

    def cleanup() {
        compiler?.invalidateCompiler()
    }

    def "refresh clears only the requested scopes and preserves their loaders"() {
        given:
        Map<String, SourceSetCompilationState> states = [:]
        Map<String, GrailsCU> units = [:]
        Map<String, GroovyClassLoader> loaders = [:]
        Map<String, TextFile> files = [:]
        ['main', 'test', 'integrationTest'].each { String name ->
            SourceSetCompilationState state = compiler.getOrCreateState(name)
            TextFile file = TextFile.create("file:///refresh/${name}/Example.groovy",
                'class Example { MissingType value }')
            state.addOrUpdateSource(file)
            assert !state.compile(Phases.SEMANTIC_ANALYSIS)
            assert state.compilationExistsFor(file.uri)
            assert state.errorCollectorOrNull.hasErrors()
            states[name] = state
            units[name] = state.compilationUnit
            loaders[name] = state.classLoader
            files[name] = file
        }

        when:
        compiler.refreshCompilationUnit(scope)

        then:
        states.each { String name, SourceSetCompilationState state ->
            assert state.classLoader.is(loaders[name])
            assert state.compilationUnit.classLoader.is(loaders[name])
            assert state.compilationUnit.configuration.is(units[name].configuration)
            if (refreshed.contains(name)) {
                assert !state.compilationUnit.is(units[name])
                assert state.sourceUnits.isEmpty()
                assert !state.compilationExistsFor(files[name].uri)
                assert state.getSourceUnit(files[name]) == null
                assert !state.errorCollectorOrNull.hasErrors()
            } else {
                assert state.compilationUnit.is(units[name])
                assert state.compilationExistsFor(files[name].uri)
                assert state.errorCollectorOrNull.hasErrors()
            }
        }
        compiler.@sourceSetStates.keySet() == states.keySet()

        when: 'fresh sources are added to the reset scopes'
        refreshed.each { String name ->
            states[name].addOrUpdateSource(TextFile.create(files[name].uri, 'class Example {}'))
        }

        then:
        refreshed.every { String name -> states[name].compile(Phases.SEMANTIC_ANALYSIS) }

        where:
        scope             | refreshed
        'main'            | ['main']
        'test'            | ['test']
        'integrationTest' | ['integrationTest']
        null              | ['main', 'test', 'integrationTest']
        ''                | ['main', 'test', 'integrationTest']
        'unknown'         | []
    }

    def "no-argument refresh initializes main on a fresh compiler"() {
        when:
        compiler.refreshCompilationUnit()

        then:
        compiler.@sourceSetStates.keySet() == ['main'] as Set
        compiler.compilationUnit != null
        compiler.compilationUnit.classLoader.is(compiler.classLoader)
        compiler.sourceUnits.isEmpty()
        !compiler.errorCollectorOrNull.hasErrors()
    }

    def "named refresh preserves laziness when the scope has not been created"() {
        when:
        compiler.refreshCompilationUnit('test')

        then:
        compiler.@sourceSetStates.isEmpty()
    }
}
