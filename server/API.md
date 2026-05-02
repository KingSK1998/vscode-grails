# Grails Language Server API Documentation

This document provides comprehensive API documentation for the Grails Language Server, covering public interfaces, core
services, and integration points.

## 🏗️ Core Architecture

### GrailsLanguageServer

The main entry point implementing the Language Server Protocol.

```groovy
class GrailsLanguageServer implements LanguageServer, LanguageClientAware {
	// LSP lifecycle methods
	CompletableFuture<InitializeResult> initialize(InitializeParams params)
	
	CompletableFuture<Object> shutdown()
	
	void exit()
	
	// Service providers
	TextDocumentService getTextDocumentService()
	
	WorkspaceService getWorkspaceService()
}
```

### GrailsService

Central orchestration service managing all LSP features and project state.

```groovy
@CompileStatic
class GrailsService implements LanguageClientAware {
	// Core services
	final GrailsTextDocumentService document
	final GrailsWorkspaceService workspace
	final GradleService gradle
	final GrailsCompiler compiler
	
	// Project management
	void setupWorkspace(String projectDir, boolean asyncCompile = true)
	
	// Document lifecycle
	void onDocumentOpened(TextFile textFile)
	
	void onDocumentChanged(TextFile textFile)
	
	void onDocumentClosed(TextFile textFile)
	
	// Dependency management (live project updates)
	File getJavaDocJarFile(DependencyNode dependency)
	
	File getSourcesJarFile(DependencyNode dependency)
	
	// Project lifecycle management
	void invalidateProjectCache(String projectDir)
	
	// For live project updates
}
```

## 🔧 Core Services

### GrailsCompiler

Advanced compilation engine with incremental updates and smart caching.

```groovy
class GrailsCompiler {
	// Project compilation
	void compileProject()
	
	void compileSourceFile(TextFile textFile)
	
	// State management
	void invalidateCompiler()
	
	void updateCompilerOptions(CompilerOptions option = CompilerOptions.DEFAULT)
	
	void updateClassLoader()
	
	// Compilation control
	boolean compileDefaultOrTillPhase(int phase = grailsService.config.compilerPhase)
	
	// Error handling
	ErrorCollector getErrorCollectorOrNull()
	
	// Source unit access
	SourceUnit getSourceUnit(TextFile textFile)
	
	List<SourceUnit> getSourceUnits()
	
	// Smart patching
	String getPatchedSourceUnitText(TextFile textFile)
	
	TextFile getPatchedSourceUnitTextFile(TextFile file)
}
```

### GradleService

Gradle integration for project structure and dependency resolution.

```groovy
class GradleService {
	// Project management
	GrailsProject getGrailsProject(String projectDir)
	
	// Cache management
	void invalidateCache()
	
	void invalidateProjectCache(String projectDir)
	
	// Dependency artifact downloads
	File downloadJavaDocJarFile(File rootDirectory, DependencyNode dependency)
	
	File downloadSourcesJarFile(File rootDirectory, DependencyNode dependency)
}
```

## 📄 Document Services

### GrailsTextDocumentService

Implements LSP textDocument/* methods.

```groovy
class GrailsTextDocumentService implements TextDocumentService {
	// Completion
	CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params)
	
	// Hover
	CompletableFuture<Hover> hover(HoverParams params)
	
	// Definition
	CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>> definition(DefinitionParams params)
	
	// References
	CompletableFuture<List<? extends Location>> references(ReferenceParams params)
	
	// Document symbols
	CompletableFuture<List<Either<SymbolInformation, DocumentSymbol>>> documentSymbol(DocumentSymbolParams params)
	
	// Diagnostics
	CompletableFuture<List<Diagnostic>> diagnostic(DocumentDiagnosticParams params)
	
	// Additional features
	CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params)
	
	CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params)
	
	CompletableFuture<List<InlayHint>> inlayHint(InlayHintParams params)
}
```

### GrailsWorkspaceService

Implements LSP workspace/* methods.

```groovy
class GrailsWorkspaceService implements WorkspaceService {
	// Workspace symbols
	CompletableFuture<Either<List<? extends SymbolInformation>, List<? extends WorkspaceSymbol>>> symbol(WorkspaceSymbolParams params)
	
	// Configuration changes
	void didChangeConfiguration(DidChangeConfigurationParams params)
	
	void didChangeWatchedFiles(DidChangeWatchedFilesParams params)
}
```

## 🎯 Feature Providers

### Completion System

Modular completion system with strategy pattern.

```groovy
// Base completion strategy
abstract class BaseCompletionStrategy implements CompletionStrategy {
	abstract boolean canHandle(CompletionRequest request)
	
	abstract List<CompletionItem> getCompletions(CompletionRequest request)
}

// Available strategies
class TypeStrategy extends BaseCompletionStrategy {}

class MethodCallStrategy extends BaseCompletionStrategy {}

class PropertyExpressionStrategy extends BaseCompletionStrategy {}

class VariableExpressionStrategy extends BaseCompletionStrategy {}

class KeywordStrategy extends BaseCompletionStrategy {}

class ImportStrategy extends BaseCompletionStrategy {}

class GrailsArtifactStrategy extends BaseCompletionStrategy {}

class ConstructorStrategy extends BaseCompletionStrategy {}
```

### Provider Base Classes

```groovy
// Base provider with common functionality
abstract class BaseProvider {
	protected final GrailsService grailsService
	
	protected final ASTContext getASTContext(TextFile textFile)
}

// Specific providers
class GrailsCompletionProvider extends BaseProvider {}

class GrailsHoverProvider extends BaseProvider {}

class GrailsDefinitionProvider extends BaseProvider {}

class GrailsReferenceProvider extends BaseProvider {}

class GrailsDocumentSymbolProvider extends BaseProvider {}
```

## 📊 Data Models

### Core Models

```groovy
// Project representation
class GrailsProject {
	String name
	String rootDirectory
	String buildFile
	GrailsArtifactType projectType
	List<DependencyNode> dependencies
	List<String> sourceDirectories
	List<String> testDirectories
}

// File representation
class TextFile {
	String uri
	String content
	String languageId
	int version
	FileState state
}

// Dependency representation
class DependencyNode {
	String group
	String name
	String version
	String scope
	File jarFileClasspath
	File javadocFileClasspath
	File sourceJarFileClasspath
}

// Configuration
class GrailsLspConfig {
	int compilerPhase = Phases.SEMANTIC_ANALYSIS
	boolean enableIncrementalCompilation = true
	boolean enableDiagnostics = true
	CodeLensMode codeLensMode = CodeLensMode.ENABLED
}
```

### AST Models

```groovy
// AST context for providers
class ASTContext {
	SourceUnit sourceUnit
	ModuleNode moduleNode
	ClassNode classNode
	MethodNode methodNode
	Position position
	ASTNode nodeAtPosition
}

// Grails artifact types
enum GrailsArtifactType {
	CONTROLLER, SERVICE, DOMAIN, TAGLIB, JOB,
	COMMAND, INTERCEPTOR, FILTER, CODEC,
	GROOVY, UNKNOWN
}
```

## 🛠️ Utility APIs

### AST Utilities

```groovy
class ASTUtils {
	// Node finding
	static ASTNode findNodeAtPosition(ModuleNode moduleNode, Position position)
	
	static ClassNode findClassNode(ModuleNode moduleNode, String className)
	
	static MethodNode findMethodNode(ClassNode classNode, String methodName)
	
	// Type resolution
	static ClassNode resolveType(ASTNode node)
	
	static List<ClassNode> getInterfaces(ClassNode classNode)
	
	static ClassNode getSuperClass(ClassNode classNode)
	
	// Member extraction
	static List<MethodNode> getMethods(ClassNode classNode)
	
	static List<PropertyNode> getProperties(ClassNode classNode)
	
	static List<FieldNode> getFields(ClassNode classNode)
}
```

### Position and Range Utilities

```groovy
class PositionHelper {
	// Position conversion
	static Position offsetToPosition(String text, int offset)
	
	static int positionToOffset(String text, Position position)
	
	// Range operations
	static Range createRange(int startLine, int startChar, int endLine, int endChar)
	
	static boolean isPositionInRange(Position position, Range range)
}

class RangeHelper {
	// Range creation from AST nodes
	static Range createRange(ASTNode node)
	
	static Range createRange(int startLine, int startColumn, int endLine, int endColumn)
	
	// Range validation
	static boolean isValidRange(Range range)
	
	static boolean containsPosition(Range range, Position position)
}
```

### Documentation Utilities

```groovy
class DocumentationHelper {
	// Javadoc extraction
	static String getJavadocContent(ClassNode classNode)
	
	static String getJavadocContent(MethodNode methodNode)
	
	// External documentation
	static String getContentFromJavadocJar(DependencyNode dependency, String className)
	
	static String getGroovydocContent(ASTNode node)
	
	// Markdown conversion
	static String convertToMarkdown(String javadoc)
}
```

### Grails Utilities

```groovy
class GrailsUtils {
	// Artifact detection
	static GrailsArtifactType getArtifactType(String filePath)
	
	static boolean isGrailsArtifact(String filePath)
	
	// Convention helpers
	static String getControllerName(String className)
	
	static String getServiceName(String className)
	
	static String getDomainName(String className)
	
	// Project structure
	static boolean isGrailsProject(String projectDir)
	
	static List<String> getGrailsSourceDirectories(String projectDir)
}
```

## 🔌 Extension Points

### Custom Completion Strategies

```groovy
// Implement custom completion strategy
class MyCustomStrategy extends BaseCompletionStrategy {
	@Override
	boolean canHandle(CompletionRequest request) {
		// Custom logic to determine if this strategy applies
	}
	
	@Override
	List<CompletionItem> getCompletions(CompletionRequest request) {
		// Custom completion logic
	}
}

// Register strategy
CompletionBuilder.registerStrategy(new MyCustomStrategy())
```

### Custom Providers

```groovy
// Extend base provider for custom features
class MyCustomProvider extends BaseProvider {
	MyCustomProvider(GrailsService grailsService) {
		super(grailsService)
	}
	
	// Custom provider methods
	def provideCustomFeature(CustomParams params) {
		ASTContext context = getASTContext(params.textFile)
		// Custom logic using AST context
	}
}
```

## 🧪 Testing APIs

### Base Test Classes

```groovy
// Base test specification
abstract class BaseLspSpec extends Specification {
	// Test infrastructure setup
	protected GrailsService createGrailsService(ProjectType projectType = ProjectType.DUMMY)
	
	protected TextFile createTextFile(String content, String fileName = "Test.groovy")
	
	// Assertion helpers
	protected void assertCompletionContains(List<CompletionItem> completions, String label)
	
	protected void assertDiagnosticContains(List<Diagnostic> diagnostics, String message)
}

// Specialized test classes
abstract class CompletionTestSpec extends BaseLspSpec {}

abstract class DiagnosticsTestSpec extends BaseLspSpec {}

abstract class DefinitionTestSpec extends BaseLspSpec {}
```

### Test Utilities

```groovy
// Project types for testing
enum ProjectType {
	DUMMY, // Fast unit tests
	GROOVY, // Groovy project integration tests
	GRAILS // Full Grails project integration tests
}

// Test helpers
class TestUtils {
	static TextFile createGroovyFile(String content, String fileName = "Test.groovy")
	
	static GrailsProject createTestProject(ProjectType type)
	
	static Position createPosition(int line, int character)
}
```

## 🔧 Configuration

### Compiler Options

```groovy
enum CompilerOptions {
	DEFAULT,
	FAST_COMPILATION,
	FULL_ANALYSIS,
	DEBUG_MODE
}

// Usage
compiler.updateCompilerOptions(CompilerOptions.FULL_ANALYSIS)
```

### LSP Configuration

```groovy
// Client capabilities handling
class ClientCapabilities {
	boolean supportsHover
	boolean supportsCompletion
	boolean supportsDefinition
	boolean supportsReferences
	boolean supportsDiagnostics
}
```

## 📈 Performance APIs

### Caching

```groovy
// AST caching
class ASTCache {
	void cacheSourceUnit(String uri, SourceUnit sourceUnit)
	
	SourceUnit getCachedSourceUnit(String uri)
	
	void invalidateCache(String uri)
}

// Compilation caching
class CompilationCache {
	void cacheCompilationResult(String projectDir, CompilationResult result)
	
	CompilationResult getCachedResult(String projectDir)
}
```

### Progress Reporting

```groovy
class ProgressReportService {
	void sendProgressReport(String message, int percentage)
	
	void startProgress(String title)
	
	void updateProgress(String message, int percentage)
	
	void endProgress()
}
```