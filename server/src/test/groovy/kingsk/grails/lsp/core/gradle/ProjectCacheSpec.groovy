package kingsk.grails.lsp.core.gradle

import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Tests for the BinaryProjectCache
 */
class ProjectCacheSpec extends Specification {
	
	@TempDir
	Path tempDir
	
	private ProjectCache cache
	private File projectDir
	private GrailsProject testProject
	
	def setup() {
		cache = new ProjectCache()
		projectDir = tempDir.toFile()
		
		// Create a test project
		testProject = new GrailsProject(
				name: "TestProject",
				version: 1,
				group: "com.example",
				description: "Test project for cache testing",
				rootDirectory: projectDir,
				dependencies: [
						new DependencyNode("test-lib", "com.test", "1.0.0", "compile", null, null, null)
				] as Set
		)
	}
	
	def cleanup() {
		// Clean up cache files created during tests
		File cacheFile = cache.getCacheFile(projectDir)
		cacheFile?.delete()
		cacheFile?.parentFile?.delete() // Remove .grails-lsp directory
		
		// Clean up test files
		new File(projectDir, "build.gradle")?.delete()
		new File(projectDir, "gradle.properties")?.delete()
	}
	
	def "should save and load project successfully"() {
		when: "Saving project to cache"
		cache.save(projectDir, testProject)
		
		and: "Loading project from cache"
		GrailsProject loaded = cache.load(projectDir)
		
		then: "Loaded project should match saved project"
		loaded != null
		loaded.name == testProject.name
		loaded.group == testProject.group
		loaded.description == testProject.description
		loaded.dependencies.size() == testProject.dependencies.size()
		
		cleanup: "Clean up cache file"
		File cacheFile = cache.getCacheFile(projectDir)
		cacheFile?.delete()
		cacheFile?.parentFile?.delete()
	}
	
	def "should return null when cache file doesn't exist"() {
		when: "Loading from non-existent cache"
		GrailsProject loaded = cache.load(projectDir)
		
		then: "Should return null"
		loaded == null
	}
	
	def "should detect stale cache when build.gradle is newer"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		Thread.sleep(10) // Ensure time difference
		
		and: "A newer build.gradle file"
		File buildGradle = new File(projectDir, "build.gradle")
		buildGradle.createNewFile()
		buildGradle.setLastModified(System.currentTimeMillis() + 1000)
		
		when: "Checking if cache is stale"
		boolean isStale = cache.isStale(projectDir)
		
		then: "Cache should be stale"
		isStale
		
		cleanup: "Clean up files"
		buildGradle.delete()
		File cacheFile = cache.getCacheFile(projectDir)
		cacheFile?.delete()
		cacheFile?.parentFile?.delete()
	}
	
	def "should detect stale cache when gradle.properties is newer"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		Thread.sleep(10) // Ensure time difference
		
		and: "A newer gradle.properties file"
		File gradleProps = new File(projectDir, "gradle.properties")
		gradleProps.createNewFile()
		gradleProps.setLastModified(System.currentTimeMillis() + 1000)
		
		when: "Checking if cache is stale"
		boolean isStale = cache.isStale(projectDir)
		
		then: "Cache should be stale"
		isStale
	}
	
	def "should detect valid cache when no newer files exist"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		
		when: "Checking if cache is stale"
		boolean isStale = cache.isStale(projectDir)
		
		then: "Cache should not be stale"
		!isStale
	}
	
	def "should handle version mismatch gracefully"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		
		when: "Bumping cache version and loading"
		cache.bumpUpVersion()
		GrailsProject loaded = cache.load(projectDir)
		
		then: "Should return null due to version mismatch"
		loaded == null
	}
	
	def "should force invalidate cache"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		
		when: "Force invalidating cache"
		cache.forceInvalidation(projectDir)
		
		and: "Attempting to load"
		GrailsProject loaded = cache.load(projectDir)
		
		then: "Cache should be gone"
		!cache.cacheExists(projectDir)
		loaded == null
	}
	
	def "should provide cache information"() {
		given: "A cached project"
		cache.save(projectDir, testProject)
		
		when: "Getting cache information"
		boolean exists = cache.cacheExists(projectDir)
		long age = cache.getCacheAge(projectDir)
		long size = cache.getCacheSize(projectDir)
		
		then: "Cache info should be accurate"
		exists
		age >= 0
		size > 0
	}
	
	def "should handle corrupted cache file gracefully"() {
		given: "A corrupted cache file"
		File cacheFile = cache.getCacheFile(projectDir)
		cacheFile.parentFile.mkdirs()
		cacheFile.createNewFile()
		cacheFile.text = "corrupted data"
		
		when: "Attempting to load"
		GrailsProject loaded = cache.load(projectDir)
		
		then: "Should return null without throwing exception"
		loaded == null
	}
	
	def "should increment version correctly"() {
		given: "Initial cache version"
		int initialVersion = cache.currentVersion
		
		when: "Bumping version"
		cache.bumpUpVersion()
		
		then: "Version should be incremented"
		cache.currentVersion == initialVersion + 1
	}
}