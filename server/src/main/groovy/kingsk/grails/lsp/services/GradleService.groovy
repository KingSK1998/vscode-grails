package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.core.gradle.GrailsProjectBuilder
import kingsk.grails.lsp.core.gradle.ProjectCache
import kingsk.grails.lsp.model.DependencyNode
import kingsk.grails.lsp.model.GrailsProject

@Slf4j
@CompileStatic
class GradleService {
	private final ProjectCache cache = new ProjectCache()
	private final GrailsProjectBuilder builder = new GrailsProjectBuilder()
	
	GrailsProject getGrailsProject(String projectDir) {
		File rootDir = new File(projectDir.toURI())
		if (!rootDir.exists()) {
			throw new FileNotFoundException("[GRADLE] Project directory does not exist: ${projectDir}")
		}
		
		// Try to load from cache first (only if not stale)
		if (!cache.isStale(rootDir)) {
			GrailsProject cachedProject = cache.load(rootDir)
			if (cachedProject) {
				log.debug("[GRADLE] Project loaded from cache: ${rootDir.name}")
				return cachedProject
			}
			// If cache exists but load returns null (corrupted), fall through to rebuild
			log.debug("[GRADLE] Cache file exists but corrupted, rebuilding: ${rootDir.name}")
		}
		
		// Build project once (cache is stale, missing, or corrupted)
		log.info("[GRADLE] Building GrailsProject from Gradle API for: ${rootDir.name}")
		long startTime = System.currentTimeMillis()
		
		try {
			GrailsProject grailsProject = builder.build(rootDir)
			cache.save(rootDir, grailsProject)
			
			long buildTime = System.currentTimeMillis() - startTime
			long cacheSize = cache.getCacheSize(rootDir)
			log.info("[GRADLE] GrailsProject built and cached in ${buildTime}ms: ${cacheSize / 1024}KB")
			return grailsProject
		} catch (Exception e) {
			log.error("[GRADLE] Failed to build project from Gradle API for: ${rootDir.name}", e)
			throw new IllegalStateException("Failed to build project: ${rootDir.absolutePath}", e)
		}
	}
	
	void invalidateCache() {
		cache.bumpUpVersion()
		log.info("[GRADLE] ProjectInfoCache version bumped to: ${cache.currentVersion}")
	}
	
	/**
	 * Force invalidate cache for a specific project
	 */
	void invalidateProjectCache(String projectDir) {
		File rootDir = new File(projectDir.toURI())
		cache.forceInvalidation(rootDir)
		log.info("[GRADLE] Force invalidated cache for project: ${rootDir.name}")
	}
	
	// ===== Download Javadoc or Sources =====
	
	/**
	 * Download javadoc artifact
	 */
	File downloadJavaDocJarFile(File rootDirectory, DependencyNode dependency) {
		return builder.downloadArtifactInternal(rootDirectory, dependency, 'javadoc')
	}
	
	/**
	 * Download sources artifact
	 */
	File downloadSourcesJarFile(File rootDirectory, DependencyNode dependency) {
		return builder.downloadArtifactInternal(rootDirectory, dependency, 'sources')
	}
}