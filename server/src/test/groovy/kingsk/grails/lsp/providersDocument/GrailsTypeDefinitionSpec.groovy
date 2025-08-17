package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.ProjectType
import kingsk.grails.lsp.test.TypeDefinitionTestSpec
import spock.lang.Narrative
import spock.lang.Title

/**
 * Tests for the GrailsTypeDefinitionProvider
 */
@Title("Grails Type Definition Provider Tests")
@Narrative("""
    These tests verify that the Grails type definition provider correctly resolves
    type definitions for variables, methods, and other language elements.
""")
class GrailsTypeDefinitionSpec extends TypeDefinitionTestSpec {
	
	def setup() {
		initializeProject(ProjectType.DUMMY)
	}
	
	def "should resolve local variable type definition from declaration"() {
		given: "A class with a local variable declaration"
		String content = """
            class TypeDefinitions {
              public TypeDefinitions() {
                TypeDefinitions localVar
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for local variable"
		def locations = getTypeDefinitionLocations(uri, 2, 22)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 4
		location.range.end.character == 1
	}
	
	def "should resolve local variable type definition from assignment"() {
		given: "A class with a local variable assignment"
		String content = """
            class TypeDefinitions {
              public TypeDefinitions() {
                TypeDefinitions localVar
                localVar = null
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for local variable in assignment"
		def locations = getTypeDefinitionLocations(uri, 3, 6)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 5
		location.range.end.character == 1
	}
	
	def "should resolve local variable type definition from method call"() {
		given: "A class with a local variable method call"
		String content = """
            class TypeDefinitions {
              public void method() {
              }
              public TypeDefinitions() {
                TypeDefinitions localVar
                localVar.method()
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for local variable in method call"
		def locations = getTypeDefinitionLocations(uri, 5, 6)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 7
		location.range.end.character == 1
	}
	
	def "should resolve member variable type definition from declaration"() {
		given: "A class with a member variable declaration"
		String content = """
            class TypeDefinitions {
              TypeDefinitions memberVar
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for member variable"
		def locations = getTypeDefinitionLocations(uri, 1, 20)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 2
		location.range.end.character == 1
	}
	
	def "should resolve member variable type definition from assignment"() {
		given: "A class with a member variable assignment"
		String content = """
            class TypeDefinitions {
              TypeDefinitions memberVar
              public TypeDefinitions() {
                memberVar = null
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for member variable in assignment"
		def locations = getTypeDefinitionLocations(uri, 3, 6)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 5
		location.range.end.character == 1
	}
	
	def "should resolve member method type definition from declaration"() {
		given: "A class with a member method declaration"
		String content = """
            class TypeDefinitions {
              public TypeDefinitions memberMethod() {
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for method return type"
		def locations = getTypeDefinitionLocations(uri, 1, 27)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 3
		location.range.end.character == 1
	}
	
	def "should resolve member method type definition from call"() {
		given: "A class with a member method call"
		String content = """
            class TypeDefinitions {
              public TypeDefinitions memberMethod() {
              }
              public TypeDefinitions() {
                memberMethod()
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for method call"
		def locations = getTypeDefinitionLocations(uri, 4, 6)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 6
		location.range.end.character == 1
	}
	
	def "should resolve parameter type definition"() {
		given: "A method with a typed parameter"
		String content = """
            class TypeDefinitions {
              public void testMethod(TypeDefinitions param) {
                param.toString()
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for parameter"
		def locations = getTypeDefinitionLocations(uri, 2, 6)
		
		then: "Should resolve to class definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 4
		location.range.end.character == 1
	}
	
	def "should resolve generic type definition"() {
		given: "A class with generic type usage"
		String content = """
            import java.util.List
            
            class TypeDefinitions {
              public void testMethod() {
                List<String> list = []
                list.add("test")
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for generic type"
		def locations = getTypeDefinitionLocations(uri, 4, 6)
		
		then: "Should resolve to List interface definition"
		locations.size() >= 1
		// The exact location will depend on the classpath, but should resolve to java.util.List
		def location = locations[0]
		location.uri.contains("List") || location.uri.contains("java/util")
	}
	
	def "should resolve array type definition"() {
		given: "A class with array type usage"
		String content = """
            class TypeDefinitions {
              public void testMethod() {
                String[] array = new String[5]
                array[0] = "test"
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for array variable"
		def locations = getTypeDefinitionLocations(uri, 3, 6)
		
		then: "Should resolve to String type definition"
		locations.size() >= 1
		// Should resolve to String class
		def location = locations[0]
		location.uri.contains("String") || location.uri.contains("java/lang")
	}
	
	def "should resolve interface type definition"() {
		given: "A class implementing an interface"
		String content = """
            interface TestInterface {
                void testMethod()
            }
            
            class TypeDefinitions implements TestInterface {
              public void testMethod() {
                // implementation
              }
              
              public void useInterface() {
                TestInterface instance = this
                instance.testMethod()
              }
            }
        """
		String uri = openTextDocument("TypeDefinitions.groovy", content)
		
		when: "Getting type definition for interface variable"
		def locations = getTypeDefinitionLocations(uri, 10, 6)
		
		then: "Should resolve to interface definition"
		def location = assertSingleTypeDefinitionLocation(locations)
		location.uri == uri
		location.range.start.line == 0
		location.range.start.character == 0
		location.range.end.line == 2
		location.range.end.character == 1
	}
}