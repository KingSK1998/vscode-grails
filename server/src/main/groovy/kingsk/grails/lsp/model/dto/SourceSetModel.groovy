package kingsk.grails.lsp.model.dto

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@CompileStatic
@ToString(includeNames = true)
@EqualsAndHashCode(includes = ["gradleProjectPath", "sourceSetName", "compileClasspath"])
class SourceSetModel implements Serializable {
    private static final long serialVersionUID = 1L

    final String sourceSetName
    final String gradleProjectPath
    final File buildRoot
    final File moduleDirectory
    final Set<File> sourceDirectories
    final Set<File> resourceDirectories
    final Set<File> generatedDirectories
    final List<File> compileClasspath
    final List<File> runtimeClasspath
    final Set<File> outputDirectories
    final Set<String> excludes
    final boolean isComplete
    final List<String> errors

    SourceSetModel() {
        this.sourceSetName = null
        this.gradleProjectPath = null
        this.buildRoot = null
        this.moduleDirectory = null
        this.sourceDirectories = Collections.emptySet()
        this.resourceDirectories = Collections.emptySet()
        this.generatedDirectories = Collections.emptySet()
        this.compileClasspath = Collections.emptyList()
        this.runtimeClasspath = Collections.emptyList()
        this.outputDirectories = Collections.emptySet()
        this.excludes = Collections.emptySet()
        this.isComplete = true
        this.errors = Collections.emptyList()
    }

    SourceSetModel(
        String sourceSetName,
        String gradleProjectPath,
        File buildRoot,
        File moduleDirectory,
        Collection<File> sourceDirectories = [],
        Collection<File> resourceDirectories = [],
        Collection<File> generatedDirectories = [],
        List<File> compileClasspath = [],
        List<File> runtimeClasspath = [],
        Collection<File> outputDirectories = [],
        Collection<String> excludes = [],
        boolean isComplete = true,
        List<String> errors = []
    ) {
        this.sourceSetName = sourceSetName
        this.gradleProjectPath = gradleProjectPath
        this.buildRoot = buildRoot
        this.moduleDirectory = moduleDirectory
        this.sourceDirectories = Collections.unmodifiableSet(new LinkedHashSet<File>(sourceDirectories ?: Collections.<File>emptyList()))
        this.resourceDirectories = Collections.unmodifiableSet(new LinkedHashSet<File>(resourceDirectories ?: Collections.<File>emptyList()))
        this.generatedDirectories = Collections.unmodifiableSet(new LinkedHashSet<File>(generatedDirectories ?: Collections.<File>emptyList()))
        this.compileClasspath = Collections.unmodifiableList(new ArrayList<File>(compileClasspath ?: Collections.<File>emptyList()))
        this.runtimeClasspath = Collections.unmodifiableList(new ArrayList<File>(runtimeClasspath ?: Collections.<File>emptyList()))
        this.outputDirectories = Collections.unmodifiableSet(new LinkedHashSet<File>(outputDirectories ?: Collections.<File>emptyList()))
        this.excludes = Collections.unmodifiableSet(new LinkedHashSet<String>(excludes ?: Collections.<String>emptyList()))
        this.isComplete = isComplete
        this.errors = Collections.unmodifiableList(new ArrayList<String>(errors ?: Collections.<String>emptyList()))
    }

    boolean isGenerated(File file) {
        if (!file || generatedDirectories == null || generatedDirectories.isEmpty()) return false
        String filePath = file.absolutePath
        return generatedDirectories.any { File genDir ->
            isSameOrChild(filePath, genDir.absolutePath)
        }
    }

    File getMatchingDeclaredRoot(File file) {
        if (!file) return null
        String filePath = file.absolutePath
        File bestRoot = null
        int bestLen = -1

        Set<File> allRoots = new LinkedHashSet<File>()
        if (sourceDirectories != null) allRoots.addAll(sourceDirectories)
        if (resourceDirectories != null) allRoots.addAll(resourceDirectories)
        if (generatedDirectories != null) allRoots.addAll(generatedDirectories)

        for (File dir : allRoots) {
            if (dir != null && isSameOrChild(filePath, dir.absolutePath)) {
                int len = dir.absolutePath.length()
                if (len > bestLen) {
                    bestLen = len
                    bestRoot = dir
                }
            }
        }
        return bestRoot
    }

    boolean containsSource(File file) {
        if (!file) return false
        File matchingRoot = getMatchingDeclaredRoot(file)
        if (matchingRoot == null) return false
        if (isExcluded(file, matchingRoot)) return false
        return true
    }

    boolean isExcluded(File file, File rootDir) {
        if (!file || !rootDir || excludes == null || excludes.isEmpty()) return false
        String rel = getRelativePath(file, rootDir)
        if (!rel) return false
        return excludes.any { String pattern -> matchesPattern(rel, pattern) }
    }

    static String getRelativePath(File file, File rootDir) {
        if (!file || !rootDir) return null
        String f = file.absolutePath.replace('\\', '/')
        String r = rootDir.absolutePath.replace('\\', '/')
        while (r.endsWith('/')) {
            r = r.substring(0, r.length() - 1)
        }
        r = r + '/'
        if (f.equalsIgnoreCase(r)) return ""
        if (f.length() > r.length() && f.substring(0, r.length()).equalsIgnoreCase(r)) {
            return f.substring(r.length())
        }
        return null
    }

    private static boolean matchesPattern(String relativePath, String pattern) {
        if (!relativePath || !pattern) return false
        String rel = relativePath.replace('\\', '/').replaceAll('^/+', '')
        String pat = pattern.replace('\\', '/').replaceAll('^/+', '')

        if (!pat.contains('/')) {
            pat = '**/' + pat
        }

        StringBuilder regex = new StringBuilder('^')
        int i = 0
        int len = pat.length()
        while (i < len) {
            char ch = pat.charAt(i)
            if (ch == '*' as char) {
                if (i + 1 < len && pat.charAt(i + 1) == '*' as char) {
                    if (i + 2 < len && pat.charAt(i + 2) == '/' as char) {
                        regex.append('(?:.*/)?')
                        i += 3
                    } else {
                        regex.append('.*')
                        i += 2
                    }
                } else {
                    regex.append('[^/]*')
                    i++
                }
            } else if (ch == '?' as char) {
                regex.append('[^/]')
                i++
            } else if ('\\[]{}()+^$|.'.indexOf((int) ch) != -1) {
                regex.append('\\').append(ch)
                i++
            } else {
                regex.append(ch)
                i++
            }
        }
        regex.append('$')
        return rel.matches(regex.toString())
    }

    static boolean isSameOrChild(String childPath, String parentPath) {
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
