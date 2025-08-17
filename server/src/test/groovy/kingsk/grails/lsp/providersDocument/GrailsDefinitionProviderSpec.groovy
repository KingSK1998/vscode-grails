package kingsk.grails.lsp.providersDocument

import kingsk.grails.lsp.test.DefinitionTestSpec
import kingsk.grails.lsp.test.ProjectType

/**
 * Tests for the GrailsDefinitionProvider
 */
class GrailsDefinitionProviderSpec extends DefinitionTestSpec {
	
	def setup() {
		initializeProject(ProjectType.EMPTY)
	}
	
	def "should resolve local variable definition from declaration"() {
		given: "A class with a local variable"
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    int localVar\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when: "Getting definition for local variable"
		def locations = getDefinitionLocations(uri, 2, 14)?.getLeft()
		
		then: "Should resolve to variable declaration"
		locations.size() == 1
		assertLocation(locations[0], uri, 2, 8, 2, 16)
	}
	
	def "should resolve local variable definition from assignment"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    int localVar\r\n")
		builder.append("    localVar = 123\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 3, 6)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 2, 8, 2, 16)
	}
	
	def "should resolve local variable definition from method call object expression"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    String localVar = \"hi\"\r\n")
		builder.append("    localVar.charAt(0)\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 3, 6)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 2, 11, 2, 19)
	}
	
	def "should resolve member variable definition from declaration"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public int memberVar\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 1, 18)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 2, 1, 22)
	}
	
	def "should resolve member variable definition from assignment"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public int memberVar\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    memberVar = 123\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 3, 6)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 2, 1, 22)
	}
	
	def "should resolve member method definition from declaration"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public void memberMethod() {}\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 1, 16)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 2, 1, 31)
	}
	
	def "should resolve member method definition from call"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public void memberMethod() {}\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    memberMethod()\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 3, 6)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 2, 1, 31)
	}
	
	def "should resolve class definition from declaration"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 0, 8)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 0, 0, 1, 1)
	}
	
	def "should resolve constructor definition from constructor call"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    new Definitions()\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 2, 10)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 2, 3, 3)
	}
	
	def "should resolve parameter definition from declaration"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public void memberMethod(int param) {\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 1, 33)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 27, 1, 36)
	}
	
	def "should resolve parameter definition from reference"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public void memberMethod(int param) {\r\n")
		builder.append("    param\r\n")
		builder.append("  }\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 2, 6)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 1, 27, 1, 36)
	}
	
	def "should resolve definition from array item member access"() {
		given:
		StringBuilder builder = new StringBuilder()
		builder.append("class Definitions {\r\n")
		builder.append("  public Definitions() {\r\n")
		builder.append("    Definitions[] items\r\n")
		builder.append("    items[0].hello\r\n")
		builder.append("  }\r\n")
		builder.append("  public String hello\r\n")
		builder.append("}\r\n")
		String uri = openTextDocument("Definitions.groovy", builder.toString())
		
		when:
		def locations = getDefinitionLocations(uri, 3, 15)?.getLeft()
		
		then:
		locations.size() == 1
		assertLocation(locations[0], uri, 5, 2, 5, 21)
	}
}