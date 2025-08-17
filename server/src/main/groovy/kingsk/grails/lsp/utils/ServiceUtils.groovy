package kingsk.grails.lsp.utils

import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.GrailsProject
import kingsk.grails.lsp.model.TextFile

@Slf4j
class ServiceUtils {
	
	static List<TextFile> getAllRelatedFilesFromProject(TextFile sourceFile, GrailsProject project) {
		def sourceFiles = getAllGroovySourceFilesFromProject(project)
		def fQCNToTextFile = generateFQCNFromSourceFiles(sourceFiles)
		return tryToResolveDeepDependentFiles(sourceFile, fQCNToTextFile)
	}
	
	static Map<String, TextFile> generateFQCNFromSourceFiles(List<File> sourceFiles) {
		if (!sourceFiles) return [:]
		Map<String, TextFile> fqcnToTextFile = [:]
		sourceFiles.each { file ->
			def text = file.text
			def name = file.name.replaceFirst(/\.(groovy|java)$/, "")
			def matcher = text =~ /(?m)^package\s+([\w.]+)/
			def pkg = matcher.find() ? matcher[0][1] : ""
			def fqcn = pkg ? "${pkg}.${name}" : name
			def tf = TextFile.create(file.toURI().toString(), text)
			fqcnToTextFile[fqcn] = tf
			//fqcnToTextFile[name] = tf
		}
		return fqcnToTextFile
	}
	
	static List<File> getAllGroovySourceFilesFromProject(GrailsProject grailsProject) {
		if (!grailsProject) return []
		List<File> sourceFiles = []
		// gradle api can return null directories such as "java" folder
		grailsProject.sourceDirectories
				.findAll { dir -> dir.exists() && dir.directory }
				.forEach { dir ->
					dir.eachFileRecurse { file ->
						if (file.exists() && file.file && file.name.endsWith(".groovy")) {
							sourceFiles << file
						}
					}
				}
		return sourceFiles
	}
	
	static List<TextFile> tryToResolveDeepDependentFiles(TextFile sourceFile, Map<String, TextFile> fqcnMap, Set<String> visited = []) {
		if (!visited.add(sourceFile.uri)) return []
		List<TextFile> resolved = [sourceFile]
		List<TextFile> dependencies = tryToResolveDependentFile(sourceFile, fqcnMap)
		dependencies.each { dep ->
			resolved.addAll(tryToResolveDeepDependentFiles(dep, fqcnMap, visited))
		}
		return resolved.unique()
	}
	
	private static List<TextFile> tryToResolveDependentFile(TextFile sourceFile, Map<String, TextFile> fqcnMap) {
		def referenced = (sourceFile.text =~ /(?:new\s+|class\s+|extends\s+|implements\s+)?\b([A-Z]\w+)\b/)
				.collect { it[1] }.unique() as List<String>
		
		// get package name
		//		def matcher = sourceFile.text =~ /(?m)^package\s+([\w.]+)/
		//		def pkg = matcher.find() ? matcher[0][1] : null
		
		return referenced.collect { ref ->
			//			String key = pkg ? "${pkg}.${ref}" : ref
			String key = tryToResolveKey(ref, fqcnMap)
			def tf = fqcnMap[key]
			if (tf && tf.uri != sourceFile.uri) return tf
			return null
		}.findAll { it != null }
	}
	
	private static String tryToResolveKey(String ref, Map<String, TextFile> fqcnMap) {
		// if ref = "com.example.MyClass" then key = "com.example.MyClass"
		if (fqcnMap.containsKey(ref)) return ref
		// if in same package, then key = "MyClass"
		def keys = fqcnMap.keySet().findAll { it.endsWith(ref) }
		if (keys.size() == 1) return keys.first()
		// multiple files with same name in different packages
		// This case should not happen in a typical Groovy/Grails project
		return ref
	}
	
	static String getPackageNameFromFQCN(String fqcn) {
		int lastDot = fqcn.lastIndexOf('.')
		return lastDot >= 0 ? fqcn.substring(0, lastDot) : ""
	}
	
	static String getSimpleNameFromFQCN(String fqcn) {
		int lastDot = fqcn.lastIndexOf('.')
		return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn
	}
	
	
	/**
	 * Validates the given File as a classpath entry and returns its absolute path as String.
	 * Ensures the file exists and can be safely converted to a well-formed URL.
	 *
	 * @param file File to validate
	 * @return Absolute path as String if valid; otherwise null
	 */
	static URL validateClasspathEntry(File file) {
		if (!file || !file.exists()) {
			log.warn("Classpath entry does not exist: $file")
			return null
		}
		
		try {
			// Attempt to convert to URL to catch malformed paths
			def url = file.toURI().toURL()
			if (!"file".equalsIgnoreCase(url.protocol)) {
				log.warn "Unsupported URL protocol for classpath entry: ${url.protocol} ($file)"
				return null
			}
			
			return url
		} catch (MalformedURLException e) {
			log.error("Malformed URL for classpath entry: $file", e)
			return null
		}
	}
	
	/**
	 * Converts the given file to a valid URL.
	 *
	 * @param filePath the file to convert
	 * @return a valid URL or null if conversion fails
	 */
	static URL getValidURL(File filePath) {
		try {
			return filePath.toURI().toURL()
		} catch (MalformedURLException e) {
			log.error("Invalid classpath entry (MalformedURLException): $filePath", e)
			return null
		}
	}
}
