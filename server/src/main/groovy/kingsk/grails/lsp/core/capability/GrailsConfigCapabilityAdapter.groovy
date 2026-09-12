package kingsk.grails.lsp.core.capability

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import java.util.Collections

/**
 * Capability adapter for Grails application configuration.
 *
 * Implements:
 * 1. Detection: grailsApplication.config access in Grails projects.
 * 2. Inputs: Configuration keys.
 * 3. Derivations: Standard Grails configuration properties (dataSource, grails, server, etc.).
 * 4. Applicability: Grails projects.
 * 5. Invalidation: Config file updates.
 * 6. Limits: Static standard keys without live runtime config evaluation.
 */
@Slf4j
@CompileStatic
class GrailsConfigCapabilityAdapter implements GrailsCapabilityAdapter {

    public static final GrailsConfigCapabilityAdapter INSTANCE = new GrailsConfigCapabilityAdapter()

    private static final List<String> CONFIG_PROPERTIES = Collections.unmodifiableList([
        'dataSource', 'grails', 'server', 'spring', 'hibernate',
        'mail', 'security', 'environments', 'info', 'endpoints'
    ])

    @Override
    String getCapabilityName() {
        return "GrailsConfig"
    }

    @Override
    String getDetectionEvidence() {
        return "Grails project configuration convention"
    }

    @Override
    String getInputsDescription() {
        return "grailsApplication.config expressions"
    }

    @Override
    String getDerivationDescription() {
        return "Standard Grails configuration namespaces (dataSource, grails, server, spring, etc.)"
    }

    @Override
    String getApplicabilityDescription() {
        return "Grails projects"
    }

    @Override
    String getInvalidationDescription() {
        return "Evaluated per RequestContext snapshot"
    }

    @Override
    String getLimitsDescription() {
        return "Standard configuration hierarchy without reading encrypted credentials or runtime profiles"
    }

    @Override
    boolean isApplicable(RequestContext ctx, String uri = null) {
        if (ctx == null) return false
        return ctx.grailsProject()?.isGrailsProject ?: false
    }

    /**
     * Gets configuration property completions.
     */
    List<CompletionItem> getConfigCompletions() {
        List<CompletionItem> items = []
        for (String prop : CONFIG_PROPERTIES) {
            CompletionItem item = new CompletionItem(prop)
            item.kind = CompletionItemKind.Property
            item.detail = "Configuration property"
            items.add(item)
        }
        return items
    }
}

