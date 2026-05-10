package kingsk.grails.lsp.core.gradle

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.protocol.mapper.ProjectMapper

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
            log.warn("[GRADLE] Failed to load cache for ${projectDir.name}, will rebuild", e)
            return null
        }
    }

    void save(File projectDir, GrailsProject grailsProject) {
        try {
            File cacheFile = getCacheFile(projectDir)

            // Binary Cache
            cacheFile.withObjectOutputStream { oos ->
                oos.writeInt(currentVersion)
                oos.writeObject(grailsProject)
            }

            // JSON cache (for client / debug)
            File jsonFile = getJsonFile(projectDir)
            def dto = ProjectMapper.toDTO(grailsProject)
            jsonFile.text = JsonOutput.prettyPrint(JsonOutput.toJson(dto))

            BigDecimal sizeKB = cacheFile.length() / 1024
            log.info("[GRADLE] Saved project to cache: ${grailsProject.name} (${sizeKB}KB, ${grailsProject.dependencies.size()} deps)")
        } catch (Exception e) {
            log.error("[GRADLE] Failed to save cache for ${grailsProject.name}", e)
        }
    }

    /**
     * Get binary cache file path in project root under .grails-lsp directory
     */
    static File getCacheFile(File projectDir) {
        File cacheDir = new File(projectDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return new File(cacheDir, PROJECT_CACHE_FILE)
    }

    /**
     * Get json cache file path in project root under .grails-lsp directory
     */
    static File getJsonFile(File projectDir) {
        File cacheDir = new File(projectDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return new File(cacheDir, PROJECT_JSON_FILE)
    }

    static boolean isStale(File projectDir) {
        File cacheFile = getCacheFile(projectDir)
        if (!cacheFile.exists()) {
            log.debug("[GRADLE] Binary cache is stale - file does not exist: ${projectDir.name}")
            return true
        }

        // Timestamp check: only a small set of Gradle root files (avoid broad directory walks)
        long cacheTime = cacheFile.lastModified()

        List<String> watchNames = [
            'build.gradle',
            'settings.gradle',
            'gradle.properties'
        ]

        for (String name : watchNames) {
            File f = new File(projectDir, name)
            if (f.exists() && f.lastModified() > cacheTime) {
                log.info("[GRADLE] Binary cache is stale - ${name} newer: ${projectDir.name}")
                return true
            }
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

    static long getCacheSize(File projectDir) {
        File cacheFile = getCacheFile(projectDir)
        return cacheFile.exists() ? cacheFile.length() : 0
    }

    /* ------------------- Testing Utilities --------------------------- */

    static long getCacheAge(File projectDir) {
        File cacheFile = getCacheFile(projectDir)
        if (!cacheFile.exists()) return -1
        return System.currentTimeMillis() - cacheFile.lastModified()
    }

    static boolean cacheExists(File projectDir) {
        return getCacheFile(projectDir).exists()
    }
}