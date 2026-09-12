package kingsk.grails.lsp.core.capability

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.discovery.DeclarationProvenance
import kingsk.grails.lsp.model.discovery.ResolvedDeclaration
import kingsk.grails.lsp.model.dto.DependencyNode
import kingsk.grails.lsp.model.dto.GradleModel
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.PropertyNode
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Capability adapter for Grails Object Relational Mapping (GORM) conventions.
 *
 * Implements:
 * 1. Detection: GORM artifacts and traits present on project classpath.
 * 2. Inputs: Domain ClassNode properties, relationships, and AST hierarchy.
 * 3. Derivations: Dynamic finders (findBy*, findAllBy*, countBy*), GORM instance methods,
 *    static methods, criteria restrictions, and injected properties.
 * 4. Applicability: Scoped to GORM domains within Grails projects; strictly absent in non-Grails projects.
 * 5. Invalidation: Source property rename/deletion immediately alters dynamic finder output.
 * 6. Limits: Static AST derivation; zero runtime application execution.
 */
@Slf4j
@CompileStatic
class GormCapabilityAdapter implements GrailsCapabilityAdapter {

    public static final GormCapabilityAdapter INSTANCE = new GormCapabilityAdapter()

    private static final List<String> GORM_STATIC_METHODS = Collections.unmodifiableList([
        'get', 'load', 'read', 'getAll', 'list', 'count', 'exists',
        'where', 'withCriteria', 'withTransaction', 'createCriteria',
        'findWhere', 'findAllWhere'
    ])

    private static final List<String> GORM_INSTANCE_METHODS = Collections.unmodifiableList([
        'save', 'delete', 'ident', 'attach', 'discard', 'lock',
        'refresh', 'validate', 'hasErrors', 'isAttached', 'markDirty'
    ])

    private static final List<String> CRITERIA_METHODS = Collections.unmodifiableList([
        'eq', 'like', 'ilike', 'gt', 'ge', 'lt', 'le', 'between',
        'inList', 'isNull', 'isNotNull', 'order', 'projections',
        'and', 'or', 'not', 'sqlRestriction', 'idEq'
    ])

    private static final Map<ClassLoader, Boolean> GORM_CLASSPATH_CACHE = new ConcurrentHashMap<>()

    @Override
    String getCapabilityName() {
        return "GORM"
    }

    @Override
    String getDetectionEvidence() {
        return "grails.gorm.GormEntity or org.grails.datastore.gorm.GormEntity or grails.gorm.annotation.Entity on project classpath"
    }

    @Override
    String getInputsDescription() {
        return "Domain ClassNode property declarations, constraints, relationships, and AST hierarchy"
    }

    @Override
    String getDerivationDescription() {
        return "Dynamic finders (findBy*, findAllBy*, countBy*), instance methods (save, delete, etc.), static methods (get, list, etc.), criteria restrictions, and injected properties (id, version, errors)"
    }

    @Override
    String getApplicabilityDescription() {
        return "Domain classes within Grails projects with GORM dependencies; excluded from non-Grails projects and non-domain classes"
    }

    @Override
    String getInvalidationDescription() {
        return "Evaluated per RequestContext snapshot; immediately updates on property addition, rename, deletion, or dependency change"
    }

    @Override
    String getLimitsDescription() {
        return "Pure static AST derivation without running a Grails application, booting Spring, or initializing Hibernate mappings"
    }

    @Override
    boolean isApplicable(RequestContext ctx, String uri = null) {
        if (ctx == null) return false
        GradleModel gp = ctx.grailsProject()
        if (gp == null || !gp.isGrailsProject) return false

        if (isGormOnClasspath(ctx.classLoader())) return true

        if (gp.dependencies != null && !gp.dependencies.isEmpty()) {
            return gp.dependencies.any { DependencyNode node ->
                String coord = "${node.group}:${node.name}".toLowerCase()
                coord.contains("gorm") || coord.contains("hibernate") || coord.contains("datastore")
            }
        }

        return true
    }

    /**
     * Checks whether GORM capability classes are available on the project ClassLoader.
     */
    boolean isGormOnClasspath(ClassLoader loader) {
        if (loader == null) return true
        Boolean cached = GORM_CLASSPATH_CACHE.get(loader)
        if (cached != null) return cached.booleanValue()

        boolean available = false
        List<String> markerClasses = [
            'grails.gorm.GormEntity',
            'org.grails.datastore.gorm.GormEntity',
            'grails.gorm.annotation.Entity',
            'grails.persistence.Entity',
            'grails.gorm.GormStaticApi'
        ]

        for (String fqn : markerClasses) {
            try {
                Class.forName(fqn, false, loader)
                available = true
                break
            } catch (Throwable ignored) {
            }
        }

        // Also check if any GORM or Datastore classes are resolvable
        if (!available) {
            try {
                Class.forName('org.grails.datastore.mapping.model.PersistentEntity', false, loader)
                available = true
            } catch (Throwable ignored) {
            }
        }

        GORM_CLASSPATH_CACHE.put(loader, Boolean.valueOf(available))
        return available
    }

    /**
     * Determines if a ClassNode is an admitted GORM domain entity in the current context.
     */
    boolean isDomainClass(ClassNode classNode, RequestContext ctx, String uri = null) {
        if (classNode == null) return false
        if (!isApplicable(ctx, uri)) return false

        String className = classNode.name
        if (className == 'java.lang.Object' || className == 'groovy.lang.GroovyObject') return false

        // Check explicit GrailsUtils domain checks
        if (GrailsUtils.isDomainClass(classNode, uri)) return true

        // Check annotations
        if (classNode.annotations?.any {
            String annName = it.classNode.name
            annName in ['grails.gorm.annotation.Entity', 'grails.persistence.Entity', 'jakarta.persistence.Entity', 'javax.persistence.Entity']
        }) {
            return true
        }

        // Check interfaces
        if (classNode.interfaces?.any {
            String iface = it.name
            iface in ['grails.gorm.Entity', 'org.grails.datastore.gorm.GormEntity', 'grails.artefact.DomainClass']
        }) {
            return true
        }

        // Check static fields commonly used in GORM entities
        if (classNode.getField('constraints') != null || classNode.getField('mapping') != null ||
            classNode.getField('hasMany') != null || classNode.getField('belongsTo') != null) {
            return true
        }

        // Check naming convention in domain directory or if ending with Domain
        if (classNode.nameWithoutPackage.endsWith('Domain')) return true
        if (uri != null && uri.replace('\\', '/').contains('/grails-app/domain/')) return true

        return false
    }

    /**
     * Derives GORM static completions (get, list, count, dynamic finders) for a domain class.
     */
    List<CompletionItem> getStaticCompletions(ClassNode domainClass, RequestContext ctx) {
        if (!isDomainClass(domainClass, ctx, ctx?.uri())) return []

        List<CompletionItem> items = []
        Set<String> seen = new HashSet<>()

        // 1. Static query and persistence methods
        for (String m : GORM_STATIC_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "GORM static method"
            items.add(item)
            seen.add(m)
        }

        // 2. Generic dynamic finder prefixes
        for (String prefix : ['findBy', 'findAllBy', 'countBy']) {
            if (!seen.contains(prefix)) {
                CompletionItem item = new CompletionItem(prefix)
                item.kind = CompletionItemKind.Method
                item.detail = "GORM dynamic finder"
                items.add(item)
                seen.add(prefix)
            }
        }

        // 3. Property-derived dynamic finders
        List<PropertyNode> eligibleProperties = getEligibleDomainProperties(domainClass)
        for (PropertyNode prop : eligibleProperties) {
            String cap = prop.name.capitalize()
            for (String prefix : ['findBy', 'findAllBy', 'countBy']) {
                String finderName = prefix + cap
                if (!seen.contains(finderName)) {
                    CompletionItem item = new CompletionItem(finderName)
                    item.kind = CompletionItemKind.Method
                    item.detail = "GORM dynamic finder"
                    items.add(item)
                    seen.add(finderName)
                }
            }
        }

        // 4. Multi-property dynamic finders (Boolean combinations for first few properties)
        if (eligibleProperties.size() >= 2) {
            int limit = Math.min(eligibleProperties.size(), 5)
            for (int i = 0; i < limit; i++) {
                for (int j = 0; j < limit; j++) {
                    if (i != j) {
                        String cap1 = eligibleProperties[i].name.capitalize()
                        String cap2 = eligibleProperties[j].name.capitalize()
                        for (String op : ['And', 'Or']) {
                            for (String prefix : ['findBy', 'findAllBy', 'countBy']) {
                                String finderName = "${prefix}${cap1}${op}${cap2}".toString()
                                if (!seen.contains(finderName)) {
                                    CompletionItem item = new CompletionItem(finderName)
                                    item.kind = CompletionItemKind.Method
                                    item.detail = "GORM dynamic finder"
                                    items.add(item)
                                    seen.add(finderName)
                                }
                            }
                        }
                    }
                }
            }
        }

        return items
    }

    /**
     * Derives GORM instance completions (save, delete, refresh, validate, id, version, errors) for a domain instance.
     */
    List<CompletionItem> getInstanceCompletions(ClassNode domainClass, RequestContext ctx) {
        if (!isDomainClass(domainClass, ctx, ctx?.uri())) return []

        List<CompletionItem> items = []
        Set<String> seen = new HashSet<>()

        // 1. Instance methods
        for (String m : GORM_INSTANCE_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "GORM instance method"
            items.add(item)
            seen.add(m)
        }

        // 2. Injected properties
        List<String> injectedProps = ['id', 'version', 'errors']
        for (String prop : injectedProps) {
            if (!seen.contains(prop)) {
                CompletionItem item = new CompletionItem(prop)
                item.kind = CompletionItemKind.Property
                item.detail = "GORM property"
                items.add(item)
                seen.add(prop)
            }
        }

        // 3. Declared domain properties
        for (PropertyNode prop : getEligibleDomainProperties(domainClass)) {
            if (!seen.contains(prop.name)) {
                CompletionItem item = new CompletionItem(prop.name)
                item.kind = CompletionItemKind.Property
                item.detail = "Domain property"
                items.add(item)
                seen.add(prop.name)
            }
        }

        return items
    }

    /**
     * Provides Criteria Builder restrictions (eq, like, between, order, projections, etc.).
     */
    List<CompletionItem> getCriteriaCompletions() {
        List<CompletionItem> items = []
        for (String m : CRITERIA_METHODS) {
            CompletionItem item = new CompletionItem(m)
            item.kind = CompletionItemKind.Method
            item.detail = "Criteria restriction"
            items.add(item)
        }
        return items
    }

    /**
     * Derives typed ResolvedDeclaration items with origin CAPABILITY_ADAPTER for discovery service.
     */
    List<ResolvedDeclaration> resolveDeclarations(ClassNode domainClass, RequestContext ctx, boolean isStatic) {
        if (!isDomainClass(domainClass, ctx, ctx?.uri())) return []

        List<ResolvedDeclaration> declarations = []
        String sourceSetName = ctx.sourceSet()?.sourceSetName ?: "main"
        String projectCoords = ctx.grailsProject()?.name ?: "project"
        String declaringClass = domainClass.name

        if (isStatic) {
            for (String m : GORM_STATIC_METHODS) {
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    "org.grails:grails-datastore-gorm",
                    declaringClass,
                    "${m}()",
                    "CAPABILITY_ADAPTER",
                    null
                )
                declarations.add(new ResolvedDeclaration(
                    m,
                    "METHOD",
                    "Object",
                    [],
                    [],
                    true,
                    prov
                ))
            }
            for (PropertyNode prop : getEligibleDomainProperties(domainClass)) {
                String cap = prop.name.capitalize()
                for (String prefix : ['findBy', 'findAllBy', 'countBy']) {
                    String finder = prefix + cap
                    String returnType = prefix == 'countBy' ? 'Number' : (prefix == 'findAllBy' ? 'List' : domainClass.nameWithoutPackage)
                    DeclarationProvenance prov = new DeclarationProvenance(
                        sourceSetName,
                        "org.grails:grails-datastore-gorm",
                        declaringClass,
                        "${finder}(${prop.type.nameWithoutPackage})",
                        "CAPABILITY_ADAPTER",
                        null
                    )
                    declarations.add(new ResolvedDeclaration(
                        finder,
                        "METHOD",
                        returnType,
                        [prop.type.nameWithoutPackage],
                        [],
                        true,
                        prov
                    ))
                }
            }
        } else {
            for (String m : GORM_INSTANCE_METHODS) {
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    "org.grails:grails-datastore-gorm",
                    declaringClass,
                    "${m}()",
                    "CAPABILITY_ADAPTER",
                    null
                )
                declarations.add(new ResolvedDeclaration(
                    m,
                    "METHOD",
                    m == 'save' ? domainClass.nameWithoutPackage : 'Object',
                    [],
                    [],
                    false,
                    prov
                ))
            }
            for (String prop : ['id', 'version', 'errors']) {
                DeclarationProvenance prov = new DeclarationProvenance(
                    sourceSetName,
                    "org.grails:grails-datastore-gorm",
                    declaringClass,
                    prop,
                    "CAPABILITY_ADAPTER",
                    null
                )
                declarations.add(new ResolvedDeclaration(
                    prop,
                    "PROPERTY",
                    prop == 'errors' ? 'Errors' : 'Long',
                    [],
                    [],
                    false,
                    prov
                ))
            }
        }

        return declarations
    }

    /**
     * Filters out compiler-synthetic, class, metaClass, and static properties.
     */
    List<PropertyNode> getEligibleDomainProperties(ClassNode domainClass) {
        if (domainClass == null) return []
        List<PropertyNode> eligible = []
        Set<String> seen = new HashSet<>()

        for (PropertyNode prop : domainClass.properties) {
            String name = prop.name
            if (name == 'class' || name == 'metaClass' || name == 'constraints' || name == 'mapping' ||
                name == 'hasMany' || name == 'belongsTo' || name == 'hasOne' || name == 'transients' ||
                prop.isStatic() || seen.contains(name)) {
                continue
            }
            seen.add(name)
            eligible.add(prop)
        }

        // Also check declared fields with getters/setters or standard properties
        for (FieldNode field : domainClass.fields) {
            String name = field.name
            if (name.startsWith('$') || name == 'class' || name == 'metaClass' || field.isStatic() || seen.contains(name)) {
                continue
            }
            seen.add(name)
            eligible.add(new PropertyNode(field, field.modifiers, null, null))
        }

        return eligible
    }
}
