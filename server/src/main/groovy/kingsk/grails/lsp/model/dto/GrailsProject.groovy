package kingsk.grails.lsp.model.dto

import kingsk.grails.lsp.utils.services.ServiceUtils
import org.gradle.api.JavaVersion

class GrailsProject implements Serializable {
    private static final long serialVersionUID = 2L

    // Basic Project Info
    String name
    Integer version
    String group
    String description

    // ENVIRONMENT Info
    File javaHome

    String targetCompatibility
    transient JavaVersion javaVersion
    boolean isGrailsProject = false
    boolean isGrailsPlugin = false
    // only if isGrailsPlugin
    String pluginVersion

    // GRAILS VERSION INFO
    String grailsVersion
    String projectVersion
    String groovyVersion
    boolean isGrails7Plus = false

    // PROJECT DIRECTORIES
    File rootDirectory
    Set<File> sourceDirectories = [] as LinkedHashSet<File>
    Set<File> resourceDirectories = [] as LinkedHashSet<File>
    Set<File> testDirectories = [] as LinkedHashSet<File>
    Set<File> testResourceDirectories = [] as LinkedHashSet<File>
    Set<File> generatedSourceDirectories = [] as LinkedHashSet<File>
    Set<File> excludeDirectories = [] as LinkedHashSet<File>

    // DEPENDENCIES
    Set<DependencyNode> dependencies = [] as LinkedHashSet<DependencyNode>

    // SOURCE SETS
    Map<String, SourceSetModel> sourceSets = [:] as LinkedHashMap<String, SourceSetModel>

    int sourceFileCount = 0

    SourceSetModel getSourceSetForUri(String uri) {
        if (!uri) return null
        File file = fileFromUri(uri)
        if (!file) return null
        return getSourceSetForFile(file)
    }

    SourceSetModel getSourceSetForFile(File file) {
        if (!file || sourceSets == null || sourceSets.isEmpty()) return null

        SourceSetModel winningModel = null
        int longestMatchLength = -1
        boolean isAmbiguous = false

        for (SourceSetModel model : sourceSets.values()) {
            if (model.containsSource(file)) {
                File root = model.getMatchingDeclaredRoot(file)
                int matchLen = root != null ? root.absolutePath.length() : 0
                if (matchLen > longestMatchLength) {
                    longestMatchLength = matchLen
                    winningModel = model
                    isAmbiguous = false
                } else if (matchLen == longestMatchLength && winningModel != null && winningModel.sourceSetName != model.sourceSetName) {
                    isAmbiguous = true
                }
            }
        }

        return isAmbiguous ? null : winningModel
    }

    boolean isTestFile(String uri) {
        if (!uri) return false
        File file = fileFromUri(uri)
        if (!file) return false

        SourceSetModel ss = getSourceSetForFile(file)
        if (ss != null) {
            return isTestScopeName(ss.sourceSetName)
        }

        if (sourceSets != null && !sourceSets.isEmpty()) {
            return false
        }

        String path = file.absolutePath
        return testDirectories.any { dir -> isSameOrChild(path, dir.absolutePath) } ||
               testResourceDirectories.any { dir -> isSameOrChild(path, dir.absolutePath) }
    }

    private static boolean isTestScopeName(String name) {
        if (!name) return false
        String lower = name.toLowerCase()
        return lower.contains("test")
    }

    List<DependencyNode> getMainDependencies() {
        if (dependencies == null) return []
        return dependencies.findAll { dep ->
            !isTestScope(dep?.scope)
        } as List<DependencyNode>
    }

    List<DependencyNode> getTestDependencies() {
        if (dependencies == null) return []
        return new ArrayList<DependencyNode>(dependencies)
    }

    private static boolean isTestScope(String scope) {
        if (!scope) return false
        String s = scope.trim().toUpperCase()
        return s.contains("TEST")
    }

    List<URL> getMainClasspathUrls() {
        if (sourceSets != null && !sourceSets.isEmpty()) {
            if (sourceSets.containsKey("main")) {
                SourceSetModel mainModel = sourceSets.get("main")
                return (mainModel?.compileClasspath ?: []).collect { ServiceUtils.validateClasspathEntry(it) }.findAll { it != null }
            }
            return []
        }
        return getMainDependencies().collect { ServiceUtils.validateClasspathEntry(it.jarFileClasspath) }.findAll { it != null }
    }

    List<URL> getTestClasspathUrls() {
        if (sourceSets != null && !sourceSets.isEmpty()) {
            if (sourceSets.containsKey("test")) {
                SourceSetModel testModel = sourceSets.get("test")
                return (testModel?.compileClasspath ?: []).collect { ServiceUtils.validateClasspathEntry(it) }.findAll { it != null }
            }
            return []
        }
        return getTestDependencies().collect { ServiceUtils.validateClasspathEntry(it.jarFileClasspath) }.findAll { it != null }
    }

    List<URL> getClasspathUrlsForUri(String uri) {
        if (sourceSets != null && !sourceSets.isEmpty()) {
            SourceSetModel ss = getSourceSetForUri(uri)
            if (ss != null) {
                return (ss.compileClasspath ?: []).collect { ServiceUtils.validateClasspathEntry(it) }.findAll { it != null }
            }
            return []
        }
        if (isTestFile(uri)) {
            return getTestClasspathUrls()
        }
        return getMainClasspathUrls()
    }

    private static File fileFromUri(String uri) {
        try {
            if (uri.startsWith("file:")) {
                return new File(URI.create(uri))
            }
            return new File(uri)
        } catch (Exception ignored) {
            return null
        }
    }

    private static boolean isSameOrChild(String childPath, String parentPath) {
        if (!childPath || !parentPath) return false
        String c = childPath.replace('/', File.separator).replace('\\', File.separator)
        String p = parentPath.replace('/', File.separator).replace('\\', File.separator)
        if (c.equalsIgnoreCase(p)) return true
        if (!p.endsWith(File.separator)) {
            p = p + File.separator
        }
        return c.length() > p.length() && c.substring(0, p.length()).equalsIgnoreCase(p)
    }
}
