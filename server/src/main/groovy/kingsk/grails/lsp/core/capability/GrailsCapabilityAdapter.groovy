package kingsk.grails.lsp.core.capability

import groovy.transform.CompileStatic
import kingsk.grails.lsp.context.RequestContext

/**
 * Interface for Grails capability adapters.
 *
 * Each admitted adapter documents:
 * - capabilityName: Name of the capability
 * - detectionEvidence: Artifact, type, annotation, or descriptor that enables the rule
 * - inputsDescription: AST and model facts consumed by the adapter
 * - derivationDescription: How output is expanded from domain/project entities
 * - applicabilityDescription: Project, source-set, and classpath scope
 * - invalidationDescription: Source, config, or dependency changes invalidating facts
 * - limitsDescription: Explicit boundaries (no runtime execution, static analysis only)
 */
@CompileStatic
interface GrailsCapabilityAdapter {

    String getCapabilityName()

    String getDetectionEvidence()

    String getInputsDescription()

    String getDerivationDescription()

    String getApplicabilityDescription()

    String getInvalidationDescription()

    String getLimitsDescription()

    boolean isApplicable(RequestContext ctx, String uri)
}

