package myapp

class MyCustomTagLib {
    static namespace = "my" // Optional namespace for your tags

    def greet = { attrs, body ->
        def name = attrs.name ?: "Guest"
        out << "Hello, ${name}!"
    }
}
