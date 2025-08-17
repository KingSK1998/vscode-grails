package kingsk.grails.lsp.core.gradle

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

import java.nio.file.Files
import java.util.regex.Matcher

@Slf4j
@CompileStatic
class GrailsProjectBuilder {
	
	// Performance optimization - reuse Gradle init script
	private static final String ARTIFACT_RESOLVER_SCRIPT = """
initscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}
allprojects {
    task resolveArtifact {
        doLast {
            try {
				def classifier = project.findProperty('c')
				def base = "\${project.findProperty('g')}:\${project.findProperty('n')}:\${project.findProperty('v')}"
                def notation = classifier ? "\${base}:\${classifier}@jar" : "\${base}@jar"
                def dep = dependencies.create(notation)
                def config = configurations.detachedConfiguration(dep)
                config.resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
                def files = config.resolve()
                if (files && !files.isEmpty()) {
                    println "ARTIFACT_PATH=" + files.iterator().next().absolutePath
                } else {
                    println "ARTIFACT_NOT_FOUND"
                }
            } catch (Exception e) {
                println "ARTIFACT_ERROR=" + e.message
            }
        }
    }
}
"""
	
	private static final String GRAILS_VERSION_REGEX = /grailsVersion\s*=\s*(['"])([^'"]+)\1/
	private static final String GROUP_REGEX = /group\s*=\s*(['"])([a-zA-Z0-9]+(?:\.[a-zA-Z0-9]+)+)\1/
	private static final String VERSION_REGEX = /version\s*=\s*(['"])([0-9]+(?:\.[0-9]+)*(?:-[A-Za-z0-9]+)?)\1/
	private static final String DESCRIPTION_REGEX = /description\s*=\s*(['"])(.+?)\1/
	
	static GrailsProject build(File projectDir) {
		try (ProjectConnection connection = GradleConnector.newConnector()
				.useBuildDistribution()
				.forProjectDirectory(projectDir)
				.connect()) {
			
			IdeaProject ideaProject = connection.getModel(IdeaProject)
			GrailsProject project = fromIdeaProject(ideaProject)
			log.info("[GRADLE] Successfully built project: ${project.name} (${project.dependencies.size()} dependencies)")
			return project
		}
	}
	
	private static GrailsProject fromIdeaProject(IdeaProject ideaProject) {
		GrailsProject project = new GrailsProject(
				name: ideaProject.name,
				description: ideaProject.description,
				javaHome: ideaProject.javaLanguageSettings.jdk?.javaHome,
				targetCompatibility: ideaProject.javaLanguageSettings.targetBytecodeVersion,
				javaVersion: ideaProject.javaLanguageSettings.languageLevel,
				dependencies: [] as Set,
				sourceDirectories: [] as Set,
				resourceDirectories: [] as Set,
				testDirectories: [] as Set,
				testResourceDirectories: [] as Set,
				excludeDirectories: [] as Set
		)
		
		ideaProject.modules.each { IdeaModule module ->
			module.contentRoots.each { IdeaContentRoot root ->
				// gets all directories
				project.rootDirectory = root.rootDirectory
				// gets only source directories i.e. controller, services, domains, etc
				project.sourceDirectories.addAll(root.sourceDirectories*.directory as Set<File>)
				// gets only resource directories i.e. views, i18n, etc
				project.resourceDirectories.addAll(root.resourceDirectories*.directory as Set<File>)
				// gets all exclude directories
				project.excludeDirectories.addAll(root.excludeDirectories)
				// gets all test directories
				project.testDirectories.addAll(root.testDirectories*.directory as Set<File>)
				// gets all test resource directories
				project.testResourceDirectories.addAll(root.testResourceDirectories*.directory as Set<File>)
			}
			
			(module.dependencies as List<IdeaSingleEntryLibraryDependency>).each {
				def dep = new DependencyNode(
						it.gradleModuleVersion.name,
						it.gradleModuleVersion.group,
						it.gradleModuleVersion.version,
						it.scope?.scope,
						it.file,
						it.source,
						it.javadoc
				)
				project.dependencies << dep
			}
		}
		
		// Extract Grails version information
		extractGrailsVersionInfo(project)
		
		project.sourceFileCount = getFileCount(project.sourceDirectories, ".groovy")
		// project.javaFileCount = getFileCount(project.sourceDirectories, ".java")
		
		return project
	}
	
	private static void extractGrailsVersionInfo(GrailsProject project) {
		if (!project.rootDirectory) return
		
		// Try gradle.properties first
		File gradlePropertiesFile = new File(project.rootDirectory, "gradle.properties")
		if (gradlePropertiesFile.exists()) {
			def properties = new Properties()
			gradlePropertiesFile.withInputStream { properties.load(it) }
			
			project.isGrailsProject = properties.containsKey("grailsVersion")
			project.grailsVersion = project.grailsVersion ?: properties.getProperty("grailsVersion")
			project.groovyVersion = project.groovyVersion ?: properties.getProperty("groovyVersion")
			project.projectVersion = project.projectVersion ?: properties.getProperty("version")
		}
		
		// Try build.gradle for version info
		File buildGradleFile = new File(project.rootDirectory, "build.gradle")
		if (buildGradleFile.exists()) {
			extractVersionsFromBuildGradle(project, buildGradleFile.text)
		}
		
		// Fallback: use Groovy runtime version
		if (!project.groovyVersion) {
			try {
				project.groovyVersion = GroovySystem.version
			} catch (Throwable ignored) {
				// Leave null if even runtime version isn't accessible
			}
		}
		
		if (project.grailsVersion) {
			try {
				// Extract the first numeric component (major version)
				def matcher = project.grailsVersion =~ /(\d+)/
				if (matcher.find()) {
					int major = matcher.group(1).toInteger()
					project.isGrails7Plus = major >= 7
				} else {
					project.isGrails7Plus = false
				}
			} catch (Exception ignored) {
				project.isGrails7Plus = false
			}
		}
	}
	
	private static void extractVersionsFromBuildGradle(GrailsProject project, String content) {
		[//grailsVersion: GRAILS_VERSION_REGEX,
		 group         : GROUP_REGEX,
		 projectVersion: VERSION_REGEX,
		 description   : DESCRIPTION_REGEX
		].each { key, regex ->
			if (!project[key]) {
				Matcher matcher = content =~ regex
				if (matcher.find()) {
					// matcher[0] is the full match, [2] is the capturing group
					project[key] = matcher.group(2)
				}
			}
		}
	}
	
	private static int getFileCount(Set<File> directories, String extension) {
		if (!directories) return 0
		int total = 0
		directories.findAll { it?.exists() && it.directory }.each { dir ->
			dir.eachFileRecurse { file ->
				if (file.name.endsWith(extension)) total++
			}
		}
		return total
	}
	
	/**
	 * Internal artifact download implementation with optimized Gradle connection handling
	 */
	static File downloadArtifactInternal(File projectDir, DependencyNode dependency, String classifier) {
		// Input validation
		if (!dependency || !dependency.group?.trim() || !dependency.name?.trim() || !dependency.version?.trim()) {
			log.warn("[GRADLE] Invalid dependency: ${dependency?.group}:${dependency?.name}:${dependency?.version}")
			return null
		}
		if (!classifier?.trim()) {
			log.warn("[GRADLE] Invalid classifier for artifact: ${dependency.group}:${dependency.name}:${dependency.version}:${classifier}")
			return null
		}
		
		if (!projectDir) {
			log.warn("[GRADLE] No project loaded, cannot download artifact: ${dependency}:${classifier}")
			return null
		}
		
		long startTime = System.currentTimeMillis()
		
		// Create optimized temporary init script
		File initScript = File.createTempFile("grails-lsp-artifact-", ".gradle")
		
		try {
			initScript.text = ARTIFACT_RESOLVER_SCRIPT
			
			try (ProjectConnection connection = GradleConnector.newConnector()
					.useBuildDistribution()
					.forProjectDirectory(projectDir)
					.connect()) {
				
				String output = runTaskResolveArtifact(connection, initScript, dependency, classifier)
				File result = parseArtifactOutput(output, dependency, classifier)
				
				long duration = System.currentTimeMillis() - startTime
				if (result) {
					log.info("[GRADLE] Artifact resolved in ${duration}ms: ${dependency}:${classifier}")
				} else {
					log.warn("[GRADLE] Artifact resolution failed in ${duration}ms: ${dependency}:${classifier}")
				}
				
				return result
			}
		} catch (Exception e) {
			log.warn("[GRADLE] Artifact download failed: ${e.message}")
			return null
		} finally {
			try {
				Files.deleteIfExists(initScript.toPath())
			} catch (IOException ignored) {
				// Ignore cleanup errors
			}
		}
	}
	
	private static String runTaskResolveArtifact(ProjectConnection connection, File initScript, DependencyNode dependency, String classifier) {
		ByteArrayOutputStream output = new ByteArrayOutputStream()
		// run task
		connection.newBuild()
				.forTasks("resolveArtifact")
				.withArguments(
						"--init-script", initScript.absolutePath,
						"--quiet", // Reduce output noise
						"-Pg=${dependency.group}",
						"-Pn=${dependency.name}",
						"-Pv=${dependency.version}",
						"-Pc=${classifier}"
				)
				.setStandardOutput(output)
				.run()
		return output.toString("UTF-8")
	}
	
	/**
	 * Parse Gradle output for artifact resolution results
	 */
	private static File parseArtifactOutput(String outputText, DependencyNode dependency, String classifier) {
		def lines = outputText.readLines()
		
		def pathLine = lines.find { it.startsWith("ARTIFACT_PATH=") }
		def notFoundLine = lines.find { it.startsWith("ARTIFACT_NOT_FOUND") }
		def errorLine = lines.find { it.startsWith("ARTIFACT_ERROR=") }
		
		if (pathLine) {
			String artifactPath = pathLine.replace("ARTIFACT_PATH=", "").trim()
			// Verify file exists before returning
			File classpath = new File(artifactPath)
			if (classpath.exists()) return classpath
			log.warn("[GRADLE] Resolved artifact path does not exist: ${artifactPath}")
		} else if (notFoundLine) {
			log.warn("[GRADLE] Artifact not found: ${dependency}:${classifier}")
		} else if (errorLine) {
			String errorMsg = errorLine.replace("ARTIFACT_ERROR=", "").trim()
			log.warn("[GRADLE] Artifact resolution error: ${errorMsg}")
		} else {
			log.info("[GRADLE] No artifact resolution output received")
		}
		return null
	}
}
