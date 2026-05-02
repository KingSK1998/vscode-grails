package kingsk.grails.lsp.providersDocument

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.utils.GrailsUtils
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
class GrailsGormSqlProvider {
    private final GrailsService service

    GrailsGormSqlProvider(GrailsService service) {
        this.service = service
    }

    String generateSql(String uri) {
        String normalizedUri = kingsk.grails.lsp.model.TextFile.normalizePath(uri)
        Set<ClassNode> classNodes = service.visitor.allClassNodes.get(normalizedUri)
        ClassNode classNode = classNodes ? classNodes.find { true } : null
        
        if (!classNode || !GrailsUtils.isDomainClass(classNode)) {
            return "-- Not a valid Grails Domain class."
        }

        String tableName = discoverTableName(classNode)
        StringBuilder sql = new StringBuilder()
        sql.append("/* GORM SQL Preview for ${classNode.nameWithoutPackage} */\n\n")
        sql.append("CREATE TABLE ${tableName} (\n")

        List<FieldNode> properties = GrailsUtils.getDomainProperties(classNode)
        List<String> columns = []

        // ID column (default GORM behavior)
        columns.add("    id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT")
        columns.add("    version BIGINT NOT NULL")

        properties.each { field ->
            String colName = discoverColumnName(classNode, field.name)
            String colType = mapToSqlType(field.type.name)
            
            columns.add("    ${colName} ${colType}".toString())
        }

        sql.append(columns.join(",\n"))
        sql.append("\n);")

        return sql.toString()
    }

    private String discoverTableName(ClassNode classNode) {
        FieldNode mapping = GrailsUtils.getMappingField(classNode)
        if (mapping?.initialExpression instanceof ClosureExpression) {
            ClosureExpression closure = (ClosureExpression) mapping.initialExpression
            if (closure.code instanceof BlockStatement) {
                BlockStatement block = (BlockStatement) closure.code
                for (statement in block.statements) {
                    if (statement instanceof ExpressionStatement) {
                        ExpressionStatement exprStmt = (ExpressionStatement) statement
                        if (exprStmt.expression instanceof MethodCallExpression) {
                            MethodCallExpression call = (MethodCallExpression) exprStmt.expression
                            if (call.methodAsString == 'table' && call.arguments instanceof NamedArgumentListExpression) {
                                NamedArgumentListExpression args = (NamedArgumentListExpression) call.arguments
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
        return camelToSnake(classNode.nameWithoutPackage)
    }

    private String discoverColumnName(ClassNode classNode, String fieldName) {
        // Simple GORM default mapping
        return camelToSnake(fieldName)
    }

    private String camelToSnake(String str) {
        return str.replaceAll(/([a-z])([A-Z])/, '$1_$2').toLowerCase()
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
            default: return "VARCHAR(255) /* Inferred from ${groovyType} */".toString()
        }
    }
}
