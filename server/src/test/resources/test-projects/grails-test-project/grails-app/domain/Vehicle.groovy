package com.example

@SuppressWarnings('GrailsDomainReservedSqlKeywordName')
class Vehicle {
	
	Integer years
	String name
	Model model
	Make make
	
	static constraints = {
		years min: 1900
		name maxSize: 255
	}
	
	String toString() {
		name
	}
}