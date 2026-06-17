package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.context.RequestContext
import kingsk.grails.lsp.model.types.TextFile
import kingsk.grails.lsp.utils.ast.ASTUtils
import kingsk.grails.lsp.utils.ast.GrailsASTHelper
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.stmt.BlockStatement

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

@Slf4j
@CompileStatic
class GrailsGormSqlProvider extends BaseProvider {

    GrailsGormSqlProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.services.WorkspaceManager workspaceManager) {
        super(providerContext, workspaceManager)
    }

    CompletableFuture<String> provideGormSqlPreview(String uri) {
        long startTime = System.currentTimeMillis()

        return CompletableFuture.supplyAsync({ ->
            try {
                def ctx = createRequestContext(uri)
                String normalizedUri = TextFile.normalizePath(uri)
                
                def nodes = ctx.ast().getNodes(normalizedUri)
                if (!nodes) return ""
                
                def classNode = nodes.find { it instanceof ClassNode } as ClassNode
                if (!classNode || !GrailsUtils.isDomainClass(classNode)) {
                    return "-- Not a Grails Domain Class"
                }

                def tableName = discoverTableName(classNode)
                StringBuilder sb = new StringBuilder()
                sb.append("/* GORM SQL Preview for ").append(classNode.nameWithoutPackage).append(" */\n")
                sb.append("CREATE TABLE ").append(tableName).append(" (\n")

                def properties = GrailsUtils.getDomainProperties(classNode)
                List<String> columns = []
                columns.add("    id BIGINT PRIMARY KEY,")
                columns.add("    version BIGINT NOT NULL,")

                properties.each { FieldNode field ->
                    if (field.name != 'id' && field.name != 'version') {
                        def colName = discoverColumnName(classNode, field.name)
                        def colType = mapToSqlType(field.type.name)
                        columns.add("    ${colName} ${colType},".toString())
                    }
                }

                sb.append(columns.join("\n"))
                sb.append("\n);")

                return sb.toString()
            } finally {
                recordHealth("gormSql", System.currentTimeMillis() - startTime, true)
            }
        } as Supplier<String>)
    }

    private String discoverTableName(ClassNode node) {
        node.nameWithoutPackage.toLowerCase()
    }

    private String discoverColumnName(ClassNode node, String fieldName) {
        fieldName.toLowerCase()
    }

    private String mapToSqlType(String javaType) {
        switch (javaType) {
            case 'java.lang.String': return 'VARCHAR(255)'
            case 'java.lang.Integer':
            case 'int': return 'INT'
            case 'java.lang.Long':
            case 'long': return 'BIGINT'
            case 'java.lang.Boolean':
            case 'boolean': return 'BOOLEAN'
            case 'java.util.Date': return 'TIMESTAMP'
            default: return 'VARCHAR(255)'
        }
    }
}
