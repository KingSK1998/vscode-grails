package kingsk.grails.lsp.protocol.dto

class ProjectPatchDTO implements Serializable {
    public String projectId

    /**
     * changed fields only
     * key = field name
     * value = new value
     */
    public Map<String, Object> changes
}