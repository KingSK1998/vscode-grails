package kingsk.grails.lsp.index

import groovy.transform.CompileStatic
import java.util.concurrent.atomic.AtomicReference

@CompileStatic
class ProjectIndex {
    private final String projectRoot
    private final AtomicReference<IndexSnapshot> currentSnapshot
    
    ProjectIndex(String projectRoot) {
        this.projectRoot = projectRoot
        this.currentSnapshot = new AtomicReference<>(IndexSnapshot.EMPTY)
    }
    
    String getProjectRoot() { 
        return projectRoot 
    }
    
    IndexSnapshot getSnapshot() { 
        return currentSnapshot.get() 
    }
    
    void commit(IndexSnapshot snapshot) { 
        currentSnapshot.set(snapshot) 
    }
}
