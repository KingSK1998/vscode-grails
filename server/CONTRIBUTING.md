# Contributing to Grails Language Server

Thank you for your interest in contributing to the Grails Language Server! This document provides guidelines and
information for contributors.

## 🚀 Getting Started

### Prerequisites

- **Java 17.0.8+** LTS
- **Groovy 4.0.23+**
- **Gradle 8.12+**
- **Git**
- **IDE**: IntelliJ IDEA or VS Code recommended

### Development Setup

1. **Fork and Clone**
   ```bash
   # Contact the developer for access (single developer project, not open-source yet)
   # git clone <repository-url>
   # cd grails-language-server
   ```

2. **Build the Project**
   ```bash
   ./gradlew build
   ```

3. **Run Tests**
   ```bash
   ./gradlew test
   ```

4. **Verify Everything Works**
   ```bash
   ./gradlew checkAll
   ```

## 📋 Development Guidelines

### Code Style

- **Language**: Groovy with `@CompileStatic` where appropriate
- **Formatting**: Follow existing code style in the project
- **Naming**: Use descriptive names for classes, methods, and variables
- **Documentation**: Add Groovydoc for public APIs

### Package Organization

Follow the established package structure:

```
kingsk.grails.lsp/
├── core/                   # Core compilation and AST processing
├── model/                  # Data models and configuration
├── providersDocument/      # LSP textDocument feature providers
├── providersWorkspace/     # LSP workspace feature providers
├── services/               # Core services and utilities
└── utils/                  # Helper classes and utilities
```

### Logging Standards

Use the component-based logging format:

```groovy
@Slf4j
class MyClass {
	void myMethod() {
		log.info("[COMPONENT] Descriptive message with context")
		log.warn("[COMPONENT] Warning message")
		log.error("[COMPONENT] Error message", exception)
	}
}
```

**Component Tags:**

- `[COMPILER]` - Compilation operations
- `[AST]` - AST manipulation
- `[COMPLETION]` - Code completion
- `[HOVER]` - Hover information
- `[DIAGNOSTICS]` - Error/warning diagnostics
- `[DEFINITION]` - Go to definition
- `[WORKSPACE]` - Workspace operations
- `[GRADLE]` - Gradle integration

## 🧪 Testing Guidelines

### Test Structure

Use the specialized base test classes:

- `BaseLspSpec` - Base for all LSP tests
- `CompletionTestSpec` - For completion functionality
- `DiagnosticsTestSpec` - For diagnostics functionality
- `DefinitionTestSpec` - For definition functionality
- `SignatureHelpTestSpec` - For signature help functionality
- `TypeDefinitionTestSpec` - For type definition functionality

### Test Pattern

Follow Spock Framework conventions:

```groovy
class MyFeatureSpec extends CompletionTestSpec {
	def "should provide meaningful test description"() {
		given: "What is provided or auto-initialized"
		// Setup code
		
		when: "When the action is performed"
		// Action code
		
		then: "Expected results are as expected"
		// Assertions
	}
}
```

### Project Types

- `ProjectType.DUMMY` - Fast unit tests (default)
- `ProjectType.GROOVY` - Groovy project integration tests
- `ProjectType.GRAILS` - Full Grails project integration tests

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "MyFeatureSpec"

# Run with coverage
./gradlew jacocoTestReport

# Run all checks
./gradlew checkAll
```

## 🔧 Feature Development

### Adding New LSP Features

1. **Create Provider Class**
   ```groovy
   @Slf4j
   @CompileStatic
   class MyFeatureProvider extends BaseProvider {
       // Implementation
   }
   ```

2. **Add to Service Layer**
    - Update `GrailsTextDocumentService` or `GrailsWorkspaceService`
    - Wire the provider in the service constructor

3. **Create Tests**
    - Extend appropriate base test class
    - Test with different project types
    - Include edge cases and error scenarios

4. **Update Documentation**
    - Add feature to README.md
    - Update FEATURE_RESPONSIBILITIES.md
    - Add usage examples

### Performance Considerations

- **Use Central AST Resolution** - Leverage existing AST compilation
- **Implement Caching** - Cache expensive operations
- **Thread Safety** - Ensure thread-safe operations
- **Memory Management** - Proper resource cleanup

## 📝 Pull Request Process

### Before Submitting

1. **Run All Tests**
   ```bash
   ./gradlew checkAll
   ```

2. **Check Code Coverage**
   ```bash
   ./gradlew jacocoTestCoverageVerification
   ```

3. **Update Documentation**
    - Update relevant documentation files
    - Add changelog entry
    - Update feature matrix if needed

### PR Guidelines

1. **Clear Description**
    - Describe what the PR does
    - Explain why the change is needed
    - Include any breaking changes

2. **Small, Focused Changes**
    - Keep PRs focused on a single feature/fix
    - Split large changes into multiple PRs

3. **Tests Required**
    - All new features must have tests
    - Bug fixes should include regression tests
    - Maintain or improve code coverage

4. **Documentation Updates**
    - Update README.md for new features
    - Update relevant documentation files
    - Add changelog entry

### Review Process

1. **Automated Checks** - All CI checks must pass
2. **Code Review** - At least one maintainer review required
3. **Testing** - Manual testing for complex features
4. **Documentation** - Documentation review for user-facing changes

## 🐛 Bug Reports

### Before Reporting

1. **Search Existing Issues** - Check if already reported
2. **Reproduce the Bug** - Ensure it's reproducible
3. **Gather Information** - Collect relevant details

### Bug Report Template

```markdown
**Describe the Bug**
A clear description of what the bug is.

**To Reproduce**
Steps to reproduce the behavior:

1. Go to '...'
2. Click on '....'
3. See error

**Expected Behavior**
What you expected to happen.

**Environment**

- OS: [e.g. Windows 11, macOS, Linux]
- Java Version: [e.g. 17.0.8]
- Groovy Version: [e.g. 4.0.23]
- Project Type: [e.g. Grails 6.0.0]

**Additional Context**
Add any other context about the problem here.
```

## 💡 Feature Requests

### Feature Request Template

```markdown
**Is your feature request related to a problem?**
A clear description of what the problem is.

**Describe the solution you'd like**
A clear description of what you want to happen.

**Describe alternatives you've considered**
Alternative solutions or features you've considered.

**Additional context**
Add any other context or screenshots about the feature request.
```

## 📚 Resources

### Documentation

- [Testing Guide](docs/TESTING.md)
- [Compiler Architecture](docs/COMPILER.md)
- [Feature Responsibilities](docs/FEATURE_RESPONSIBILITIES.md)
- [Logging Guide](docs/LOGGING_GUIDE.md)

### External Resources

- [LSP4J Documentation](https://github.com/eclipse/lsp4j)
- [Language Server Protocol Specification](https://microsoft.github.io/language-server-protocol/)
- [Groovy Documentation](https://groovy-lang.org/documentation.html)
- [Grails Documentation](https://grails.org/documentation.html)
- [Spock Framework](https://spockframework.org/)

## 🤝 Community

**Note**: This is currently a single developer project and not yet open-source.

- **Contact**: Reach out to the developer directly for questions or collaboration
- **Issues**: Not yet available (project not open-source yet)
- **Discussions**: Not yet available (project not open-source yet)
- **Documentation**: See docs/ folder and root documentation files

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the same license as the
project (MIT License).

---

Thank you for contributing to the Grails Language Server! 🎉