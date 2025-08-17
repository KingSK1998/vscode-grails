package kingsk.grails.lsp.model

import org.codehaus.groovy.ast.ClassNode

class FullyQualifiedType {
	String packageName
	String className
	
	FullyQualifiedType() {}
	
	FullyQualifiedType(ClassNode classNode) {
		packageName = classNode.packageName
		className = classNode.nameWithoutPackage
	}
	
	String getFqn() { "$packageName.$className" }
	
	static FullyQualifiedType from(ClassNode classNode) {
		return new FullyQualifiedType(classNode)
	}
}
