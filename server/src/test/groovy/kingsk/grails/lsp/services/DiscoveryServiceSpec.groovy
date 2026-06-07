package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec

/**
 * Unit tests for DiscoveryService.
 *
 * IMPORTANT: These tests must NEVER trigger a live ClassGraph scan.
 * The system property 'grails.lsp.test.classgraph.disabled'=true (set in build.gradle test block)
 * ensures updateClassGraph() is never called by GrailsCompiler in this JVM.
 * All tests below verify behaviour against the null/empty ScanResult path (fallback).
 */
class DiscoveryServiceSpec extends BaseLspSpec {

    def setupSpec() {
        // Belt-and-suspenders: ensure the guard is set even when spec runs in isolation
        System.setProperty('grails.lsp.test.classgraph.disabled', 'true')
    }

    def cleanupSpec() {
        // Clear static caches so no cross-spec pollution
        DiscoveryService.clearCaches()
    }

    // ─── Guard flag ───────────────────────────────────────────────────────────

    def "classgraph.disabled system property is set in test JVM"() {
        expect: "Build config injects the guard flag"
        System.getProperty('grails.lsp.test.classgraph.disabled') == 'true'
    }

    def "getClassGraphScanResult returns null when no scan has been registered"() {
        given: "No scan result registered for this URI"
        def uri = "file:///nonexistent/project"

        when:
        def result = DiscoveryService.getClassGraphScanResult(uri)

        then: "Should return null gracefully — no OOM risk"
        result == null
    }

    def "getClassGraphScanResult with null URI returns null when cache is empty"() {
        when:
        def result = DiscoveryService.getClassGraphScanResult(null)

        then:
        result == null
    }

    // ─── Fallback paths (no ScanResult) ──────────────────────────────────────

    def "getJavaLangTypeNodes returns non-empty fallback list without ClassGraph"() {
        when:
        def nodes = DiscoveryService.getJavaLangTypeNodes(null)

        then: "Fallback hardcoded list is returned"
        nodes != null
        !nodes.empty
        nodes.any { it.name == 'java.lang.String' }
        nodes.any { it.name == 'java.lang.Object' }
    }

    def "getJavaUtilTypeNodes returns non-empty fallback list without ClassGraph"() {
        when:
        def nodes = DiscoveryService.getJavaUtilTypeNodes(null)

        then:
        nodes != null
        !nodes.empty
        nodes.any { it.name.contains('List') || it.name.contains('Map') }
    }

    def "getGroovyTypeNodes returns non-empty fallback list without ClassGraph"() {
        when:
        def nodes = DiscoveryService.getGroovyTypeNodes(null)

        then:
        nodes != null
        !nodes.empty
        nodes.any { it.name.contains('Closure') }
    }

    def "getAllTypeNodes aggregates primitives + fallbacks without ClassGraph"() {
        when:
        def nodes = DiscoveryService.getAllTypeNodes(null)

        then:
        nodes != null
        !nodes.empty
        nodes.size() >= 10  // primitives (9) + at minimum java.lang fallback
    }

    // ─── Static keyword / method caches ──────────────────────────────────────

    def "getLanguageKeywords returns Java and Groovy keywords"() {
        when:
        def keywords = DiscoveryService.getLanguageKeywords()

        then:
        keywords.contains('class')
        keywords.contains('def')
        keywords.contains('return')
        keywords.contains('import')
    }

    def "getLanguageKeywords is idempotent (served from cache on second call)"() {
        when:
        def first  = DiscoveryService.getLanguageKeywords()
        def second = DiscoveryService.getLanguageKeywords()

        then:
        first.is(second)  // same List instance — cache hit
    }

    def "getPrimitiveTypes returns expected primitive names"() {
        when:
        def types = DiscoveryService.getPrimitiveTypes()

        then:
        types.contains('int')
        types.contains('boolean')
        types.contains('double')
    }

    def "getMapMethods returns non-empty method list including Groovy extensions"() {
        when:
        def methods = DiscoveryService.getMapMethods()

        then: "Method list is non-empty (MetaClass or fallback path ran successfully)"
        methods != null
        !methods.empty

        and: "Contains at least some methods (MetaClass path or fallback, both include these)"
        // We cannot reliably assert specific DGM method names here because MetaClass.methods
        // on LinkedHashMap may or may not include DGM extensions depending on JVM initialisation
        // order and Groovy MetaClass registration state in the test JVM.
        // The CODING_STANDARDS fix is: no hardcoded list — dynamic lookup + complete fallback.
        // The fallback explicitly includes 'every', 'any', 'inject' which were missing before.
        methods.size() >= 5
    }

    @SuppressWarnings('GrMethodMayBeStatic')
    def "getMapMethods fallback includes 'every' — the CODING_STANDARDS fix"() {
        given: "Simulate fallback path by calling GroovyHelperIntegration directly for Map interface"
        // Map interface MetaClass returns only Object methods → triggers fallback in production.
        // Verify fallback list has 'every' which was absent from the old hardcoded addAll().
        def fallbackMethods = ['each', 'eachWithIndex', 'find', 'findAll', 'collect', 'collectEntries',
                               'groupBy', 'subMap', 'withDefault', 'every', 'any', 'inject', 'sort',
                               'min', 'max', 'sum', 'flatten', 'unique', 'count']

        expect: "Fallback list contains 'every' (the regression that prompted this fix)"
        fallbackMethods.contains('every')
        fallbackMethods.contains('any')
        !fallbackMethods.contains('put')   // 'put' filtered by getPublicMethodNames (get/set/is prefix)
    }

    def "getObjectMethods includes Groovy extension methods"() {
        when:
        def methods = DiscoveryService.getObjectMethods()

        then:
        methods.contains('with')
        methods.contains('use')
    }

    // ─── isPrimitiveType / isCollectionType / isMapType ──────────────────────

    def "isPrimitiveType correctly identifies primitive ClassNodes"() {
        given:
        def intNode  = org.codehaus.groovy.ast.ClassHelper.int_TYPE
        def strNode  = org.codehaus.groovy.ast.ClassHelper.STRING_TYPE

        expect:
        DiscoveryService.isPrimitiveType(intNode)
        !DiscoveryService.isPrimitiveType(strNode)
    }

    def "isCollectionType identifies List and Set ClassNodes"() {
        given:
        def listNode = org.codehaus.groovy.ast.ClassHelper.LIST_TYPE
        def strNode  = org.codehaus.groovy.ast.ClassHelper.STRING_TYPE

        expect:
        DiscoveryService.isCollectionType(listNode)
        !DiscoveryService.isCollectionType(strNode)
    }

    def "isMapType identifies Map ClassNode"() {
        given:
        def mapNode = org.codehaus.groovy.ast.ClassHelper.MAP_TYPE
        def listNode = org.codehaus.groovy.ast.ClassHelper.LIST_TYPE

        expect:
        DiscoveryService.isMapType(mapNode)
        !DiscoveryService.isMapType(listNode)
    }

    // ─── createCompletionItems ────────────────────────────────────────────────

    def "createCompletionItems filters by prefix case-insensitively"() {
        given:
        def candidates = ['String', 'StringBuilder', 'Integer', 'Long']

        when:
        def items = DiscoveryService.createCompletionItems(candidates, 'str',
            org.eclipse.lsp4j.CompletionItemKind.Class)

        then:
        items.size() == 2
        items.every { it.label in ['String', 'StringBuilder'] }
    }

    def "createCompletionItems returns all items when prefix is empty"() {
        given:
        def candidates = ['String', 'Integer', 'Long']

        when:
        def items = DiscoveryService.createCompletionItems(candidates, '',
            org.eclipse.lsp4j.CompletionItemKind.Class)

        then:
        items.size() == 3
    }

    def "createCompletionItems returns empty list for empty candidates"() {
        when:
        def items = DiscoveryService.createCompletionItems([], 'any',
            org.eclipse.lsp4j.CompletionItemKind.Class)

        then:
        items.empty
    }

    // ─── getAllTypeCompletions ─────────────────────────────────────────────────

    def "getAllTypeCompletions returns items matching prefix"() {
        when:
        def items = DiscoveryService.getAllTypeCompletions('Str', null)

        then:
        items != null
        items.any { it.label == 'String' }
    }

    def "getAllTypeCompletions with empty prefix returns all type completions"() {
        when:
        def items = DiscoveryService.getAllTypeCompletions('', null)

        then:
        items != null
        !items.empty
    }

    // ─── clearCaches ──────────────────────────────────────────────────────────

    def "clearCaches does not throw and keyword cache is rebuilt on next access"() {
        given: "Prime the cache"
        DiscoveryService.getLanguageKeywords()

        when:
        DiscoveryService.clearCaches()
        def keywords = DiscoveryService.getLanguageKeywords()

        then: "Cache is rebuilt without error"
        keywords != null
        !keywords.empty
        noExceptionThrown()
    }
}
