package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.context.ProjectContextImpl
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.test.MockLanguageClient
import org.eclipse.lsp4j.*
import spock.lang.Specification

import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BooleanSupplier

class RevisionAndPublicationSpec extends Specification {

    private GrailsService grailsService
    private ProjectContextImpl context
    private File projectRoot
    private MockLanguageClient mockClient

    def setup() {
        grailsService = new GrailsService()
        projectRoot = new File(System.getProperty("user.dir"), "build/test-rev-pub")
        projectRoot.mkdirs()

        GrailsProject project = new GrailsProject(
            name: "test-rev-pub",
            rootDirectory: projectRoot,
            sourceDirectories: [new File(projectRoot, "src/main/groovy")] as Set
        )
        grailsService.workspaceManager.addProject(project)
        context = (ProjectContextImpl) grailsService.workspaceManager.getProjectForUri(projectRoot.toURI().toString())

        mockClient = new MockLanguageClient()
        grailsService.connect(mockClient)
    }

    def cleanup() {
        grailsService?.shutdown()
        if (projectRoot.exists()) {
            projectRoot.deleteDir()
        }
    }

    def "should reject stale candidate when newer version arrives during compilation"() {
        given: "A file tracked at version 1"
        File sourceFile = new File(projectRoot, "src/main/groovy/Candidate.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package sample\nclass Candidate { int v = 0 }"
        String uri = sourceFile.toURI().toString()

        TextFile initial = grailsService.fileTracker.didOpenFile(
            new DidOpenTextDocumentParams(new TextDocumentItem(uri, "groovy", 1, "package sample\nclass Candidate { int v = 1 }")),
            false
        )
        assert initial.version == 1

        and: "A candidate revision created from version 1"
        TextFile candidateV1 = TextFile.create(initial.uri, initial.text)
        candidateV1.version = 1
        candidateV1.openGeneration = initial.openGeneration

        AtomicBoolean wasSuperseded = new AtomicBoolean(false)
        BooleanSupplier currentRevision = { -> !wasSuperseded.get() } as BooleanSupplier

        long initialSnapshots = context.snapshotManager.active?.version ?: 0L

        when: "Compilation begins for version 1, but version 2 arrives before completion"
        grailsService.fileTracker.didChangeFile(
            new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                [new TextDocumentContentChangeEvent("package sample\nclass Candidate { int v = 2 }")]
            ),
            false
        )
        wasSuperseded.set(true)

        context.compileAndVisitAST(candidateV1, currentRevision)

        then: "Candidate V1 compilation is rejected and not committed to snapshots"
        long afterSnapshots = context.snapshotManager.active?.version ?: 0L
        afterSnapshots == initialSnapshots
        !context.openDocumentUris.contains(TextFile.normalizePath(uri))
    }

    def "should reject stale candidate when open generation has changed"() {
        given: "A file opened, then closed, then reopened with a new open generation"
        File sourceFile = new File(projectRoot, "src/main/groovy/ReopenRace.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package sample\nclass ReopenRace {}"
        String uri = sourceFile.toURI().toString()

        TextFile open1 = grailsService.fileTracker.didOpenFile(
            new DidOpenTextDocumentParams(new TextDocumentItem(uri, "groovy", 1, "class ReopenRace { int v = 1 }")),
            false
        )
        long oldGen = open1.openGeneration
        TextFile candidateOld = TextFile.create(open1.uri, open1.text)
        candidateOld.version = 1
        candidateOld.openGeneration = oldGen

        // Close and reopen with reset version 1 under a new generation
        grailsService.fileTracker.didCloseFile(new DidCloseTextDocumentParams(new TextDocumentIdentifier(uri)))
        TextFile open2 = grailsService.fileTracker.didOpenFile(
            new DidOpenTextDocumentParams(new TextDocumentItem(uri, "groovy", 1, "class ReopenRace { int v = 2 }")),
            false
        )
        assert open2.openGeneration > oldGen

        when: "The stale candidate from the old generation tries to compile and commit"
        context.compileAndVisitAST(candidateOld, { -> true } as BooleanSupplier)

        then: "The candidate with the obsolete generation is rejected"
        !context.openDocumentUris.contains(TextFile.normalizePath(uri))
    }

    def "should reject delayed diagnostics when document version has advanced"() {
        given: "A file opened and advanced to version 2"
        File sourceFile = new File(projectRoot, "src/main/groovy/DelayedDiag.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package sample\nclass DelayedDiag {}"
        String uri = sourceFile.toURI().toString()

        TextFile open = grailsService.fileTracker.didOpenFile(
            new DidOpenTextDocumentParams(new TextDocumentItem(uri, "groovy", 1, "class DelayedDiag {}")),
            false
        )
        grailsService.fileTracker.didChangeFile(
            new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier(uri, 2),
                [new TextDocumentContentChangeEvent("class DelayedDiag { int x = 1 }")]
            ),
            false
        )

        mockClient.diagnostics.clear()

        when: "Publishing delayed diagnostics with expectedVersion = 1"
        grailsService.diagnostics.publishDiagnosticsForFile(uri, 1, open.openGeneration)

        then: "Stale diagnostics for version 1 are rejected"
        mockClient.diagnostics.isEmpty()

        when: "Publishing diagnostics matching current version 2"
        grailsService.diagnostics.publishDiagnosticsForFile(uri, 2, open.openGeneration)

        then: "Diagnostics for version 2 are accepted and published"
        mockClient.diagnostics.size() == 1
        mockClient.diagnostics[0].uri == uri
        mockClient.diagnostics[0].version == 2
    }

    def "should permanently remove file on deleteDocument while closeDocument retains disk facts"() {
        given: "A file on disk tracked and compiled in the project"
        File sourceFile = new File(projectRoot, "src/main/groovy/LifecycleTest.groovy")
        sourceFile.parentFile.mkdirs()
        sourceFile.text = "package sample\nclass LifecycleTest { String name }"
        String uri = sourceFile.toURI().toString()

        TextFile overlay = grailsService.fileTracker.didOpenFile(
            new DidOpenTextDocumentParams(new TextDocumentItem(uri, "groovy", 1, "package sample\nclass LifecycleTest { String modified }")),
            false
        )
        context.compileAndVisitAST(overlay, { -> true } as BooleanSupplier)
        assert context.openDocumentUris.contains(TextFile.normalizePath(uri))

        when: "Closing the overlay"
        context.closeDocument(uri)
        grailsService.fileTracker.clearClosedFileDependencies(uri)

        then: "The overlay is removed from openDocumentUris, but disk source remains valid on disk"
        !context.openDocumentUris.contains(TextFile.normalizePath(uri))
        sourceFile.exists()

        when: "Deleting the document permanently"
        sourceFile.delete()
        grailsService.fileTracker.didDeleteFile(uri)
        context.deleteDocument(uri)

        then: "The file is permanently removed from the compiler cache and project tracking"
        !context.openDocumentUris.contains(TextFile.normalizePath(uri))
        !context.compiler.compilationExistsFor(uri)
        grailsService.fileTracker.getTextFile(uri) == null
    }
}

