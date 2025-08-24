package kingsk.grails.lsp.core.gradle

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject

/**
 * Simple binary cache using Java serialization - faster and smaller than JSON
 * Cache is stored in project root under .grails-lsp/ directory
 */
@Slf4j
@CompileStatic
class ProjectCache {
	private static final String CACHE_DIR_NAME = ".grails-lsp"
	private static final String PROJECT_CACHE_FILE = "projectInfo.cache"
    private static final String PROJECT_JSON_FILE = "projectInfo.json"

	// Increment when project structure changes
	private int currentVersion = 1

    /* -------------- public API remains unchanged -------------------- */

	GrailsProject load(File projectDir) {
		File cacheFile = getCacheFile(projectDir)
		if (!cacheFile.exists()) {
			log.debug("[GRADLE] No binary cache file found for: ${projectDir.name}")
			return null
		}

		try {
			cacheFile.withObjectInputStream { ois ->
				int version = ois.readInt()
				if (version != currentVersion) {
					log.info("[GRADLE] Cache version mismatch (${version} vs ${currentVersion}), invalidating: ${projectDir.name}")
					return null
				}

				GrailsProject project = ois.readObject() as GrailsProject
				log.info("[GRADLE] Loaded project from binary cache: ${project.name} (${project.dependencies.size()} dependencies)")
				return project
			}
		} catch (Exception e) {
			log.warn("[GRADLE] Failed to load binary cache for ${projectDir.name}, will rebuild", e)
			return null
		}
	}

	void save(File projectDir, GrailsProject grailsProject) {
		try {
			File cacheFile = getCacheFile(projectDir)
			cacheFile.withObjectOutputStream { oos ->
				oos.writeInt(currentVersion)
				oos.writeObject(grailsProject)
			}

            // 2. JSON side-car for VS Code
            Map dto = toDto(grailsProject, projectDir)
            File cacheDir = new File(projectDir, CACHE_DIR_NAME)
            new File(cacheDir, PROJECT_JSON_FILE).text = JsonOutput.prettyPrint(JsonOutput.toJson(dto))

			BigDecimal sizeKB = cacheFile.length() / 1024
			log.info("[GRADLE] Saved project to binary cache: ${grailsProject.name} (${sizeKB}KB, ${grailsProject.dependencies.size()} deps)")
		} catch (Exception e) {
			log.error("[GRADLE] Failed to save binary cache for ${grailsProject.name}", e)
		}
	}

	static boolean isStale(File projectDir) {
		File cacheFile = getCacheFile(projectDir)
		if (!cacheFile.exists()) {
			log.debug("[GRADLE] Binary cache is stale - file does not exist: ${projectDir.name}")
			return true
		}

		// Simple timestamp check against key project files
		long cacheTime = cacheFile.lastModified()

		File buildGradle = new File(projectDir, "build.gradle")
		if (buildGradle.exists() && buildGradle.lastModified() > cacheTime) {
			log.info("[GRADLE] Binary cache is stale - build.gradle newer: ${projectDir.name}")
			return true
		}

		File gradleProperties = new File(projectDir, "gradle.properties")
		if (gradleProperties.exists() && gradleProperties.lastModified() > cacheTime) {
			log.info("[GRADLE] Binary cache is stale - gradle.properties newer: ${projectDir.name}")
			return true
		}

		log.debug("[GRADLE] Binary cache is valid for: ${projectDir.name}")
		return false
	}

	void bumpUpVersion() {
		currentVersion++
		log.info("[GRADLE] Bumped binary cache version to: ${currentVersion}")
	}

	int getCurrentVersion() {
		return currentVersion
	}

	static void forceInvalidation(File projectDir) {
		File cacheFile = getCacheFile(projectDir)
		if (cacheFile.exists()) {
			boolean deleted = cacheFile.delete()
			log.info("[GRADLE] Force invalidated binary cache for project: ${projectDir.name}, deleted: ${deleted}")
		}
	}

	static boolean cacheExists(File projectDir) {
		return getCacheFile(projectDir).exists()
	}

	static long getCacheAge(File projectDir) {
		File cacheFile = getCacheFile(projectDir)
		if (!cacheFile.exists()) return -1
		return System.currentTimeMillis() - cacheFile.lastModified()
	}

	static long getCacheSize(File projectDir) {
		File cacheFile = getCacheFile(projectDir)
		return cacheFile.exists() ? cacheFile.length() : 0
	}

	/**
	 * Get cache file path in project root under .grails-lsp directory
	 */
	static File getCacheFile(File projectDir) {
		File cacheDir = new File(projectDir, CACHE_DIR_NAME)
		if (!cacheDir.exists()) {
			cacheDir.mkdirs()
		}
		return new File(cacheDir, PROJECT_CACHE_FILE)
	}

    /* ---------------- helper to produce a tiny DTO ------------------ */
    private static Map toDto(GrailsProject gp, File root) {
        [
            id            : root.absolutePath,
            rootPath      : root.absolutePath,
            name          : gp.name,
            type          : gp.isGrailsPlugin ? "grails-plugin" :
                            (gp.isGrailsProject ? "grails" : "groovy"),
            grailsVersion : gp.grailsVersion,
            pluginVersion : gp.pluginVersion,
            groovyVersion : gp.groovyVersion,
            dependencies  : gp.dependencies.collect { d ->
                [ group: d.group, name: d.name, version: d.version ]
            },
            artifactCounts: [
                controllers: countSources(root, "controllers"),
                services   : countSources(root, "services"),
                domain     : countSources(root, "domain"),
                taglib     : countSources(root, "taglib"),
                conf       : countSources(root, "conf"),
                jobs       : countSources(root, "jobs")
            ]
        ]
    }

    private static int countSources(File root, String dir) {
        return (int) (new File(root, "grails-app/$dir")
            .listFiles()
            ?.count { it.isFile() && it.name.endsWith(".groovy") } ?: 0)
    }
}