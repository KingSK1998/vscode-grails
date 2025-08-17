package kingsk.grails.lsp.model

import groovy.transform.CompileStatic

@CompileStatic
class DependencyNode implements Serializable {
	String name
	String group
	String version
	String scope
	
	File jarFileClasspath
	File sourceJarFileClasspath
	File javadocFileClasspath
	
	DependencyNode(String name, String group, String version, String scope, File jarFile, File sourceFile, File javadocFile) {
		this.name = name
		this.group = group
		this.version = version
		this.scope = scope
		this.jarFileClasspath = jarFile
		this.sourceJarFileClasspath = sourceFile
		this.javadocFileClasspath = javadocFile
	}
	
	DependencyNode() {}
	
	@Override
	String toString() {
		return "${group}:${name}:${version}${scope ? ':' + scope : ''}"
	}
	
	@Override
	boolean equals(Object obj) {
		if (!(obj instanceof DependencyNode)) return false
		DependencyNode other = (DependencyNode) obj
		return name == other.name && group == other.group && version == other.version
	}
	
	@Override
	int hashCode() {
		return Objects.hash(name, group, version)
	}
}