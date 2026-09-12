package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.model.discovery.DeclarationProvenance
import kingsk.grails.lsp.model.discovery.ResolvedDeclaration
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for discovering declarations (methods, fields, properties, types)
 * along with their source-set, artifact, and signature provenance.
 *
 * Enforces INV-DISC-001, INV-DISC-002, and INV-DISC-003:
 * - Real fixture signatures match resolved source/bytecode declarations.
 * - Source attachments are optional enrichment; missing source preserves bytecode declarations.
 * - Each result has source-set/artifact/signature provenance.
 * - Missing modules and wrong scopes do NOT invent fallback APIs; they return empty.
 */
@Slf4j
@CompileStatic
class DeclarationDiscoveryService {

    private static final Map<String, List<ResolvedDeclaration>> DGM_CACHE = new ConcurrentHashMap<>()

    /**
     * Resolves all member declarations (methods, properties, fields, including inherited,
     * generic, overloaded members, and traits) for a ClassNode within a specific project/source-set scope.
     */
    static List<ResolvedDeclaration> resolveMemberDeclarations(
        ClassNode classNode,
        ClassLoader loader,
        String sourceSetName = "main",
        String projectCoords = "project",
        String sourceAttachment = null
    ) {
        if (classNode == null) return []

        List<ResolvedDeclaration> results = []
        Set<String> seenSignatures = new HashSet<>()
        Set<String> visitedClasses = new HashSet<>()

        collectClassHierarchy(classNode, loader, sourceSetName, projectCoords, sourceAttachment, results, seenSignatures, visitedClasses)

        return results
    }

    private static void collectClassHierarchy(
        ClassNode current,
        ClassLoader loader,
        String sourceSetName,
        String projectCoords,
        String sourceAttachment,
        List<ResolvedDeclaration> results,
        Set<String> seenSignatures,
        Set<String> visitedClasses
    ) {
        if (current == null || !visitedClasses.add(current.name)) return

        boolean isCurrentSource = (sourceAttachment != null && !sourceAttachment.isEmpty())
        boolean isTrait = isTraitClass(current)
        String originKind = isTrait ? "TRAIT" : (isCurrentSource ? "SOURCE" : "BYTECODE")
        String artifact = isCurrentSource ? projectCoords : (current.name.startsWith("java.") || current.name.startsWith("javax.") ? "jdk::rt" : projectCoords)

        // 1. Properties
        current.properties?.each { PropertyNode prop ->
            String sig = buildPropertySignature(prop)
            String key = "prop:${current.name}:${prop.name}"
            if (seenSignatures.add(key)) {
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    artifact,
                    current.name,
                    sig,
                    originKind,
                    isCurrentSource ? sourceAttachment : null
                )
                results << new ResolvedDeclaration(
                    prop.name,
                    "PROPERTY",
                    prop.type?.nameWithoutPackage ?: "Object",
                    [],
                    extractGenericTypes(prop.type),
                    prop.static,
                    prov
                )
            }
        }

        // 2. Fields
        current.fields?.each { FieldNode field ->
            String sig = buildFieldSignature(field)
            String key = "field:${current.name}:${field.name}"
            if (seenSignatures.add(key)) {
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    artifact,
                    current.name,
                    sig,
                    originKind,
                    isCurrentSource ? sourceAttachment : null
                )
                results << new ResolvedDeclaration(
                    field.name,
                    "FIELD",
                    field.type?.nameWithoutPackage ?: "Object",
                    [],
                    extractGenericTypes(field.type),
                    field.static,
                    prov
                )
            }
        }

        // 3. Methods (preserves all overloads)
        current.methods?.each { MethodNode method ->
            String sig = buildMethodSignature(method)
            String key = "method:${current.name}:${sig}"
            if (seenSignatures.add(key)) {
                List<String> paramTypes = method.parameters?.collect { it.type?.nameWithoutPackage ?: "Object" } ?: []
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    artifact,
                    current.name,
                    sig,
                    originKind,
                    isCurrentSource ? sourceAttachment : null
                )
                results << new ResolvedDeclaration(
                    method.name,
                    "METHOD",
                    method.returnType?.nameWithoutPackage ?: "void",
                    paramTypes,
                    extractGenericTypes(method.returnType),
                    method.static,
                    prov
                )
            }
        }

        // 4. Interfaces and Traits
        current.interfaces?.each { ClassNode iface ->
            collectClassHierarchy(iface, loader, sourceSetName, projectCoords, null, results, seenSignatures, visitedClasses)
        }

        // 5. Superclass (inherited members)
        if (current.superClass != null && current.superClass.name != current.name) {
            collectClassHierarchy(current.superClass, loader, sourceSetName, projectCoords, null, results, seenSignatures, visitedClasses)
        }
    }

    /**
     * Resolves Groovy Extension Module declarations (DGM, StringGroovyMethods, etc.)
     * applicable to a target ClassNode.
     * When the library/module is absent from the provided ClassLoader, returns EMPTY without inventing fallbacks.
     */
    static List<ResolvedDeclaration> resolveExtensionMethods(
        ClassNode targetType,
        ClassLoader loader,
        String sourceSetName = "main",
        String projectCoords = "org.apache.groovy:groovy"
    ) {
        if (targetType == null || loader == null) return []

        Class<?> resolvedTarget = null
        try {
            if (targetType.isResolved()) {
                resolvedTarget = targetType.typeClass
            } else {
                resolvedTarget = Class.forName(targetType.name, false, loader)
            }
        } catch (Throwable ignored) {
            // Target type cannot be resolved in this classloader
            if (targetType == ClassHelper.STRING_TYPE) resolvedTarget = String.class
            else if (targetType == ClassHelper.LIST_TYPE) resolvedTarget = List.class
            else if (targetType == ClassHelper.MAP_TYPE) resolvedTarget = Map.class
            else if (targetType == ClassHelper.SET_TYPE) resolvedTarget = Set.class
            else if (targetType == ClassHelper.OBJECT_TYPE) resolvedTarget = Object.class
            else return []
        }

        if (resolvedTarget == null) return []

        List<ResolvedDeclaration> results = []
        List<Class<?>> extensionClasses = getExtensionClasses(loader)

        for (Class<?> extClass : extensionClasses) {
            try {
                for (Method m : extClass.methods) {
                    if (Modifier.isStatic(m.modifiers) && Modifier.isPublic(m.modifiers) && m.parameterCount > 0) {
                        Class<?> firstParam = m.parameterTypes[0]
                        if (firstParam.isAssignableFrom(resolvedTarget)) {
                            String sig = buildReflectMethodSignature(m)
                            List<String> paramTypes = (1..<m.parameterCount).collect { m.parameterTypes[it].simpleName }
                            DeclarationProvenance prov = new DeclarationProvenance(
                                sourceSetName,
                                projectCoords,
                                extClass.name,
                                sig,
                                "EXTENSION_MODULE",
                                null
                            )
                            results << new ResolvedDeclaration(
                                m.name,
                                "METHOD",
                                m.returnType.simpleName,
                                paramTypes,
                                [],
                                false,
                                prov
                            )
                        }
                    }
                }
            } catch (Throwable e) {
                log.debug("[DISCOVERY] Failed inspecting extension class {}: {}", extClass.name, e.message)
            }
        }

        return results
    }

    /**
     * Resolves all declarations (members + extension modules) with complete provenance.
     */
    static List<ResolvedDeclaration> resolveAllDeclarations(
        ClassNode classNode,
        ClassLoader loader,
        String sourceSetName = "main",
        String projectCoords = "project",
        String sourceAttachment = null
    ) {
        List<ResolvedDeclaration> all = []
        all.addAll(resolveMemberDeclarations(classNode, loader, sourceSetName, projectCoords, sourceAttachment))
        all.addAll(resolveExtensionMethods(classNode, loader, sourceSetName, projectCoords))
        return all
    }

    private static List<Class<?>> getExtensionClasses(ClassLoader loader) {
        List<Class<?>> classes = []
        // Known standard Groovy extension classes loaded via project classloader
        List<String> knownExtensions = [
            'org.codehaus.groovy.runtime.DefaultGroovyMethods',
            'org.codehaus.groovy.runtime.StringGroovyMethods',
            'org.codehaus.groovy.runtime.ResourceGroovyMethods',
            'org.codehaus.groovy.runtime.DateGroovyMethods',
            'org.apache.groovy.dateutil.extensions.DateGroovyMethods'
        ]

        for (String className : knownExtensions) {
            try {
                Class<?> clazz = Class.forName(className, false, loader)
                classes.add(clazz)
            } catch (Throwable ignored) {
                // Missing module negative case: not present in this loader
            }
        }
        return classes
    }

    private static boolean isTraitClass(ClassNode classNode) {
        if (classNode == null) return false
        if (classNode.annotations?.any { it.classNode?.name?.contains("Trait") }) return true
        if (classNode.name.endsWith("Trait")) return true
        return classNode.isInterface() && classNode.name.contains("Trait")
    }

    static String buildMethodSignature(MethodNode method) {
        String ret = method.returnType?.nameWithoutPackage ?: "void"
        String params = method.parameters?.collect { "${it.type?.nameWithoutPackage ?: 'Object'} ${it.name}" }?.join(", ") ?: ""
        return "${ret} ${method.name}(${params})"
    }

    static String buildFieldSignature(FieldNode field) {
        String type = field.type?.nameWithoutPackage ?: "Object"
        return "${type} ${field.name}"
    }

    static String buildPropertySignature(PropertyNode property) {
        String type = property.type?.nameWithoutPackage ?: "Object"
        return "${type} ${property.name}"
    }

    static String buildReflectMethodSignature(Method method) {
        String ret = method.returnType.simpleName
        // First parameter is 'self' for Groovy extension methods
        String params = (1..<method.parameterCount).collect { method.parameterTypes[it].simpleName }.join(", ")
        return "${ret} ${method.name}(${params})"
    }

    static List<String> extractGenericTypes(ClassNode type) {
        if (type?.genericsTypes == null || type.genericsTypes.length == 0) return []
        return type.genericsTypes.collect {
            if (it.placeholder) {
                return it.name ?: "Object"
            }
            return it.type?.nameWithoutPackage ?: (it.name ?: "Object")
        }
    }
}

