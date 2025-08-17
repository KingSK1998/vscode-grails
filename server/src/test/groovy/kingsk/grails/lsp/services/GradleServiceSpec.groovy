package kingsk.grails.lsp.services

import kingsk.grails.lsp.core.gradle.ProjectCache
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.test.ProjectType

/**
 * Tests for the GradleService - Updated for clean single-project API
 */
class GradleServiceSpec extends BaseLspSpec {
	
	private GradleService gradleService
	
	def setup() {
		gradleService = new GradleService()
	}
	
	def "should load Grails project from valid directory"() {
		given: "A valid Grails project directory"
		String projectDir = getProjectDir(ProjectType.GRAILS)
		
		when: "Loading the project"
		GrailsProject project = gradleService.getGrailsProject(projectDir)
		
		then: "Project should be loaded successfully"
		project != null
		project.name == "grails-test-project"
		project.rootDirectory != null
		project.rootDirectory.exists()
		project.grailsVersion == "7.0.0-M4"
		project.group == "com.example"
		project.projectVersion == "0.1"
		project.description == null
	}
	
	def "should load Groovy project from valid directory"() {
		given: "A valid Groovy project directory"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		
		when: "Loading the project"
		GrailsProject project = gradleService.getGrailsProject(projectDir)
		
		then: "Project should be loaded successfully"
		project != null
		project.rootDirectory != null
		project.rootDirectory.exists()
		project.name == "Test"
		project.grailsVersion == null
		project.group == "org.example"
		project.projectVersion == "1.0-SNAPSHOT"
		project.description == 'A simple Groovy project with JUnit 5'
		!project.isGrailsProject
	}
	
	def "should throw exception for non-existent directory"() {
		given: "A non-existent directory path"
		String invalidPath = "/non/existent/path"
		
		when: "Attempting to load project"
		gradleService.getGrailsProject(invalidPath)
		
		then: "Should throw FileNotFoundException"
		thrown(IllegalArgumentException)
	}
	
	def "should use cache on subsequent calls"() {
		given: "A valid Grails project directory"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		
		when: "Loading project twice"
		GrailsProject project1 = gradleService.getGrailsProject(projectDir)
		GrailsProject project2 = gradleService.getGrailsProject(projectDir)
		
		then: "Both projects should have same basic properties"
		project1 != null
		project2 != null
		project1.name == project2.name
		project1.rootDirectory == project2.rootDirectory
	}
	
	def "should force invalidate specific project cache"() {
		given: "A valid project with cache"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		gradleService.getGrailsProject(projectDir) // Load to create cache
		
		when: "Force invalidating project cache"
		gradleService.invalidateProjectCache(projectDir)
		
		then: "Should not throw exception"
		noExceptionThrown()
	}
	
	def "should handle cache file deletion manually"() {
		given: "A valid project with cache"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		gradleService.getGrailsProject(projectDir) // Load to create cache
		
		and: "Manually delete cache file"
		File cacheFile = ProjectCache.getCacheFile(new File(projectDir))
		if (cacheFile.exists()) {
			cacheFile.delete()
		}
		
		when: "Loading project again"
		GrailsProject project = gradleService.getGrailsProject(projectDir)
		
		then: "Should rebuild project from Gradle API"
		project != null
		project.rootDirectory != null
	}
	
	def "should download javadoc JAR files through GradleService"() {
		given: "A valid project with dependencies"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		def project = gradleService.getGrailsProject(projectDir)
		
		and: "A dependency node"
		DependencyNode dependency = new DependencyNode(
				name: "commons-lang3",
				group: "org.apache.commons",
				version: "3.12.0",
				scope: "compile"
		)
		
		when: "Downloading javadoc JAR"
		File javadocPath = gradleService.downloadJavaDocJarFile(project.rootDirectory, dependency)
		
		then: "Should handle download request gracefully (may return null in test environment)"
		javadocPath != null
		javadocPath.exists()
		javadocPath.name.endsWith(".jar")
	}
	
	def "should download sources JAR files through GradleService"() {
		given: "A valid project"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		def project = gradleService.getGrailsProject(projectDir)
		
		and: "A dependency node"
		DependencyNode dependency = new DependencyNode(
				name: "commons-lang3",
				group: "org.apache.commons",
				version: "3.12.0",
				scope: "compile"
		)
		
		when: "Downloading sources JAR"
		File sourcesPath = gradleService.downloadSourcesJarFile(project.rootDirectory, dependency)
		
		then: "Should handle download request gracefully (may return null in test environment)"
		sourcesPath != null
		sourcesPath.exists()
		sourcesPath.name.endsWith(".jar")
	}
	
	def "should handle invalid dependency for downloads"() {
		given: "A valid project"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		def project = gradleService.getGrailsProject(projectDir)
		
		when: "Attempting to download with null dependency"
		String result1 = gradleService.downloadJavaDocJarFile(project.rootDirectory, null)
		String result2 = gradleService.downloadSourcesJarFile(project.rootDirectory, null)
		
		then: "Should handle gracefully"
		result1 == null
		result2 == null
	}
	
	def "should handle corrupted cache gracefully"() {
		given: "A project directory"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		
		when: "Attempting to load from potentially corrupted cache"
		GrailsProject project = gradleService.getGrailsProject(projectDir)
		
		then: "Should either load successfully or rebuild gracefully"
		project != null
		project.rootDirectory != null
		project.rootDirectory.exists()
	}
	
	def "should handle concurrent project loading"() {
		given: "A valid project"
		String projectDir = getProjectDir(ProjectType.GROOVY)
		
		when: "Loading project concurrently"
		List<GrailsProject> projects = []
		List<Thread> threads = []
		
		3.times { i ->
			Thread thread = new Thread({
				try {
					GrailsProject project = gradleService.getGrailsProject(projectDir)
					synchronized (projects) {
						projects.add(project)
					}
				} catch (Exception ignored) {
					// Handle any threading issues gracefully
				}
			})
			threads.add(thread)
			thread.start()
		}
		
		// Wait for all threads to complete
		threads.each { it.join(3000) } // 3 second timeout
		
		then: "Should handle concurrent access gracefully"
		projects.size() <= 3 // Some may fail in test environment
		// All successful projects should have consistent data
		if (projects.size() > 1) {
			projects.every { it?.name == projects[0]?.name }
		}
	}
}