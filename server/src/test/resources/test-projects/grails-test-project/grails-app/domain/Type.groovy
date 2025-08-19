package com.example

class Type {
    String name
    String description

    static constraints = {
        name blank: false, size: 1..255
        description nullable: true, size: 0..1000
    }

    String toString() {
        name
    }
}