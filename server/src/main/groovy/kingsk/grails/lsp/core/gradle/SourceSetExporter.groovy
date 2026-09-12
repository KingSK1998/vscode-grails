package kingsk.grails.lsp.core.gradle

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.dto.ProjectDependencyEdge
import kingsk.grails.lsp.model.dto.SourceSetModel
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.CancellationTokenSource
import org.gradle.tooling.ProjectConnection

import java.nio.file.Files

@Slf4j
@CompileStatic
class SourceSetExporter {

    private static final int MAX_EXPORT_BYTES = 10 * 1024 * 1024 // 10 MiB limit

    static SourceSetExportResult exportProjectData(
        ProjectConnection connection,
        File projectDir,
        CancellationTokenSource cancellationSource = null
    ) {
        if (connection == null || projectDir == null) return new SourceSetExportResult()

        File tempInitScript = null
        File tempOutputFile = null
        try {
            tempInitScript = extractInitScript()
            tempOutputFile = Files.createTempFile("grails-lsp-export-", ".json").toFile()

            BuildLauncher launcher = connection.newBuild()
                .withArguments(
                    "-I", tempInitScript.absolutePath,
                    "-Dgrails.lsp.export.output=" + tempOutputFile.absolutePath,
                    "-q"
                )
                .forTasks("grailsLspExportSourceSets")

            if (cancellationSource != null) {
                launcher.withCancellationToken(cancellationSource.token())
            }

            // Execute export task quietly
            launcher.run()

            if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
                log.warn("[GRADLE_EXPORT] Source-set export produced empty or missing file for: ${projectDir.name}")
                return new SourceSetExportResult()
            }

            if (tempOutputFile.length() > MAX_EXPORT_BYTES) {
                log.warn("[GRADLE_EXPORT] Source-set export output exceeded 10 MiB limit (${tempOutputFile.length()} bytes)")
                return new SourceSetExportResult()
            }

            return parseExportJson(tempOutputFile, projectDir)
        } catch (Exception e) {
            log.warn("[GRADLE_EXPORT] Failed to export source sets via init-script: ${e.message}")
            return new SourceSetExportResult()
        } finally {
            cleanupFile(tempInitScript)
            cleanupFile(tempOutputFile)
        }
    }

    static Map<String, SourceSetModel> exportSourceSets(
        ProjectConnection connection,
        File projectDir,
        CancellationTokenSource cancellationSource = null
    ) {
        SourceSetExportResult res = exportProjectData(connection, projectDir, cancellationSource)
        return res != null ? res.sourceSets : Collections.<String, SourceSetModel>emptyMap()
    }

    private static File extractInitScript() {
        InputStream is = SourceSetExporter.class.getResourceAsStream("/gradle/source-set-export.gradle")
        if (is == null) {
            throw new FileNotFoundException("Classpath resource /gradle/source-set-export.gradle not found")
        }
        File tempFile = Files.createTempFile("source-set-export-", ".gradle").toFile()
        tempFile.withOutputStream { os ->
            os << is
        }
        is.close()
        return tempFile
    }

    @SuppressWarnings("unchecked")
    private static SourceSetExportResult parseExportJson(File jsonFile, File targetProjectDir) {
        SourceSetExportResult result = new SourceSetExportResult()
        try {
            JsonSlurper slurper = new JsonSlurper()
            Object parsed = slurper.parse(jsonFile)
            if (!(parsed instanceof Map)) return result
            Map<String, Object> rootMap = (Map<String, Object>) parsed

            Number schemaVersion = (Number) rootMap.get("schemaVersion")
            if (schemaVersion == null || schemaVersion.intValue() != 1) {
                log.warn("[GRADLE_EXPORT] Unsupported schema version: ${schemaVersion}")
                return result
            }

            Map<String, Object> projects = (Map<String, Object>) rootMap.get("projects")
            if (projects == null) return result

            String canonicalTarget = targetProjectDir.canonicalPath
            for (Map.Entry<String, Object> pEntry : projects.entrySet()) {
                Map<String, Object> pData = (Map<String, Object>) pEntry.value
                String pDirPath = (String) pData.get("projectDir")
                File pDir = pDirPath ? new File(pDirPath) : null
                if (pDir == null) continue

                // Check if this project matches the target directory
                if (pDir.canonicalPath.equalsIgnoreCase(canonicalTarget)) {
                    String pPath = (String) pData.get("path")
                    String buildRootPath = (String) pData.get("buildRoot")
                    File buildRoot = buildRootPath ? new File(buildRootPath) : targetProjectDir
                    result.gradleProjectPath = pPath
                    result.buildRoot = buildRoot

                    // Extract project dependencies
                    List<Map<String, Object>> pDeps = (List<Map<String, Object>>) pData.get("projectDependencies")
                    if (pDeps != null) {
                        for (Map<String, Object> pd : pDeps) {
                            String dPath = (String) pd.get("path")
                            String dName = (String) pd.get("name")
                            String dGroup = (String) pd.get("group")
                            String dProjDir = (String) pd.get("projectDir")
                            String dBuildRoot = (String) pd.get("buildRoot")
                            File depProjDir = dProjDir ? new File(dProjDir) : null
                            File depBuildRoot = dBuildRoot ? new File(dBuildRoot) : buildRoot

                            result.projectDependencies.add(new ProjectDependencyEdge(
                                dPath,
                                depBuildRoot,
                                depProjDir,
                                dName,
                                dGroup
                            ))
                        }
                    }

                    Map<String, Object> sourceSets = (Map<String, Object>) pData.get("sourceSets")
                    if (sourceSets != null) {
                        for (Map.Entry<String, Object> ssEntry : sourceSets.entrySet()) {
                            Map<String, Object> ssData = (Map<String, Object>) ssEntry.value
                            String ssName = (String) ssData.get("name")
                            List<String> srcDirs = (List<String>) ssData.get("srcDirs") ?: []
                            List<String> resDirs = (List<String>) ssData.get("resourceDirs") ?: []
                            List<String> genDirs = (List<String>) ssData.get("generatedDirs") ?: []
                            List<String> compileCp = (List<String>) ssData.get("compileClasspath") ?: []
                            List<String> runtimeCp = (List<String>) ssData.get("runtimeClasspath") ?: []
                            List<String> outDirs = (List<String>) ssData.get("outputDirs") ?: []

                            SourceSetModel model = new SourceSetModel(
                                ssName,
                                pPath,
                                buildRoot,
                                targetProjectDir,
                                srcDirs.collect { new File(it) },
                                resDirs.collect { new File(it) },
                                genDirs.collect { new File(it) },
                                compileCp.collect { new File(it) },
                                runtimeCp.collect { new File(it) },
                                outDirs.collect { new File(it) },
                                [],
                                true,
                                []
                            )
                            result.sourceSets.put(ssName, model)
                        }
                    }
                    break
                }
            }
        } catch (Exception e) {
            log.warn("[GRADLE_EXPORT] Failed to parse exported JSON: ${e.message}", e)
        }
        return result
    }

    private static void cleanupFile(File file) {
        if (file != null && file.exists()) {
            try {
                file.delete()
            } catch (Exception ignored) {}
        }
    }
}

@CompileStatic
class SourceSetExportResult {
    final Map<String, SourceSetModel> sourceSets = new LinkedHashMap<>()
    final List<ProjectDependencyEdge> projectDependencies = new ArrayList<>()
    String gradleProjectPath
    File buildRoot
}
