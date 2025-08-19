package demo

class Main {
    static void main(String[] args) {
        def calc = new Calculator()
        println "Calculator Demo"
        println "5 + 3 = ${calc.add(5, 3)}"
        println "10 - 4 = ${calc.subtract(10, 4)}"
        println "6 * 7 = ${calc.multiply(6, 7)}"
        println "15 / 3 = ${calc.divide(15, 3)}"
    }
}
