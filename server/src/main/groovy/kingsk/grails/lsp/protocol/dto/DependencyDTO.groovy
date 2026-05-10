package kingsk.grails.lsp.protocol.dto

import groovy.transform.CompileStatic

@CompileStatic
class DependencyDTO implements Serializable {
    String name
    String group
    String version
    String scope
}
