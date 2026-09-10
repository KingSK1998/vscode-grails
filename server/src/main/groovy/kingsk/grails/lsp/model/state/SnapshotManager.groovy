package kingsk.grails.lsp.model.state

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.lang.ref.SoftReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import kingsk.grails.lsp.context.DetachedASTAccessor

/**
 * Manages the versioned snapshot lineage for a project.
 * Enforces the Single-Snapshot Principle, retains history using SoftReferences,
 * and maintains the Last-Known-Good (LKG) snapshot invariant.
 */
@Slf4j
@CompileStatic
class SnapshotManager {
    private final AtomicReference<VersionedSnapshot> activeSnapshot = new AtomicReference<>(null)
    private final AtomicReference<VersionedSnapshot> lkgSnapshot = new AtomicReference<>(null)
    private final List<SoftReference<VersionedSnapshot>> historicalLineage = new CopyOnWriteArrayList<>()
    
    private static final int MAX_HISTORY = 3

    /**
     * Initializes the manager with an initial empty or base snapshot.
     * 
     * @param initial The starting snapshot.
     */
    SnapshotManager(VersionedSnapshot initial) {
        if (initial != null) {
            activeSnapshot.set(initial)
            lkgSnapshot.set(initial)
        }
    }

    /**
     * Commits a new snapshot to the lineage.
     * If the compilation was successful, updates both the active and LKG references.
     * If unsuccessful (compilation/sync error), updates only the active snapshot to preserve LKG.
     *
     * @param snapshot The new snapshot to commit.
     * @param isSuccess True if the compilation/sync succeeded, false otherwise.
     */
    void commit(VersionedSnapshot snapshot, boolean isSuccess) {
        if (snapshot == null) return

        VersionedSnapshot previousActive = activeSnapshot.getAndSet(snapshot)
        if (previousActive != null) {
            // Shift the previous active snapshot to the historical lineage
            historicalLineage.add(0, new SoftReference<>(previousActive))
            trimHistory()
        }

        if (isSuccess) {
            lkgSnapshot.set(snapshot)
            log.debug("[SNAPSHOT] Committed new success snapshot v${snapshot.version}, updated LKG")
        } else {
            log.debug("[SNAPSHOT] Committed compilation failure snapshot v${snapshot.version}, LKG remains v${lkgSnapshot.get()?.version}")
        }
    }

    /**
     * Evicts the historical lineage list to free up heap space during memory pressure.
     */
    void clearHistory() {
        log.info("[SNAPSHOT] Evicting historical snapshot lineage under memory pressure")
        historicalLineage.clear()
    }

    /**
     * Fully releases all snapshot references. Used during project disposal.
     */
    void clear() {
        activeSnapshot.set(null)
        lkgSnapshot.set(null)
        historicalLineage.clear()
    }

    /**
     * Replaces active and LKG snapshots with detached versions where AST is released.
     * Retains safe index and model facts while releasing heavy Groovy AST and classloader memory.
     */
    void detachAst() {
        VersionedSnapshot active = activeSnapshot.get()
        if (active != null && active.ast() != null && !(active.ast() instanceof DetachedASTAccessor)) {
            activeSnapshot.set(new VersionedSnapshot(
                active.version(),
                active.index(),
                active.gradleModel(),
                DetachedASTAccessor.INSTANCE,
                active.fileHashes(),
                active.timestamp()
            ))
            log.debug("[SNAPSHOT] Detached AST for active snapshot v{}", active.version())
        }

        VersionedSnapshot lkg = lkgSnapshot.get()
        if (lkg != null && lkg.ast() != null && !(lkg.ast() instanceof DetachedASTAccessor)) {
            lkgSnapshot.set(new VersionedSnapshot(
                lkg.version(),
                lkg.index(),
                lkg.gradleModel(),
                DetachedASTAccessor.INSTANCE,
                lkg.fileHashes(),
                lkg.timestamp()
            ))
            log.debug("[SNAPSHOT] Detached AST for LKG snapshot v{}", lkg.version())
        }
    }

    /**
     * Returns the active snapshot (latest committed state, which may contain errors).
     *
     * @return The active VersionedSnapshot.
     */
    VersionedSnapshot getActive() {
        activeSnapshot.get()
    }

    /**
     * Returns the Last-Known-Good snapshot (last state that compiled cleanly).
     *
     * @return The LKG VersionedSnapshot.
     */
    VersionedSnapshot getLKG() {
        lkgSnapshot.get()
    }

    /**
     * Retrieves the resolved historical snapshots that have not been garbage collected.
     *
     * @return A list of active historical VersionedSnapshots.
     */
    List<VersionedSnapshot> getHistory() {
        List<VersionedSnapshot> resolved = []
        for (SoftReference<VersionedSnapshot> ref : historicalLineage) {
            VersionedSnapshot snap = ref.get()
            if (snap != null) {
                resolved.add(snap)
            }
        }
        resolved
    }

    private void trimHistory() {
        while (historicalLineage.size() > MAX_HISTORY) {
            historicalLineage.remove(historicalLineage.size() - 1)
        }
    }
}
