package demo

import spock.lang.Specification

class CalculatorTest extends Specification {

    def calculator = new Calculator()

    def "should add two numbers"() {
        when:
        def result = calculator.add(2, 3)

        then:
        result == 5
    }

    def "should throw exception when dividing by zero"() {
        when:
        calculator.divide(10, 0)

        then:
        thrown(IllegalArgumentException)
    }
}
