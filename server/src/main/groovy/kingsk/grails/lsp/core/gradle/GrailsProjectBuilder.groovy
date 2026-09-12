package kingsk.grails.lsp.core.gradle

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GrailsProject
import kingsk.grails.lsp.model.dto.ProjectDependencyEdge
import kingsk.grails.lsp.model.dto.SourceSetModel
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaContentRoot
import org.gradle.tooling.model.idea.IdeaDependency
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
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
	private static final String GROUP_REGEX = /group\s*=\s*(['"])([A-Za-z0-9]+(?:\.[A-Za-z0-9]+)*)\1/
	private static final String VERSION_REGEX = /version\s*=\s*(['"])([0-9]+(?:\.[0-9]+)*(?:-[A-Za-z0-9]+)?)\1/
	private static final String DESCRIPTION_REGEX = /description\s*=\s*(['"])(.+?)\1/

	static GrailsProject build(File projectDir, org.gradle.tooling.CancellationTokenSource cancellationSource = null) {
		try (ProjectConnection connection = GradleConnector.newConnector()
				.useBuildDistribution()
				.forProjectDirectory(projectDir)
				.connect()) {

			org.gradle.tooling.ModelBuilder<IdeaProject> modelBuilder = connection.model(IdeaProject)
			if (cancellationSource != null) {
				modelBuilder.withCancellationToken(cancellationSource.token())
			}
			IdeaProject ideaProject = modelBuilder.get()
			GrailsProject project = fromIdeaProject(ideaProject, projectDir)

			// Enrich with real evaluated source-set membership and project edges via init-script exporter
			try {
				SourceSetExportResult exported = SourceSetExporter.exportProjectData(connection, projectDir, cancellationSource)
				if (exported != null) {
					if (exported.sourceSets && !exported.sourceSets.isEmpty()) {
						log.info("[GRADLE] Merging ${exported.sourceSets.size()} evaluated source sets from init-script export into ${project.name}")
						project.sourceSets.putAll(exported.sourceSets)
					}
					if (exported.projectDependencies && !exported.projectDependencies.isEmpty()) {
						project.projectDependencies.addAll(exported.projectDependencies)
					}
					if (exported.buildRoot != null) {
						project.buildRoot = exported.buildRoot
					}
					if (exported.gradleProjectPath != null) {
						project.gradleProjectPath = exported.gradleProjectPath
					}
				}
			} catch (Exception e) {
				log.warn("[GRADLE] Source-set init-script export skipped/failed: ${e.message}")
			}

			log.info("[GRADLE] Successfully built project: ${project.name} (${project.dependencies.size()} dependencies, ${project.sourceSets.size()} source sets)")
			return project
		}
	}

	static GrailsProject fromIdeaProject(IdeaProject ideaProject, File targetProjectDir = null) {
		IdeaModule targetModule = null
		if (targetProjectDir != null && ideaProject.modules != null) {
			String targetCanonical = targetProjectDir.canonicalPath
			targetModule = ideaProject.modules.find { mod ->
				mod.contentRoots?.any { cr ->
					cr.rootDirectory != null && isSameOrChildPath(targetCanonical, cr.rootDirectory.canonicalPath)
				}
			}
		}
		if (targetModule == null && ideaProject.modules != null && !ideaProject.modules.isEmpty()) {
			targetModule = ideaProject.modules.first()
		}

		File resolvedRoot = targetProjectDir ?: (targetModule?.contentRoots?.getAt(0)?.rootDirectory)
		String resolvedName = targetModule?.name ?: ideaProject.name

		GrailsProject project = new GrailsProject(
				name: resolvedName,
				description: ideaProject.description,
				javaHome: ideaProject.javaLanguageSettings?.jdk?.javaHome,
				targetCompatibility: ideaProject.javaLanguageSettings?.targetBytecodeVersion,
				javaVersion: ideaProject.javaLanguageSettings?.languageLevel,
				rootDirectory: resolvedRoot,
				dependencies: [] as Set,
				sourceDirectories: [] as Set,
				resourceDirectories: [] as Set,
				testDirectories: [] as Set,
				testResourceDirectories: [] as Set,
				generatedSourceDirectories: [] as Set,
				excludeDirectories: [] as Set
		)

		if (targetModule != null) {
			try {
				GradleProject gp = targetModule.gradleProject
				if (gp != null) {
					project.gradleProjectPath = gp.path
					GradleProject rootGp = gp
					while (rootGp.parent != null) {
						rootGp = rootGp.parent
					}
					project.buildRoot = rootGp.projectDirectory
				}
			} catch (Throwable ignored) {}
		}

		List<IdeaModule> modulesToProcess = targetModule != null ? [targetModule] : (ideaProject.modules as List<IdeaModule> ?: [])
		modulesToProcess.each { IdeaModule module ->
			module.contentRoots.each { IdeaContentRoot root ->
				if (project.rootDirectory == null) {
					project.rootDirectory = root.rootDirectory
				}
				root.sourceDirectories?.each { srcDir ->
					if (srcDir?.directory != null) {
						project.sourceDirectories.add(srcDir.directory)
						try {
							if (srcDir.isGenerated()) {
								project.generatedSourceDirectories.add(srcDir.directory)
							}
						} catch (Throwable ignored) {}
					}
				}
				root.testDirectories?.each { testDir ->
					if (testDir?.directory != null) {
						project.testDirectories.add(testDir.directory)
						try {
							if (testDir.isGenerated()) {
								project.generatedSourceDirectories.add(testDir.directory)
							}
						} catch (Throwable ignored) {}
					}
				}
				try {
					project.resourceDirectories.addAll(root.resourceDirectories*.directory as Set<File>)
				} catch (Throwable ignored) {}
				try {
					project.testResourceDirectories.addAll(root.testResourceDirectories*.directory as Set<File>)
				} catch (Throwable ignored) {}
				project.excludeDirectories.addAll(root.excludeDirectories ?: [] as Set<File>)
			}

			module.dependencies?.each { IdeaDependency dep ->
				if (dep instanceof IdeaSingleEntryLibraryDependency) {
					IdeaSingleEntryLibraryDependency libDep = (IdeaSingleEntryLibraryDependency) dep
					if (libDep.gradleModuleVersion != null) {
						def d = new DependencyNode(
								libDep.gradleModuleVersion.name,
								libDep.gradleModuleVersion.group,
								libDep.gradleModuleVersion.version,
								libDep.scope?.scope,
								libDep.file,
								libDep.source,
								libDep.javadoc
						)
						project.dependencies << d
					}
				} else if (dep instanceof IdeaModuleDependency) {
					try {
						IdeaModuleDependency modDep = (IdeaModuleDependency) dep
						String targetModName = modDep.targetModuleName
						File targetModDir = null
						File targetBuildRoot = null
						String targetPath = null

						if (targetModName != null && ideaProject.modules != null) {
							IdeaModule matched = ideaProject.modules.find { it.name == targetModName }
							if (matched != null) {
								if (matched.contentRoots && !matched.contentRoots.isEmpty()) {
									targetModDir = matched.contentRoots.iterator().next()?.rootDirectory
								}
								try {
									if (matched.gradleProject != null) {
										targetPath = matched.gradleProject.path
										GradleProject rootGp = matched.gradleProject
										while (rootGp.parent != null) {
											rootGp = rootGp.parent
										}
										targetBuildRoot = rootGp.projectDirectory
									}
								} catch (Throwable ignored) {}
							}
						}

						if (targetModName != null || targetModDir != null || targetPath != null) {
							project.addProjectDependency(new ProjectDependencyEdge(
									targetPath,
									targetBuildRoot ?: project.buildRoot,
									targetModDir,
									targetModName,
									null
							))
						}
					} catch (Throwable modEx) {
						log.warn("[GRADLE] Error resolving IdeaModuleDependency: ${modEx.message}")
					}
				}
			}
		}

		// Build baseline SourceSetModel instances from IDEA module metadata
		buildIdeaSourceSetModels(project, targetModule)

		// Extract Grails version information
		extractGrailsVersionInfo(project)

		project.sourceFileCount = getFileCount(project.sourceDirectories, ".groovy")

		return project
	}

	private static void buildIdeaSourceSetModels(GrailsProject project, IdeaModule module) {
		if (project == null) return
		File root = project.rootDirectory

		List<File> mainCompileCp = []
		List<File> testCompileCp = []
		List<File> mainRuntimeCp = []
		List<File> testRuntimeCp = []

		File mainOut = null
		File testOut = null
		try {
			mainOut = module?.compilerOutput?.outputDir
			testOut = module?.compilerOutput?.testOutputDir
		} catch (Throwable ignored) {}

		if (mainOut != null) {
			testCompileCp.add(mainOut)
		}

		project.dependencies?.each { dep ->
			if (dep?.jarFileClasspath != null) {
				String scope = dep.scope?.trim()?.toUpperCase() ?: "COMPILE"
				if (scope in ["COMPILE", "PROVIDED"]) {
					mainCompileCp.add(dep.jarFileClasspath)
					testCompileCp.add(dep.jarFileClasspath)
				} else if (scope == "RUNTIME") {
					mainRuntimeCp.add(dep.jarFileClasspath)
					testRuntimeCp.add(dep.jarFileClasspath)
				} else if (scope == "TEST") {
					testCompileCp.add(dep.jarFileClasspath)
					testRuntimeCp.add(dep.jarFileClasspath)
				}
			}
		}

		SourceSetModel mainModel = new SourceSetModel(
				"main",
				":",
				root,
				root,
				project.sourceDirectories,
				project.resourceDirectories,
				project.generatedSourceDirectories.findAll { project.sourceDirectories.contains(it) },
				mainCompileCp,
				mainRuntimeCp,
				mainOut != null ? [mainOut] : [],
				[],
				true,
				[]
		)

		SourceSetModel testModel = new SourceSetModel(
				"test",
				":",
				root,
				root,
				project.testDirectories,
				project.testResourceDirectories,
				project.generatedSourceDirectories.findAll { project.testDirectories.contains(it) },
				testCompileCp,
				testRuntimeCp,
				testOut != null ? [testOut] : [],
				[],
				true,
				[]
		)

		project.sourceSets.put("main", mainModel)
		project.sourceSets.put("test", testModel)
	}

	private static boolean isSameOrChildPath(String childPath, String parentPath) {
		if (!childPath || !parentPath) return false
		String c = childPath.replace('/', File.separator).replace('\\', File.separator)
		String p = parentPath.replace('/', File.separator).replace('\\', File.separator)
		if (c.equalsIgnoreCase(p)) return true
		if (!p.endsWith(File.separator)) {
			p = p + File.separator
		}
		return c.length() > p.length() && c.substring(0, p.length()).equalsIgnoreCase(p)
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
                def matcher = (content =~ regex)
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
