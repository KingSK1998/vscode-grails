package kingsk.grails.lsp.model

import spock.lang.Specification

/**
 * Tests for the DependencyNode model class
 */
class DependencyNodeSpec extends Specification {
	
	def "should create dependency node with all parameters"() {
		given: "Dependency parameters"
		String name = "test-lib"
		String group = "com.example"
		String version = "1.0.0"
		String scope = "compile"
		File jarFile = new File("/path/to/jar")
		File sourceFile = new File("/path/to/source")
		File javadocFile = new File("/path/to/javadoc")
		
		when: "Creating dependency node"
		DependencyNode node = new DependencyNode(name, group, version, scope, jarFile, sourceFile, javadocFile)
		
		then: "All properties should be set correctly"
		node.name == name
		node.group == group
		node.version == version
		node.scope == scope
		node.jarFileClasspath == jarFile
		node.sourceJarFileClasspath == sourceFile
		node.javadocFileClasspath == javadocFile
	}
	
	def "should format toString correctly with scope"() {
		given: "A dependency node with scope"
		DependencyNode node = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		
		when: "Converting to string"
		String result = node.toString()
		
		then: "Should format correctly"
		result == "com.example:test-lib:1.0.0:compile"
	}
	
	def "should format toString correctly without scope"() {
		given: "A dependency node without scope"
		DependencyNode node = new DependencyNode("test-lib", "com.example", "1.0.0", null, null, null, null)
		
		when: "Converting to string"
		String result = node.toString()
		
		then: "Should format correctly without scope"
		result == "com.example:test-lib:1.0.0"
	}
	
	def "should implement equals correctly for same dependencies"() {
		given: "Two identical dependency nodes"
		DependencyNode node1 = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		DependencyNode node2 = new DependencyNode("test-lib", "com.example", "1.0.0", "runtime", null, null, null)
		
		expect: "Comparing for equality"
		node1 == node2
	}
	
	def "should implement equals correctly for different dependencies"() {
		given: "Two different dependency nodes"
		DependencyNode node1 = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		DependencyNode node2 = new DependencyNode("other-lib", "com.example", "1.0.0", "compile", null, null, null)
		
		expect: "Comparing for inequality"
		node1 != node2
	}
	
	def "should handle equals with null object"() {
		given: "A dependency node"
		DependencyNode node = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		
		expect: "Comparing for equality"
		node != null
	}
	
	def "should implement hashCode consistently with equals"() {
		given: "Two equal dependency nodes"
		DependencyNode node1 = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		DependencyNode node2 = new DependencyNode("test-lib", "com.example", "1.0.0", "runtime", null, null, null)
		
		when: "Getting hash codes"
		int hash1 = node1.hashCode()
		int hash2 = node2.hashCode()
		
		then: "Hash codes should be equal"
		hash1 == hash2
	}
	
	def "should have different hashCodes for different dependencies"() {
		given: "Two different dependency nodes"
		DependencyNode node1 = new DependencyNode("test-lib", "com.example", "1.0.0", "compile", null, null, null)
		DependencyNode node2 = new DependencyNode("other-lib", "com.example", "1.0.0", "compile", null, null, null)
		
		when: "Getting hash codes"
		int hash1 = node1.hashCode()
		int hash2 = node2.hashCode()
		
		then: "Hash codes should likely be different"
		hash1 != hash2
	}
	
	def "should be serializable"() {
		given: "A dependency node"
		DependencyNode original = new DependencyNode("test-lib", "com.example", "1.0.0", "compile",
				new File("/jar"), new File("/source"), new File("/javadoc"))
		
		when: "Serializing and deserializing"
		ByteArrayOutputStream baos = new ByteArrayOutputStream()
		ObjectOutputStream oos = new ObjectOutputStream(baos)
		oos.writeObject(original)
		oos.close()
		
		ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray())
		ObjectInputStream ois = new ObjectInputStream(bais)
		DependencyNode deserialized = (DependencyNode) ois.readObject()
		ois.close()
		
		then: "Deserialized object should equal original"
		deserialized.equals(original)
		deserialized.name == original.name
		deserialized.group == original.group
		deserialized.version == original.version
		deserialized.scope == original.scope
	}
	
	def "should handle null values in constructor gracefully"() {
		when: "Creating dependency node with null values"
		DependencyNode node = new DependencyNode(null, null, null, null, null, null, null)
		
		then: "Should not throw exception"
		node != null
		node.name == null
		node.group == null
		node.version == null
	}
}