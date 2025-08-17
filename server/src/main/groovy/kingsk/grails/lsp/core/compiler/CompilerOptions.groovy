package kingsk.grails.lsp.core.compiler

import groovy.transform.ToString

/**
 * Configuration options for the Groovy/Grails compiler.
 * <p>
 * @see CompilerOptions#TARGET_DIRECTORY TARGET_DIRECTORY
 * @see CompilerOptions#SOURCE_ENCODING SOURCE_ENCODING
 * @see CompilerOptions#VERBOSE VERBOSE
 * @see CompilerOptions#DEBUG DEBUG
 * @see CompilerOptions#TOLERANCE TOLERANCE
 * @see CompilerOptions#WARNING_LEVEL WARNING_LEVEL
 */
@ToString
class CompilerOptions {
	/** Directory into which to write classes. Defaults to "build/classes" */
	String TARGET_DIRECTORY = "build/classes"
	
	/** Encoding for source files. Defaults to UTF-8 */
	String SOURCE_ENCODING = "UTF-8"
	
	/** If true, the compiler should produce action information. Defaults to true */
	boolean VERBOSE = true
	
	/** If true, debugging code should be activated. Defaults to true */
	boolean DEBUG = true
	
	/** The number of non-fatal errors to allow before bailing. Defaults to 0 */
	int TOLERANCE = 0
	
	/** The bytecode version target. */
	String TARGET_BYTECODE = "17"
	
	/** The default warning level. */
	int WARNING_LEVEL = 1
	
	/**
	 * Validates the compiler options
	 * @throws IllegalArgumentException if any option is invalid
	 */
	void validate() {
		if (!TARGET_DIRECTORY) {
			throw new IllegalArgumentException("TARGET_DIRECTORY cannot be null or empty")
		}
		if (!SOURCE_ENCODING) {
			throw new IllegalArgumentException("SOURCE_ENCODING cannot be null or empty")
		}
		if (TOLERANCE < 0) {
			throw new IllegalArgumentException("TOLERANCE cannot be negative")
		}
		if (WARNING_LEVEL < 0) {
			throw new IllegalArgumentException("WARNING_LEVEL cannot be negative")
		}
	}
	
	static final CompilerOptions DEFAULT = new CompilerOptions()
}