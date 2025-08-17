package groovy

class Person {
	String name
	
	String greet() {
		return "Hello, I'm ${name}"
	}
}

def person = new Person(name: "John")
person

def myList = [1, 2, 3, 4].each { println it }