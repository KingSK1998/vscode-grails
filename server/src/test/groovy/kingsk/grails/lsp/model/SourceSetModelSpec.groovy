package kingsk.grails.lsp.model

import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.SourceSetModel
import spock.lang.Specification
import spock.lang.TempDir

class SourceSetModelSpec extends Specification {
    @TempDir File root

    def "an explicitly empty resolved classpath never inherits legacy dependencies"() {
        given:
        File jar = new File(root, 'host.jar')
        jar.text = 'fixture'
        def source = new File(root, 'src')
        def model = new SourceSetModel('main', ':', root, root, [source])
        def project = new GrailsProject(rootDirectory: root, sourceSets: [main: model],
            dependencies: [new DependencyNode('host', 'test', '1', 'COMPILE', jar, null, null)] as Set)

        expect:
        project.mainClasspathUrls.isEmpty()
        project.getClasspathUrlsForUri(new File(source, 'Main.groovy').toURI().toString()).isEmpty()
    }

    def "unknown files cannot inherit a resolved project's main classpath"() {
        given:
        File jar = new File(root, 'dep.jar')
        jar.text = 'fixture'
        def model = new SourceSetModel('main', ':', root, root, [new File(root, 'src')], [], [], [jar])
        def project = new GrailsProject(rootDirectory: root, sourceSets: [main: model])

        expect:
        project.getSourceSetForUri(new File(root, 'outside/Main.groovy').toURI().toString()) == null
        project.getClasspathUrlsForUri(new File(root, 'outside/Main.groovy').toURI().toString()).isEmpty()
    }

    def "source membership applies exclusions relative to the declared root"() {
        given:
        def src = new File(root, 'src')
        def model = new SourceSetModel('main', ':', root, root, [src], [], [], [], [], [], ['internal/**'])

        expect:
        model.containsSource(new File(src, 'Public.groovy'))
        !model.containsSource(new File(src, 'internal/Hidden.groovy'))
        !model.containsSource(new File(root, 'src-api/Other.groovy'))
    }

    def "more specific declared source root wins independently of map order"() {
        given:
        def src = new File(root, 'src')
        def test = new File(src, 'checks')
        def mainModel = new SourceSetModel('main', ':', root, root, [src])
        def checkModel = new SourceSetModel('checks', ':', root, root, [test])
        def project = new GrailsProject(rootDirectory: root, sourceSets: [main: mainModel, checks: checkModel])

        expect:
        project.getSourceSetForFile(new File(test, 'Check.groovy')).sourceSetName == 'checks'
    }

    def "equal source roots in different source sets are explicitly ambiguous"() {
        given:
        def src = new File(root, 'shared')
        def project = new GrailsProject(rootDirectory: root, sourceSets: [
            main: new SourceSetModel('main', ':', root, root, [src]),
            alternate: new SourceSetModel('alternate', ':', root, root, [src])])

        expect:
        project.getSourceSetForFile(new File(src, 'Shared.groovy')) == null
    }

    def "resolved model collections cannot change after construction"() {
        given:
        def paths = [new File(root, 'first.jar')]
        def model = new SourceSetModel('main', ':', root, root, [], [], [], paths)
        paths.add(new File(root, 'second.jar'))

        expect:
        model.compileClasspath.size() == 1

        when:
        model.compileClasspath.add(new File(root, 'third.jar'))

        then:
        thrown(UnsupportedOperationException)
    }

    def "generated roots keep their owner's visibility and do not acquire other roots"() {
        given:
        def mainGenerated = new File(root, 'generated/main')
        def testGenerated = new File(root, 'generated/test')
        def model = new SourceSetModel('test', ':', root, root, [testGenerated], [], [testGenerated])

        expect:
        model.containsSource(new File(testGenerated, 'Generated.groovy'))
        model.isGenerated(new File(testGenerated, 'Generated.groovy'))
        !model.containsSource(new File(mainGenerated, 'Generated.groovy'))
    }
}
