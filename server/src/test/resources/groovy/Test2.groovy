package groovy

class Test2 {
	Test referenceClass
	
	Test2() {
		this.referenceClass = new Test()
	}
	
	Test2(Test referenceClass) {
		this.referenceClass = referenceClass
	}
	
	def testMethod() {
		println "This is a test method from Test2"
	}
	
	String testMethodFromReferenceClass() {
		referenceClass.name
		referenceClass.testMethod()
		return referenceClass.testMethodWithReturnType() + " but from Test2"
	}
}
