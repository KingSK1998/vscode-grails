package kingsk.grails.lsp.model

import org.gradle.api.JavaVersion

class GrailsProject implements Serializable {
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
	Set<File> sourceDirectories = []
	Set<File> resourceDirectories = []
	Set<File> testDirectories = []
	Set<File> testResourceDirectories = []
	Set<File> excludeDirectories = []
	
	// DEPENDENCIES
	Set<DependencyNode> dependencies = []
	
	int sourceFileCount = 0
}