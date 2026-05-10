package kingsk.grails.lsp.protocol.dto

import groovy.transform.CompileStatic

@CompileStatic
class ArtifactDTO implements Serializable {

    int controllers
    int services
    int domains
    int views
    int taglibs

    //TODO: Need to extend

}
