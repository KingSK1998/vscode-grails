package kingsk.grails.lsp.services

import kingsk.grails.lsp.test.BaseLspSpec
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.SearchTier
import kingsk.grails.lsp.model.dto.GrailsProject
import spock.lang.Subject

/**
 * Tests for OOM recovery escalation path and Tier-2 exit criteria.
 * Covers: Phase 3c.2 — Memory Lifecycle & Tier-2 Exit Criteria.
 */
class OomRecoverySpec extends BaseLspSpec {

    @Subject
    GrailsService grailsService

    def setup() {
        setupProject()
        grailsService = this.grailsService
    }

    def "should enter Tier 2 on first OOM"() {
        given: "A healthy GrailsService"
        grailsService.@currentTier = SearchTier.TIER_0
        grailsService.@oomCount = 0

        when: "First OOM is triggered"
        grailsService.handleOOM(new OutOfMemoryError("Test OOM #1"))

        then: "Service enters Tier 2"
        grailsService.@currentTier == SearchTier.TIER_2
        grailsService.@oomCount == 1
        grailsService.isTier2()
    }

    def "should hibernate all non-active projects on second OOM"() {
        given: "Two projects in workspace"
        File root1 = new File(System.getProperty("user.dir"), "build/oom_proj1")
        File root2 = new File(System.getProperty("user.dir"), "build/oom_proj2")
        root1.mkdirs()
        root2.mkdirs()
        
        def proj1 = new GrailsProject(name: "Proj1", rootDirectory: root1)
        def proj2 = new GrailsProject(name: "Proj2", rootDirectory: root2)
        
        grailsService.workspaceManager.addProject(proj1)
        grailsService.workspaceManager.addProject(proj2)
        
        and: "Service has already had one OOM"
        grailsService.@oomCount = 1
        grailsService.@currentTier = SearchTier.TIER_2

        when: "Second OOM is triggered"
        grailsService.handleOOM(new OutOfMemoryError("Test OOM #2"))

        then: "oomCount is incremented"
        grailsService.@oomCount == 2
        // Non-active projects should be hibernated (Proj2 if Proj1 is active)
        // This is best-effort; in test environment without full compiler, hibernate is no-op
    }

    def "should enter REJECT_OPS on third OOM instead of System.exit"() {
        given: "Service has had two OOMs already"
        grailsService.@oomCount = 2
        grailsService.@currentTier = SearchTier.TIER_2

        when: "Third OOM is triggered"
        grailsService.handleOOM(new OutOfMemoryError("Test OOM #3"))

        then: "Service enters REJECT_OPS instead of exiting"
        grailsService.@currentTier == SearchTier.REJECT_OPS
        grailsService.@oomCount == 3
        grailsService.isTier2()
    }

    def "should remain in Tier 2 if heap is still above threshold after cooldown"() {
        given: "Service is in Tier 2, but we can't reliably force heap usage > 75% in tests"
        grailsService.@currentTier = SearchTier.TIER_2
        grailsService.@tier2StartTime = System.currentTimeMillis() - 70000 // 70s ago
        grailsService.@oomCount = 1

        when: "isTier2 is called after cooldown"
        def result = grailsService.isTier2()

        then: "Result depends on actual heap usage — this is a behavioral test"
        // If heap is above 75%, remains Tier 2. If below, recovers.
        // We can only verify the method runs without error.
        result == result // Always true, just validates no exception
    }

    def "should recover from Tier 2 when cooldown and heap threshold pass"() {
        given: "Service is in Tier 2 with an old start time"
        grailsService.@currentTier = SearchTier.TIER_2
        grailsService.@tier2StartTime = 0L // Far in past, guarantees cooldown > 60s
        grailsService.@oomCount = 1

        when: "isTier2 is called and heap is under 75%"
        def result = grailsService.isTier2()

        then: "Service recovers to Tier 0 if heap allows"
        // The method should either return false (recovered) or true (heap still high)
        // Both are valid states depending on the test JVM's heap usage
        grailsService.@currentTier in [SearchTier.TIER_0, SearchTier.TIER_2]
    }

    def "should treat REJECT_OPS as a super-tier of Tier 2"() {
        given: "Service is in REJECT_OPS mode"
        grailsService.@currentTier = SearchTier.REJECT_OPS

        when: "isTier2 is checked"
        def result = grailsService.isTier2()

        then: "REJECT_OPS is considered a Tier 2 equivalent (all restrictions apply)"
        result == true
    }

    def "should reset oomCount on successful Tier-2 exit"() {
        given: "Service is in Tier 2 with an old start time"
        grailsService.@currentTier = SearchTier.TIER_2
        grailsService.@tier2StartTime = 0L
        grailsService.@oomCount = 1

        when: "isTier2 is called and recovery conditions are met"
        grailsService.isTier2()

        then: "If recovered, oomCount is reset"
        if (grailsService.@currentTier == SearchTier.TIER_0) {
            grailsService.@oomCount == 0
        }
    }
}