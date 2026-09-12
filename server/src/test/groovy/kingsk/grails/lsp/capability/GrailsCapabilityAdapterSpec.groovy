package kingsk.grails.lsp.capability

import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.core.capability.GormCapabilityAdapter
import kingsk.grails.lsp.core.capability.GrailsConfigCapabilityAdapter
import kingsk.grails.lsp.core.capability.GrailsInjectionCapabilityAdapter
import kingsk.grails.lsp.core.capability.TagLibCapabilityAdapter
import kingsk.grails.lsp.model.discovery.DeclarationProvenance
import kingsk.grails.lsp.model.discovery.ResolvedDeclaration
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GradleModel
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.PropertyNode
import org.eclipse.lsp4j.CompletionItem
import spock.lang.Specification

import java.lang.reflect.Modifier

/**
 * Acceptance test specification for R2-05:
 * Implement Grails capability adapters.
 *
 * Requirements:
 * R2-05/1: Each admitted adapter documents detection, inputs, derivation, applicability,
 *          invalidation, limits, and actual artifact fixtures.
 * R2-05/2: Cover current-domain GORM dynamic finders (findBy*, findAllBy*, countBy*),
 *          GORM properties (id, version, errors), injection/delegates (controller web scopes,
 *          injected services, Criteria builders, command objects), and TagLib conventions,
 *          with no-capability and renamed/deleted-domain negative cases.
 * R2-05/3: Unsupported/runtime-dependent behavior remains explicit; adapters do not execute
 *          an application for ordinary reads or accumulate manual method catalogs.
 */
class GrailsCapabilityAdapterSpec extends Specification {

    def "R2-05/1: All admitted capability adapters document detection, inputs, derivation, applicability, invalidation, and limits"() {
        expect: "GORM capability adapter exposes full architectural declarations"
        GormCapabilityAdapter.INSTANCE.capabilityName == "GORM"
        GormCapabilityAdapter.INSTANCE.detectionEvidence.contains("grails.gorm")
        GormCapabilityAdapter.INSTANCE.inputsDescription.contains("Domain ClassNode")
        GormCapabilityAdapter.INSTANCE.derivationDescription.toLowerCase().contains("dynamic finders")
        GormCapabilityAdapter.INSTANCE.applicabilityDescription.contains("Grails")
        GormCapabilityAdapter.INSTANCE.invalidationDescription.contains("property addition, rename, deletion")
        GormCapabilityAdapter.INSTANCE.limitsDescription.contains("running a Grails application")

        and: "Grails injection capability adapter exposes full architectural declarations"
        GrailsInjectionCapabilityAdapter.INSTANCE.capabilityName == "GrailsInjection"
        GrailsInjectionCapabilityAdapter.INSTANCE.detectionEvidence.contains("Controller")
        GrailsInjectionCapabilityAdapter.INSTANCE.inputsDescription.contains("Controller")
        GrailsInjectionCapabilityAdapter.INSTANCE.derivationDescription.contains("Injects")
        GrailsInjectionCapabilityAdapter.INSTANCE.applicabilityDescription.contains("Grails")
        GrailsInjectionCapabilityAdapter.INSTANCE.invalidationDescription.contains("RequestContext")
        GrailsInjectionCapabilityAdapter.INSTANCE.limitsDescription.contains("zero Spring container startup")

        and: "TagLib capability adapter exposes full architectural declarations"
        TagLibCapabilityAdapter.INSTANCE.capabilityName == "TagLib"
        TagLibCapabilityAdapter.INSTANCE.detectionEvidence.contains("TagLib")
        TagLibCapabilityAdapter.INSTANCE.inputsDescription.contains("Default 'g' namespace")
        TagLibCapabilityAdapter.INSTANCE.derivationDescription.contains("GSP tag")
        TagLibCapabilityAdapter.INSTANCE.applicabilityDescription.contains("GSP")
        TagLibCapabilityAdapter.INSTANCE.invalidationDescription.contains("TagLib")
        TagLibCapabilityAdapter.INSTANCE.limitsDescription.toLowerCase().contains("pure static ast")

        and: "Grails Config capability adapter exposes full architectural declarations"
        GrailsConfigCapabilityAdapter.INSTANCE.capabilityName == "GrailsConfig"
        GrailsConfigCapabilityAdapter.INSTANCE.detectionEvidence.contains("configuration")
        GrailsConfigCapabilityAdapter.INSTANCE.inputsDescription.contains("grailsApplication.config")
        GrailsConfigCapabilityAdapter.INSTANCE.derivationDescription.contains("Standard Grails configuration")
        GrailsConfigCapabilityAdapter.INSTANCE.applicabilityDescription.contains("Grails")
        GrailsConfigCapabilityAdapter.INSTANCE.invalidationDescription.contains("RequestContext")
        GrailsConfigCapabilityAdapter.INSTANCE.limitsDescription.contains("runtime profiles")
    }

    def "R2-05/2: GORM capability adapter derives dynamic finders, static methods, instance methods, and injected properties"() {
        given: "A domain ClassNode with multiple properties"
        ClassNode personClass = new ClassNode("com.example.Person", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        personClass.addProperty(new PropertyNode("firstName", Modifier.PUBLIC, ClassHelper.STRING_TYPE, personClass, null, null, null))
        personClass.addProperty(new PropertyNode("age", Modifier.PUBLIC, ClassHelper.Integer_TYPE, personClass, null, null, null))
        personClass.addField(new FieldNode("constraints", Modifier.STATIC | Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, personClass, null))

        RequestContext ctx = Mock(RequestContext)
        GradleModel gm = new GradleModel(
            name: "test-grails",
            group: "com.example",
            version: "1.0",
            grailsVersion: "7.0.0",
            groovyVersion: "4.0.23",
            isGrailsProject: true,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [new DependencyNode(group: "org.apache.grails", name: "grails-data-hibernate5", version: "7.0.0", scope: "compileClasspath")] as Set,
            sourceSets: [:]
        )
        ctx.grailsProject() >> gm
        ctx.classLoader() >> getClass().classLoader
        ctx.uri() >> "file:///grails-app/domain/com/example/Person.groovy"

        when: "Static completions are derived for Person"
        List<CompletionItem> staticItems = GormCapabilityAdapter.INSTANCE.getStaticCompletions(personClass, ctx)
        List<String> staticLabels = staticItems.collect { it.label }

        then: "Single-property dynamic finders are derived"
        staticLabels.contains("findByFirstName")
        staticLabels.contains("findAllByFirstName")
        staticLabels.contains("countByFirstName")
        staticLabels.contains("findByAge")
        staticLabels.contains("findAllByAge")
        staticLabels.contains("countByAge")

        and: "Multi-property boolean combination finders are derived"
        staticLabels.contains("findByFirstNameAndAge")
        staticLabels.contains("findAllByFirstNameAndAge")
        staticLabels.contains("countByFirstNameAndAge")
        staticLabels.contains("findByFirstNameOrAge")
        staticLabels.contains("findAllByFirstNameOrAge")
        staticLabels.contains("countByFirstNameOrAge")

        and: "Standard GORM static methods are present"
        staticLabels.contains("get")
        staticLabels.contains("list")
        staticLabels.contains("count")
        staticLabels.contains("where")
        staticLabels.contains("withCriteria")
        staticLabels.contains("createCriteria")

        and: "Item details reflect dynamic finder capability"
        def finderItem = staticItems.find { it.label == "findByFirstName" }
        finderItem != null
        finderItem.detail == "GORM dynamic finder"

        when: "Instance completions are derived for a Person instance"
        List<CompletionItem> instanceItems = GormCapabilityAdapter.INSTANCE.getInstanceCompletions(personClass, ctx)
        List<String> instanceLabels = instanceItems.collect { it.label }

        then: "Injected GORM properties are present"
        instanceLabels.contains("id")
        instanceLabels.contains("version")
        instanceLabels.contains("errors")

        and: "GORM instance methods are present"
        instanceLabels.contains("save")
        instanceLabels.contains("delete")
        instanceLabels.contains("validate")
        instanceLabels.contains("hasErrors")
        instanceLabels.contains("discard")
        instanceLabels.contains("refresh")

        and: "Criteria builder completions are available"
        List<CompletionItem> criteriaItems = GormCapabilityAdapter.INSTANCE.getCriteriaCompletions()
        List<String> criteriaLabels = criteriaItems.collect { it.label }
        criteriaLabels.contains("eq")
        criteriaLabels.contains("like")
        criteriaLabels.contains("between")
        criteriaLabels.contains("order")
        criteriaLabels.contains("projections")
        criteriaLabels.contains("isNull")
        criteriaLabels.contains("inList")
    }

    def "R2-05/2: ResolvedDeclaration items have CAPABILITY_ADAPTER origin and exact provenance"() {
        given: "A domain ClassNode and RequestContext"
        ClassNode bookClass = new ClassNode("com.example.Book", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        bookClass.addProperty(new PropertyNode("title", Modifier.PUBLIC, ClassHelper.STRING_TYPE, bookClass, null, null, null))
        bookClass.addField(new FieldNode("constraints", Modifier.STATIC | Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, bookClass, null))

        RequestContext ctx = Mock(RequestContext)
        GradleModel gm = new GradleModel(
            name: "test-grails",
            group: "com.example",
            version: "1.0",
            grailsVersion: "7.0.0",
            groovyVersion: "4.0.23",
            isGrailsProject: true,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [new DependencyNode(group: "org.apache.grails", name: "grails-data-hibernate5", version: "7.0.0", scope: "compileClasspath")] as Set,
            sourceSets: [:]
        )
        ctx.grailsProject() >> gm
        ctx.classLoader() >> getClass().classLoader
        ctx.uri() >> "file:///grails-app/domain/com/example/Book.groovy"

        when: "Static declarations are resolved via GormCapabilityAdapter"
        List<ResolvedDeclaration> decls = GormCapabilityAdapter.INSTANCE.resolveDeclarations(bookClass, ctx, true)

        then: "Every declaration has CAPABILITY_ADAPTER origin"
        decls.size() > 0
        decls.every { it.provenance.originKind == "CAPABILITY_ADAPTER" }

        and: "Dynamic finders and static methods carry the GORM artifact coordinate"
        def findByTitleDecl = decls.find { it.name == "findByTitle" }
        findByTitleDecl != null
        findByTitleDecl.returnType == "Book"
        findByTitleDecl.parameterTypes == ["String"]
        findByTitleDecl.provenance.artifactCoordinates == "org.grails:grails-datastore-gorm"

        def findAllByTitleDecl = decls.find { it.name == "findAllByTitle" }
        findAllByTitleDecl != null
        findAllByTitleDecl.returnType == "List"

        def countByTitleDecl = decls.find { it.name == "countByTitle" }
        countByTitleDecl != null
        countByTitleDecl.returnType == "Number"
    }

    def "R2-05/2: Negative cases: no-capability project and missing GORM yield zero GORM completions"() {
        given: "A domain-looking class in a non-Grails project"
        ClassNode personClass = new ClassNode("com.example.Person", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        personClass.addProperty(new PropertyNode("name", Modifier.PUBLIC, ClassHelper.STRING_TYPE, personClass, null, null, null))
        personClass.addField(new FieldNode("constraints", Modifier.STATIC | Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, personClass, null))

        RequestContext nonGrailsCtx = Mock(RequestContext)
        GradleModel nonGrailsGm = new GradleModel(
            name: "regular-groovy",
            group: "com.example",
            version: "1.0",
            grailsVersion: null,
            groovyVersion: "4.0.23",
            isGrailsProject: false,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [] as Set,
            sourceSets: [:]
        )
        nonGrailsCtx.grailsProject() >> nonGrailsGm
        nonGrailsCtx.classLoader() >> getClass().classLoader
        nonGrailsCtx.uri() >> "file:///src/main/groovy/com/example/Person.groovy"

        expect: "GORM adapter is not applicable in a non-Grails project"
        !GormCapabilityAdapter.INSTANCE.isApplicable(nonGrailsCtx, nonGrailsCtx.uri())
        GormCapabilityAdapter.INSTANCE.getStaticCompletions(personClass, nonGrailsCtx).isEmpty()
        GormCapabilityAdapter.INSTANCE.getInstanceCompletions(personClass, nonGrailsCtx).isEmpty()
        GormCapabilityAdapter.INSTANCE.resolveDeclarations(personClass, nonGrailsCtx, true).isEmpty()

        when: "A Grails project explicitly lacks GORM on dependencies and classloader"
        RequestContext noGormCtx = Mock(RequestContext)
        GradleModel noGormGm = new GradleModel(
            name: "no-gorm-grails",
            group: "com.example",
            version: "1.0",
            grailsVersion: "7.0.0",
            groovyVersion: "4.0.23",
            isGrailsProject: true,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [new DependencyNode(group: "org.apache.commons", name: "commons-lang3", version: "3.12.0", scope: "compileClasspath")] as Set,
            sourceSets: [:]
        )
        // URLClassLoader with empty classpath ensures marker classes fail resolution
        ClassLoader emptyLoader = new URLClassLoader([] as java.net.URL[], null)
        noGormCtx.grailsProject() >> noGormGm
        noGormCtx.classLoader() >> emptyLoader
        noGormCtx.uri() >> "file:///grails-app/domain/com/example/Person.groovy"

        then: "GORM capability adapter is strictly not applicable"
        !GormCapabilityAdapter.INSTANCE.isApplicable(noGormCtx, noGormCtx.uri())
        GormCapabilityAdapter.INSTANCE.getStaticCompletions(personClass, noGormCtx).isEmpty()
    }

    def "R2-05/2: Negative cases: renamed and deleted domain properties immediately invalidate finders"() {
        given: "A domain class starting with property 'title'"
        ClassNode postClass = new ClassNode("com.example.Post", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        PropertyNode titleProp = new PropertyNode("title", Modifier.PUBLIC, ClassHelper.STRING_TYPE, postClass, null, null, null)
        postClass.addProperty(titleProp)
        postClass.addField(new FieldNode("constraints", Modifier.STATIC | Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, postClass, null))

        RequestContext ctx = Mock(RequestContext)
        GradleModel gm = new GradleModel(
            name: "test-grails",
            group: "com.example",
            version: "1.0",
            grailsVersion: "7.0.0",
            groovyVersion: "4.0.23",
            isGrailsProject: true,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [new DependencyNode(group: "org.apache.grails", name: "grails-data-hibernate5", version: "7.0.0", scope: "compileClasspath")] as Set,
            sourceSets: [:]
        )
        ctx.grailsProject() >> gm
        ctx.classLoader() >> getClass().classLoader
        ctx.uri() >> "file:///grails-app/domain/com/example/Post.groovy"

        when: "Initial completions are checked"
        List<String> initialFinders = GormCapabilityAdapter.INSTANCE.getStaticCompletions(postClass, ctx).collect { it.label }

        then: "findByTitle is present; findBySubject is absent"
        initialFinders.contains("findByTitle")
        !initialFinders.contains("findBySubject")

        when: "Property is renamed from 'title' to 'subject'"
        postClass.properties.remove(titleProp)
        postClass.fields.removeIf { it.name == 'title' }
        PropertyNode subjectProp = new PropertyNode("subject", Modifier.PUBLIC, ClassHelper.STRING_TYPE, postClass, null, null, null)
        postClass.addProperty(subjectProp)

        List<String> renamedFinders = GormCapabilityAdapter.INSTANCE.getStaticCompletions(postClass, ctx).collect { it.label }

        then: "findByTitle disappears and findBySubject appears immediately"
        !renamedFinders.contains("findByTitle")
        renamedFinders.contains("findBySubject")
        renamedFinders.contains("findAllBySubject")
        renamedFinders.contains("countBySubject")

        when: "Property 'subject' is deleted"
        postClass.properties.remove(subjectProp)
        postClass.fields.removeIf { it.name == 'subject' }
        List<String> deletedFinders = GormCapabilityAdapter.INSTANCE.getStaticCompletions(postClass, ctx).collect { it.label }

        then: "All dynamic finders for subject disappear"
        !deletedFinders.contains("findBySubject")
        !deletedFinders.contains("findAllBySubject")
        !deletedFinders.contains("countBySubject")
    }

    def "R2-05/2: Controller injection, command objects, TagLib, and Config conventions"() {
        expect: "Controller web properties are available"
        List<String> controllerProps = GrailsInjectionCapabilityAdapter.INSTANCE.getControllerProperties().collect { it.label }
        controllerProps.contains("params")
        controllerProps.contains("request")
        controllerProps.contains("response")
        controllerProps.contains("session")
        controllerProps.contains("flash")
        controllerProps.contains("actionName")
        controllerProps.contains("controllerName")

        and: "Controller methods and render parameters are available"
        List<String> controllerMethods = GrailsInjectionCapabilityAdapter.INSTANCE.getControllerMethods().collect { it.label }
        controllerMethods.contains("render")
        controllerMethods.contains("redirect")
        controllerMethods.contains("respond")
        controllerMethods.contains("forward")

        List<String> renderParams = GrailsInjectionCapabilityAdapter.INSTANCE.getRenderParameters().collect { it.label }
        renderParams.contains("view")
        renderParams.contains("model")
        renderParams.contains("text")
        renderParams.contains("status")

        and: "Command objects implementing Validateable receive validation methods"
        ClassNode cmdClass = new ClassNode("com.example.LoginCommand", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        cmdClass.addInterface(ClassHelper.make("grails.validation.Validateable"))
        cmdClass.addProperty(new PropertyNode("username", Modifier.PUBLIC, ClassHelper.STRING_TYPE, cmdClass, null, null, null))
        GrailsInjectionCapabilityAdapter.INSTANCE.isValidateable(cmdClass)

        List<String> cmdItems = GrailsInjectionCapabilityAdapter.INSTANCE.getValidateableCompletions(cmdClass).collect { it.label }
        cmdItems.contains("validate")
        cmdItems.contains("hasErrors")
        cmdItems.contains("errors")
        cmdItems.contains("clearErrors")
        cmdItems.contains("username")

        and: "TagLib completions supply default 'g:' namespace tags"
        RequestContext tagCtx = Mock(RequestContext)
        List<String> tags = TagLibCapabilityAdapter.INSTANCE.getTagCompletions("g", tagCtx).collect { it.label }
        tags.contains("link")
        tags.contains("each")
        tags.contains("if")
        tags.contains("form")
        tags.contains("textField")
        tags.contains("select")

        and: "Grails Config completions supply standard config properties"
        List<String> configProps = GrailsConfigCapabilityAdapter.INSTANCE.getConfigCompletions().collect { it.label }
        configProps.contains("dataSource")
        configProps.contains("grails")
        configProps.contains("server")
        configProps.contains("spring")
    }

    def "R2-05/3: Unsupported/runtime behavior is explicit and derivation executes without running an application"() {
        given: "A domain class with 5 properties"
        ClassNode domain = new ClassNode("com.example.Vehicle", Modifier.PUBLIC, ClassHelper.OBJECT_TYPE)
        domain.addField(new FieldNode("constraints", Modifier.STATIC | Modifier.PUBLIC, ClassHelper.OBJECT_TYPE, domain, null))
        ['make', 'model', 'vin', 'year', 'color'].each { String prop ->
            domain.addProperty(new PropertyNode(prop, Modifier.PUBLIC, ClassHelper.STRING_TYPE, domain, null, null, null))
        }

        RequestContext ctx = Mock(RequestContext)
        GradleModel gm = new GradleModel(
            name: "test-grails",
            group: "com.example",
            version: "1.0",
            grailsVersion: "7.0.0",
            groovyVersion: "4.0.23",
            isGrailsProject: true,
            rootDirectory: new File("."),
            sourceDirectories: [] as Set,
            testDirectories: [] as Set,
            dependencies: [new DependencyNode(group: "org.apache.grails", name: "grails-data-hibernate5", version: "7.0.0", scope: "compileClasspath")] as Set,
            sourceSets: [:]
        )
        ctx.grailsProject() >> gm
        ctx.classLoader() >> getClass().classLoader
        ctx.uri() >> "file:///grails-app/domain/com/example/Vehicle.groovy"

        when: "Derivations are executed"
        long start = System.nanoTime()
        List<CompletionItem> items = GormCapabilityAdapter.INSTANCE.getStaticCompletions(domain, ctx)
        long durationMs = (System.nanoTime() - start) / 1_000_000

        then: "Derivation completes in pure static AST memory without disk/app execution"
        durationMs < 100
        items.size() > 20
        items.any { it.label == "findByMakeAndModel" }
        items.any { it.label == "findAllByVin" }
        items.any { it.label == "countByYearOrColor" }

        and: "Limits descriptions explicitly document zero Spring/app execution"
        GormCapabilityAdapter.INSTANCE.limitsDescription.contains("running a Grails application") ||
            GormCapabilityAdapter.INSTANCE.limitsDescription.contains("Pure static AST derivation")
    }
}
