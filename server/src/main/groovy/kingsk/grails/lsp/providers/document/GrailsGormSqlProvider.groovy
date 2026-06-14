package kingsk.grails.lsp.providers.document

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.utils.grails.GrailsUtils
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.FieldNode
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.NamedArgumentListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression

@Slf4j
@CompileStatic
class GrailsGormSqlProvider extends BaseProvider {

    GrailsGormSqlProvider(kingsk.grails.lsp.context.ProviderContext providerContext, kingsk.grails.lsp.context.CompilationContext compilationContext, kingsk.grails.lsp.context.ProjectContext projectContext) {
        super(providerContext, compilationContext, projectContext)
    }

    String generateSql(String uri) {
        def normalizedUri = kingsk.grails.lsp.model.types.TextFile.normalizePath(uri)
        def classNodes = visitor.allClassNodes[normalizedUri]
        def classNode = classNodes?.find { true }

        if (!classNode || !GrailsUtils.isDomainClass(classNode)) {
            return "-- Not a valid Grails Domain class."
        }

        def tableName = discoverTableName(classNode)
        def sql = new StringBuilder()
        sql.append("/* GORM SQL Preview for ${classNode.nameWithoutPackage} */\n\n")
        sql.append("CREATE TABLE ${tableName} (\n")

        def properties = GrailsUtils.getDomainProperties(classNode)
        List<String> columns = []

        // ID column (default GORM behavior)
        columns << "    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT"
        columns << "    version BIGINT NOT NULL"

        properties.each { field ->
            def colName = discoverColumnName(classNode, field.name)
            def colType = mapToSqlType(field.type.name)
            columns << "    ${colName} ${colType}".toString()
        }

        sql.append(columns.join(",\n"))
        sql.append("\n);")

        sql.toString()
    }

    private String discoverTableName(ClassNode classNode) {
        def mapping = GrailsUtils.getMappingField(classNode)
        if (mapping?.initialExpression instanceof ClosureExpression) {
            def closure = (ClosureExpression) mapping.initialExpression
            if (closure.code instanceof BlockStatement) {
                def block = (BlockStatement) closure.code
                for (statement in block.statements) {
                    if (statement instanceof ExpressionStatement) {
                        def exprStmt = (ExpressionStatement) statement
                        if (exprStmt.expression instanceof MethodCallExpression) {
                            def call = (MethodCallExpression) exprStmt.expression
                            if (call.methodAsString == 'table' && call.arguments instanceof NamedArgumentListExpression) {
                                def args = (NamedArgumentListExpression) call.arguments
                                for (entry in args.mapEntryExpressions) {
                                    if (entry.keyExpression.text == 'name') return entry.valueExpression.text.replaceAll(/['"]/, '')
                                }
                            } else if (call.methodAsString == 'table' && call.arguments instanceof ConstantExpression) {
                                return call.arguments.text.replaceAll(/['"]/, '')
                            }
                        }
                    }
                }
            }
        }
        // Default GORM table name strategy (camelCase to snake_case)
        camelToSnake(classNode.nameWithoutPackage)
    }

    private String discoverColumnName(ClassNode classNode, String fieldName) {
        // Simple GORM default mapping
        camelToSnake(fieldName)
    }

    private String camelToSnake(String str) {
        str.replaceAll(/([a-z])([A-Z])/, '$1_$2').toLowerCase()
    }

    private String mapToSqlType(String groovyType) {
        switch (groovyType) {
            case "java.lang.String": return "VARCHAR(255)"
            case "java.lang.Integer":
            case "int": return "INT"
            case "java.lang.Long":
            case "long": return "BIGINT"
            case "java.lang.Boolean":
            case "boolean": return "BOOLEAN"
            case "java.util.Date":
            case "java.time.LocalDate": return "DATE"
            case "java.time.LocalDateTime": return "TIMESTAMP"
            case "java.lang.Double":
            case "double":
            case "java.lang.Float":
            case "float": return "DOUBLE"
            case "java.math.BigDecimal": return "DECIMAL(19,2)"
            default: return "VARCHAR(255) /* Inferred from ${groovyType} */"
        }
    }
}
