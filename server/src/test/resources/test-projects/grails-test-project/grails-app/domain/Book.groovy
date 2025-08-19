package com.example

class Book {
	String title
	String author
	Type type
	
	static constraints = {
		title blank: false, size: 1..255
		author blank: false, size: 1..255
	}
}
