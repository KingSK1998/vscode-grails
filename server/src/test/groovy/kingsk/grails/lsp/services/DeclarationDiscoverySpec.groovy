package kingsk.grails.lsp.services

import kingsk.grails.lsp.model.discovery.DeclarationProvenance
import kingsk.grails.lsp.model.discovery.ResolvedDeclaration
import kingsk.grails.lsp.utils.grails.GrailsHelperIntegration
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.GenericsType
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.codehaus.groovy.ast.builder.AstBuilder
import spock.lang.Specification

import java.lang.reflect.Modifier

/**
 * Acceptance test specification for R2-04:
 * Discover declarations and explain their origins.
 *
 * Requirements:
 * R2-04/1: Real fixture signatures for inherited/generic/overloaded members, traits and
 *          Groovy extension modules match resolved declarations; source attachments are optional enrichment.
 * R2-04/2: Each result has source-set/artifact/signature provenance. Wrong-scope and
 *          missing-module negative cases do not invent fallback APIs.
 * R2-04/3: Inventory hardcoded lists by category; replace framework/library fallback
 *          inventories with evidence-backed resolution or explicit unavailable status.
 *          Preserve legitimate grammar/snippet/protocol constants with tests.
 */
class DeclarationDiscoverySpec extends Specification {

    def "R2-04/1: Real fixture signatures for inherited, generic, and overloaded members match declarations"() {
        given: "A class hierarchy with base class, generics, and overloaded methods"
        ClassNode baseClass = new ClassNode("com.example.BaseRepo", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        baseClass.addMethod(new MethodNode("findById", Modifier.PUBLIC, ClassHelper.STRING_TYPE, [new Parameter(ClassHelper.long_TYPE, "id")] as Parameter[], [] as ClassNode[], null))
        baseClass.addProperty(new PropertyNode("auditUser", Modifier.PUBLIC, ClassHelper.STRING_TYPE, baseClass, null, null, null))

        ClassNode subClass = new ClassNode("com.example.UserRepo", Modifier.PUBLIC, baseClass)
        // Overloaded methods
        subClass.addMethod(new MethodNode("findByName", Modifier.PUBLIC, ClassHelper.STRING_TYPE, [new Parameter(ClassHelper.STRING_TYPE, "name")] as Parameter[], [] as ClassNode[], null))
        subClass.addMethod(new MethodNode("findByName", Modifier.PUBLIC, ClassHelper.LIST_TYPE, [new Parameter(ClassHelper.STRING_TYPE, "name"), new Parameter(ClassHelper.boolean_TYPE, "exact")] as Parameter[], [] as ClassNode[], null))
        // Generic return type
        ClassNode genericList = ClassHelper.make(List)
        genericList.setGenericsTypes([new GenericsType(ClassHelper.make("com.example.User"))] as GenericsType[])
        subClass.addMethod(new MethodNode("listAll", Modifier.PUBLIC, genericList, [] as Parameter[], [] as ClassNode[], null))

        when: "Member declarations are resolved"
        List<ResolvedDeclaration> decls = DeclarationDiscoveryService.resolveMemberDeclarations(
            subClass,
            getClass().classLoader,
            "main",
            "project::user-service",
            "file:///src/main/groovy/com/example/UserRepo.groovy"
        )

        then: "Both overloads of findByName are resolved with distinct signatures and parameter types"
        def nameOverloads = decls.findAll { it.name == "findByName" }
        nameOverloads.size() == 2
        nameOverloads.any { it.parameterTypes == ["String"] && it.returnType == "String" && it.provenance.signature == "String findByName(String name)" }
        nameOverloads.any { it.parameterTypes == ["String", "boolean"] && it.returnType == "List" && it.provenance.signature == "List findByName(String name, boolean exact)" }

        and: "Inherited member from BaseRepo is resolved with BaseRepo provenance"
        def inheritedMethod = decls.find { it.name == "findById" }
        inheritedMethod != null
        inheritedMethod.provenance.declaringClass == "com.example.BaseRepo"
        inheritedMethod.provenance.signature == "String findById(long id)"

        def inheritedProp = decls.find { it.name == "auditUser" }
        inheritedProp != null
        inheritedProp.provenance.declaringClass == "com.example.BaseRepo"
        inheritedProp.kind == "PROPERTY"

        and: "Generic return type is preserved"
        def listAll = decls.find { it.name == "listAll" }
        listAll != null
        listAll.genericTypes.contains("User")
    }

    def "R2-04/1: Traits and Groovy extension modules match resolved declarations"() {
        given: "A class implementing a Groovy Trait"
        ClassNode traitNode = new ClassNode("com.example.AuditableTrait", Modifier.PUBLIC | Modifier.ABSTRACT, ClassHelper.OBJECT_TYPE)
        traitNode.addMethod(new MethodNode("getCreatedBy", Modifier.PUBLIC, ClassHelper.STRING_TYPE, [] as Parameter[], [] as ClassNode[], null))
        traitNode.addMethod(new MethodNode("touch", Modifier.PUBLIC, ClassHelper.VOID_TYPE, [] as Parameter[], [] as ClassNode[], null))

        ClassNode entityNode = new ClassNode("com.example.Order", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, [traitNode] as ClassNode[], [] as org.codehaus.groovy.ast.MixinNode[])
        entityNode.addMethod(new MethodNode("submit", Modifier.PUBLIC, ClassHelper.boolean_TYPE, [] as Parameter[], [] as ClassNode[], null))

        when: "Declarations are resolved for the entity"
        List<ResolvedDeclaration> decls = DeclarationDiscoveryService.resolveMemberDeclarations(
            entityNode,
            getClass().classLoader,
            "main",
            "project::order-service",
            "file:///src/main/groovy/com/example/Order.groovy"
        )

        then: "Trait methods are marked with originKind TRAIT"
        def touchMethod = decls.find { it.name == "touch" }
        touchMethod != null
        touchMethod.provenance.originKind == "TRAIT"
        touchMethod.provenance.declaringClass == "com.example.AuditableTrait"
        touchMethod.provenance.signature == "void touch()"

        and: "Own methods are marked with originKind SOURCE and retain source attachment"
        def submitMethod = decls.find { it.name == "submit" }
        submitMethod != null
        submitMethod.provenance.originKind == "SOURCE"
        submitMethod.provenance.hasSourceAttachment()
        submitMethod.provenance.sourceAttachment == "file:///src/main/groovy/com/example/Order.groovy"

        when: "Groovy extension methods are resolved for String"
        List<ResolvedDeclaration> extDecls = DeclarationDiscoveryService.resolveExtensionMethods(
            ClassHelper.STRING_TYPE,
            getClass().classLoader,
            "main",
            "org.apache.groovy:groovy:4.0.23"
        )

        then: "Standard extension methods like capitalize and toInteger are resolved with EXTENSION_MODULE provenance"
        extDecls.size() > 0
        extDecls.every { it.provenance.originKind == "EXTENSION_MODULE" }
        extDecls.any { it.name == "capitalize" && it.provenance.declaringClass.contains("StringGroovyMethods") }
        extDecls.any { it.name == "toInteger" && it.provenance.declaringClass.contains("StringGroovyMethods") }
    }

    def "R2-04/1: Source attachments are optional enrichment and do not erase bytecode declarations"() {
        given: "A bytecode-only class without source attachment"
        ClassNode bytecodeClass = new ClassNode("java.util.ArrayList", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        bytecodeClass.addMethod(new MethodNode("trimToSize", Modifier.PUBLIC, ClassHelper.VOID_TYPE, [] as Parameter[], [] as ClassNode[], null))

        when: "Declarations are resolved without source attachment"
        List<ResolvedDeclaration> decls = DeclarationDiscoveryService.resolveMemberDeclarations(
            bytecodeClass,
            getClass().classLoader,
            "main",
            "jdk::rt",
            null
        )

        then: "Declarations resolve with originKind BYTECODE and no source attachment"
        def trimMethod = decls.find { it.name == "trimToSize" }
        trimMethod != null
        trimMethod.provenance.originKind == "BYTECODE"
        !trimMethod.provenance.hasSourceAttachment()
        trimMethod.provenance.sourceAttachment == null
    }

    def "R2-04/2: Provenance is attached to all results with source-set, artifact, and signature"() {
        given: "A class in the test source set"
        ClassNode testClass = new ClassNode("com.example.UserSpec", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        testClass.addMethod(new MethodNode("testLogin", Modifier.PUBLIC, ClassHelper.VOID_TYPE, [] as Parameter[], [] as ClassNode[], null))

        when: "Declarations are resolved in test scope"
        List<ResolvedDeclaration> decls = DeclarationDiscoveryService.resolveMemberDeclarations(
            testClass,
            getClass().classLoader,
            "test",
            "project::user-service:test",
            "file:///src/test/groovy/com/example/UserSpec.groovy"
        )

        then: "All declarations carry exact provenance matching their scope and origin"
        decls.size() > 0
        decls.every { it.provenance.sourceSetName == "test" }
        decls.find { it.name == "testLogin" }.provenance.artifactCoordinates == "project::user-service:test"
        decls.findAll { it.provenance.declaringClass == "java.lang.Object" }.every { it.provenance.artifactCoordinates == "jdk::rt" }
        decls.every { it.provenance.signature != null && !it.provenance.signature.isEmpty() }
        decls.every { it.provenance.declaringClass != null && !it.provenance.declaringClass.isEmpty() }
    }

    def "R2-04/2: Wrong-scope and missing-module negative cases do not invent fallback APIs"() {
        given: "An empty isolated ClassLoader with no Grails, GORM, or Controller classes"
        ClassLoader emptyLoader = new URLClassLoader(new URL[0], (ClassLoader) null)

        when: "GORM instance/static methods are queried on an empty ClassLoader with no fallbacks"
        def gormInstanceMethods = GrailsHelperIntegration.getGormInstanceMethods(emptyLoader, false)
        def gormStaticMethods = GrailsHelperIntegration.getGormStaticMethods(emptyLoader, false)

        then: "They return empty lists and do NOT invent fallback method lists"
        gormInstanceMethods.isEmpty()
        gormStaticMethods.isEmpty()

        when: "Controller and TagLib methods are queried on an empty ClassLoader with no fallbacks"
        def controllerMethods = GrailsHelperIntegration.getControllerMethods(emptyLoader, false)
        def tagLibMethods = GrailsHelperIntegration.getTagLibMethods(emptyLoader, false)

        then: "They return empty lists and do NOT invent fallback method lists"
        controllerMethods.isEmpty()
        tagLibMethods.isEmpty()

        when: "Artifact types and config keys are queried on an empty ClassLoader with no fallbacks"
        def artifactTypes = GrailsHelperIntegration.getGrailsArtifactTypes(emptyLoader, false)
        def configKeys = GrailsHelperIntegration.getGrailsConfigurationKeys(emptyLoader, false)
        def constraints = GrailsHelperIntegration.getGrailsConstraints(emptyLoader, false)

        then: "They return empty lists and do NOT invent fallback lists"
        artifactTypes.isEmpty()
        configKeys.isEmpty()
        constraints.isEmpty()

        when: "Extension methods are queried on an unresolvable type or empty ClassLoader"
        ClassNode customType = new ClassNode("com.unresolvable.FakeType", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        def extMethods = DeclarationDiscoveryService.resolveExtensionMethods(customType, emptyLoader, "main", "isolated")

        then: "It returns empty without throwing errors or inventing fallback methods"
        extMethods.isEmpty()
    }

    def "R2-04/3: Categorized inventory replaces framework fallbacks while preserving grammar and protocol constants"() {
        given: "Category A: Framework fallback inventories are verified to return empty when absent"
        ClassLoader emptyLoader = new URLClassLoader(new URL[0], (ClassLoader) null)

        expect: "No invented GORM/Controller APIs when absent without fallback"
        GrailsHelperIntegration.getGormInstanceMethods(emptyLoader, false) == []
        GrailsHelperIntegration.getGormStaticMethods(emptyLoader, false) == []
        GrailsHelperIntegration.getControllerMethods(emptyLoader, false) == []
        GrailsHelperIntegration.getTagLibMethods(emptyLoader, false) == []
        GrailsHelperIntegration.getGrailsArtifactTypes(emptyLoader, false) == []
        GrailsHelperIntegration.getGrailsConfigurationKeys(emptyLoader, false) == []
        GrailsHelperIntegration.getGrailsConstraints(emptyLoader, false) == []

        and: "Category B: Legitimate Groovy keywords and language grammar constants are preserved"
        GrailsUtils.GROOVY_KEYWORDS != null
        GrailsUtils.GROOVY_KEYWORDS.contains("class")
        GrailsUtils.GROOVY_KEYWORDS.contains("def")
        GrailsUtils.GROOVY_KEYWORDS.contains("trait")
        GrailsUtils.GROOVY_KEYWORDS.contains("interface")
        GrailsUtils.GROOVY_KEYWORDS.contains("public")
        GrailsUtils.GROOVY_KEYWORDS.contains("static")

        and: "Category B: Legitimate protocol/dummy identifier constants are preserved"
        GrailsUtils.DUMMY_COMPLETION_IDENTIFIER == "__GRAILS_DUMMY_COMPLETION__"
        GrailsUtils.DUMMY_COMPLETION_CONSTRUCTOR == "__GRAILS_DUMMY_COMPLETION__()"
    }
}

