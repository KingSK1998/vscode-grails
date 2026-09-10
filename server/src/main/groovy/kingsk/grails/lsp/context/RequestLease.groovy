package kingsk.grails.lsp.context

import groovy.transform.CompileStatic
import kingsk.grails.lsp.model.state.VersionedSnapshot

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks an active reader's lease on a specific VersionedSnapshot generation.
 * Prevents premature AST detachment or state clearing until all in-flight readers release.
 */
@CompileStatic
class RequestLease implements AutoCloseable {
    private final ProjectContextImpl context
    private final VersionedSnapshot snapshot
    private final AtomicBoolean closed = new AtomicBoolean(false)

    RequestLease(ProjectContextImpl context, VersionedSnapshot snapshot) {
        this.context = context
        this.snapshot = snapshot
    }

    VersionedSnapshot getSnapshot() {
        return snapshot
    }

    @Override
    void close() {
        if (closed.compareAndSet(false, true)) {
            context?.releaseLease(this)
        }
    }

    boolean isClosed() {
        return closed.get()
    }
}
