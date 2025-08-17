package groovy

class Test {
	def testProperty
	def testPropertyWithData = 20
	String name = "John Doe"
	
	def testMethod() {
		println "This is a test method from Test"
	}
	
	String testMethodWithReturnType() {
		testMethod()
		return "This is a test method with return type from Test"
	}
}
