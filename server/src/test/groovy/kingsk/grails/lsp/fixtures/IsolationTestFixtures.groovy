package kingsk.grails.lsp.fixtures

import javax.tools.JavaCompiler
import javax.tools.StandardJavaFileManager
import javax.tools.ToolProvider
import java.nio.file.Files
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class IsolationTestFixtures {

    static void createJar(File jarFile, Map<String, String> javaSources) {
        jarFile.parentFile.mkdirs()
        File tempSrcDir = Files.createTempDirectory("fixture-src").toFile()
        File tempClassesDir = Files.createTempDirectory("fixture-bin").toFile()
        try {
            List<File> sourceFiles = []
            javaSources.each { String relativePath, String sourceCode ->
                File srcFile = new File(tempSrcDir, relativePath)
                srcFile.parentFile.mkdirs()
                srcFile.text = sourceCode
                sourceFiles.add(srcFile)
            }

            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler()
            if (compiler == null) {
                throw new IllegalStateException("No JavaCompiler available in test runtime")
            }
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)
            def compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles)
            List<String> options = ["-d", tempClassesDir.absolutePath]
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, null, options, null, compilationUnits)
            boolean success = task.call()
            fileManager.close()
            if (!success) {
                throw new IllegalStateException("Failed to compile fixture sources: " + javaSources.keySet())
            }

            Manifest manifest = new Manifest()
            manifest.mainAttributes.putValue("Manifest-Version", "1.0")
            jarFile.withOutputStream { fos ->
                JarOutputStream jos = new JarOutputStream(fos, manifest)
                tempClassesDir.eachFileRecurse { file ->
                    if (file.isFile()) {
                        String entryName = tempClassesDir.toPath().relativize(file.toPath()).toString().replace('\\', '/')
                        JarEntry entry = new JarEntry(entryName)
                        jos.putNextEntry(entry)
                        file.withInputStream { is -> jos << is }
                        jos.closeEntry()
                    }
                }
                jos.close()
            }
        } finally {
            tempSrcDir.deleteDir()
            tempClassesDir.deleteDir()
        }
    }

    static File createSharedApiV1Jar(File dir) {
        File jar = new File(dir, "shared-api-1.0.0.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/shared/SharedApi.java": '''package kingsk.isolate.fixture.shared;
public class SharedApi {
    public String getApiVersion() { return "1.0.0"; }
    public String v1OnlyMethod() { return "v1"; }
}
'''
        ])
        return jar
    }

    static File createSharedApiV2Jar(File dir) {
        File jar = new File(dir, "shared-api-2.0.0.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/shared/SharedApi.java": '''package kingsk.isolate.fixture.shared;
public class SharedApi {
    public String getApiVersion() { return "2.0.0"; }
    public String v2OnlyMethod() { return "v2"; }
}
'''
        ])
        return jar
    }

    static File createTestOnlyJar(File dir) {
        File jar = new File(dir, "test-only-api-1.0.0.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/testonly/TestOnlyApi.java": '''package kingsk.isolate.fixture.testonly;
public class TestOnlyApi {
    public String assertSuccess() { return "test-pass"; }
}
'''
        ])
        return jar
    }

    static File createRuntimeOnlyJar(File dir) {
        File jar = new File(dir, "runtime-only-api-1.0.0.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/runtimeonly/RuntimeOnlyApi.java": '''package kingsk.isolate.fixture.runtimeonly;
public class RuntimeOnlyApi {
    public String runtimeInfo() { return "runtime"; }
}
'''
        ])
        return jar
    }

    static File createPrecedenceJarA(File dir) {
        File jar = new File(dir, "precedence-api-A.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/precedence/PrecedenceApi.java": '''package kingsk.isolate.fixture.precedence;
public class PrecedenceApi {
    public String getOrigin() { return "A"; }
}
'''
        ])
        return jar
    }

    static File createPrecedenceJarB(File dir) {
        File jar = new File(dir, "precedence-api-B.jar")
        createJar(jar, [
            "kingsk/isolate/fixture/precedence/PrecedenceApi.java": '''package kingsk.isolate.fixture.precedence;
public class PrecedenceApi {
    public String getOrigin() { return "B"; }
}
'''
        ])
        return jar
    }

    static String getSha256(File file) {
        MessageDigest digest = MessageDigest.getInstance("SHA-256")
        file.withInputStream { is ->
            byte[] buffer = new byte[8192]
            int read
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().encodeHex().toString()
    }
}
