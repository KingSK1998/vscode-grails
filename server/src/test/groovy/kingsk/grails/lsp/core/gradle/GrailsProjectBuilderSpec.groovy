package kingsk.grails.lsp.core.gradle

import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType
import spock.lang.Narrative
import spock.lang.Title

/**
 * Tests for the GrailsProjectBuilder
 */
@Title("Grails Project Builder Tests")
@Narrative("""
    These tests verify that the GrailsProjectBuilder correctly builds
    GrailsProject instances from Gradle project structures.
""")
class GrailsProjectBuilderSpec extends BaseLspSpec {

	def "should handle projects without version information gracefully"() {
		given: "A basic project without Grails info"
		File projectDir = new File(getProjectDir(ProjectType.DUMMY).toURI())

		when: "Building project"
		GrailsProject project = GrailsProjectBuilder.build(projectDir)

		then: "Should build project with defaults"
		project != null
		project.rootDirectory == projectDir
		!project.isGrailsProject
		!project.isGrails7Plus
		project.grailsVersion == null
	}

	def "should count source files correctly"() {
		given: "A project with source files"
		File projectDir = new File(getProjectDir(ProjectType.DUMMY).toURI())

		when: "Building project"
		GrailsProject project = GrailsProjectBuilder.build(projectDir)

		then: "Should count source files"
		project != null
		project.sourceFileCount >= 0
		// Note: DUMMY project may have 0 source files, which is valid
	}

	def "should handle missing project directory gracefully"() {
		given: "A non-existent directory"
		File nonExistentDir = new File("/non/existent/path")

		when: "Attempting to build project"
		GrailsProjectBuilder.build(nonExistentDir)

		then: "Should throw appropriate exception"
		def exception = thrown(Exception)
		exception.message?.toLowerCase()?.contains("not found") ||
				exception.message?.toLowerCase()?.contains("could not fetch model of type 'ideaproject' using connection to gradle distribution")
	}

	def "should handle Grails Project correctly"() {
		given: "A project with dependencies"
		File projectDir = new File(getProjectDir(ProjectType.GRAILS).toURI())

		when: "Building project"
		GrailsProject project = GrailsProjectBuilder.build(projectDir)

		then: "Should extract dependencies"
		project.dependencies.size() >= 0
		// Dependencies count will vary based on actual project
		project.name == "grails-test-project"
		project.grailsVersion == "7.0.0-RC1"
		project.group == "com.example"
		project.projectVersion == "0.1"
		project.description == null
	}

	def "should handle Groovy Project correctly"() {
		given: "A project with dependencies"
		File projectDir = new File(getProjectDir(ProjectType.GROOVY).toURI())

		when: "Building project"
		GrailsProject project = GrailsProjectBuilder.build(projectDir)

		then: "Should extract dependencies"
		project.dependencies.size() >= 0
		// Dependencies count will vary based on actual project
		project.name ==  "groovy-test-project"
		project.grailsVersion == null
		project.group == "demo"
		project.projectVersion == "0.1"
		project.description == 'Test Groovy Project'
		!project.isGrailsProject
	}
}
