package kingsk.grails.lsp.core.compiler

import groovy.transform.CompileStatic

/**
 * Custom parent classloader that isolates project compilation from host/server libraries.
 * Only permits standard JDK platform classes and essential Groovy compiler runtime classes.
 * Host test frameworks (e.g. JUnit, Spock) and other server-internal JARs will NOT leak
 * into the project's compilation or analysis classpath.
 */
@CompileStatic
class IsolatedParentClassLoader extends ClassLoader {
    private final ClassLoader hostClassLoader

    IsolatedParentClassLoader(ClassLoader hostClassLoader) {
        super(ClassLoader.getPlatformClassLoader())
        this.hostClassLoader = hostClassLoader != null ? hostClassLoader : IsolatedParentClassLoader.class.classLoader
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        // 1. JDK and standard runtime packages from platform classloader
        //    Includes java.xml module types (org.w3c.dom, org.xml.sax) needed by Groovy XML extensions
        if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jdk.") || name.startsWith("sun.") ||
            name.startsWith("org.w3c.") || name.startsWith("org.xml.")) {
            return super.loadClass(name, resolve)
        }

        // 2. Groovy runtime packages required for compilation
        //    Groovy 4 relocated some packages from org.codehaus.groovy to org.apache.groovy
        if (name.startsWith("groovy.") || name.startsWith("org.codehaus.groovy.") || name.startsWith("org.apache.groovy.") ||
            name.startsWith("groovyjarjarantlr4.") || name.startsWith("groovyjarjarasm.") ||
            name.startsWith("groovyjarjarcommonscli.")) {
            return hostClassLoader.loadClass(name)
        }

        // 3. Reject all other server/host/test dependencies
        throw new ClassNotFoundException("IsolatedParentClassLoader blocked host dependency: " + name)
    }

    @Override
    URL getResource(String name) {
        if (name.startsWith("java/") || name.startsWith("javax/") ||
            name.startsWith("org/w3c/") || name.startsWith("org/xml/") ||
            name.startsWith("groovy/") || name.startsWith("org/codehaus/groovy/") || name.startsWith("org/apache/groovy/")) {
            return hostClassLoader.getResource(name)
        }
        return super.getResource(name)
    }
}
