# Testing Guide

## Overview

Comprehensive testing infrastructure for the Grails Language Server using Spock Framework with specialized base classes
for different LSP features.

## Quick Reference

### Base Test Classes

- `BaseLspSpec`: The base class for all LSP tests, providing common setup and utilities.
- `CompletionTestSpec`: For testing completion functionality.
- `DiagnosticsTestSpec`: For testing diagnostics functionality.
- `DefinitionTestSpec`: For testing definition functionality.
- `SignatureHelpTestSpec`: For testing signature help functionality.
- `TypeDefinitionTestSpec`: For testing type definition functionality.

### Project Types

- `ProjectType.DUMMY`: Fast unit tests with minimal setup (default)
- `ProjectType.GROOVY`: Integration tests with full Groovy project structure
- `ProjectType.GRAILS`: Full integration tests with Grails project structure

## Writing Tests

### Basic Test Structure

```groovy
class MyFeatureSpec extends CompletionTestSpec {
    
    def "should provide specific functionality"() {
        given: "A clear description of the setup"
        initializeProject(ProjectType.DUMMY) // or GROOVY/GRAILS if needed
        String content = '''
            class MyClass {
                def myMethod() {
                    // test content
                }
            }
        '''
        
        when: "A clear description of the action"
        List<CompletionItem> items = getCompletionItems(content, 3, 10)
        
        then: "A clear description of the expected result"
        assertContainsItem(items, "expectedItem")
        assertNotContainsItem(items, "unexpectedItem")
    }
}
```

### Completion Testing

```groovy
// Get completion items at a specific position
List<CompletionItem> items = getCompletionItems(content, line, character)

// Assert that items contain specific completions
assertContainsItem(items, "expectedItem")
assertContainsItems(items, ["item1", "item2", "item3"])
assertNotContainsItem(items, "unexpectedItem")

// Assert completion item properties
assertItemHasKind(items, "methodName", CompletionItemKind.Method)
assertItemHasDetail(items, "fieldName", "Expected detail")
```

### Diagnostics Testing

```groovy
// Get diagnostics for a document
List<Diagnostic> diagnostics = getDiagnostics(content)

// Assert diagnostic presence and properties
assertContainsDiagnostic(diagnostics, "error message", DiagnosticSeverity.Error)
assertNoDiagnostics(diagnostics)
assertDiagnosticCount(diagnostics, 2)

// Assert diagnostic location
assertDiagnosticAtLine(diagnostics, 5, "expected message")
```

### Definition Testing

```groovy
// Get definition locations for a position
List<Location> locations = getDefinitionLocations(uri, line, character)

// Assert that locations contain a specific location
assertContainsLocation(locations, expectedUri, startLine, startChar, endLine, endChar)

// Assert that locations do not contain a specific location
assertNotContainsLocation(locations, unexpectedUri)
```

### Signature Help Testing

```groovy
// Get signature help at a position
SignatureHelp signatureHelp = getSignatureHelp(uri, line, character)

// Assert that signature help contains a specific signature
assertContainsSignature(signatureHelp, "expectedSignature")

// Assert active signature and parameter
assertActiveSignatureAndParameter(signatureHelp, 0, 1)
```

### Type Definition Testing

```groovy
// Get type definition locations for a position
List<Location> locations = getTypeDefinitionLocations(uri, line, character)

// Assert single type definition location
Location location = assertSingleTypeDefinitionLocation(locations)

// Assert specific type definition location
assertContainsTypeDefinitionLocation(locations, uri, startLine, startChar, endLine, endChar)
```

## Running Tests

### Gradle Commands

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "kingsk.grails.lsp.providersDocument.GrailsCompletionProviderSpec"

# Run specific test method
./gradlew test --tests "kingsk.grails.lsp.providersDocument.GrailsCompletionProviderSpec.should provide controller action completions"

# Run tests with coverage
./gradlew jacocoTestReport

# Run all checks (tests + coverage verification)
./gradlew checkAll
```

### Test Reports

Test reports are generated in:

- `build/reports/tests/test/index.html` - Test execution report
- `build/reports/jacoco/test/html/index.html` - Code coverage report

## Test Infrastructure

### Mock Language Client

```groovy
class MockLanguageClient implements LanguageClient {
    List<PublishDiagnosticsParams> diagnostics = []
    List<MessageParams> messages = []
    List<ProgressParams> progressUpdates = []
    
    // Methods to capture and verify LSP interactions
    List<Diagnostic> getDiagnosticsForUri(String uri)
    void clear()
}
```

### Dynamic Workspaces

The testing infrastructure supports dynamic creation of test project structures:

```groovy
// Create a minimal test workspace
initializeProject(ProjectType.DUMMY)

// Create a full Groovy project structure
initializeProject(ProjectType.GROOVY)

// Create a full Grails project structure
initializeProject(ProjectType.GRAILS)
```

### Test Utilities

Common utilities available in all test base classes:

```groovy
// Document operations
String createDocument(String content)
void updateDocument(String uri, String content)
void closeDocument(String uri)

// Position helpers
Position createPosition(int line, int character)
Range createRange(int startLine, int startChar, int endLine, int endChar)

// Assertion helpers
void assertContainsItem(List<CompletionItem> items, String label)
void assertContainsDiagnostic(List<Diagnostic> diagnostics, String message, DiagnosticSeverity severity)
void assertContainsLocation(List<Location> locations, String uri, int startLine, int startChar, int endLine, int endChar)
```

## Best Practices

### Test Organization

1. **Use appropriate base class**: Choose the most specific base class for your test type
2. **Minimal setup**: Use `ProjectType.DUMMY` unless you specifically need full project structure
3. **Clear test names**: Use descriptive test method names that explain what is being tested
4. **Given/When/Then**: Follow Spock's natural language structure

### Performance

1. **Fast unit tests**: Prefer `ProjectType.DUMMY` for most tests
2. **Selective integration**: Only use `ProjectType.GROOVY` or `ProjectType.GRAILS` when necessary
3. **Cleanup**: Tests automatically clean up resources, but avoid creating unnecessary files

### Assertions

1. **Use helper methods**: Leverage provided assertion helpers for consistent error messages
2. **Specific assertions**: Be specific about what you're testing
3. **Negative tests**: Include tests for what should NOT happen

## Test Coverage

The project maintains comprehensive test coverage with:

- **Unit Tests**: Fast, isolated tests for individual components
- **Integration Tests**: Tests that verify component interactions
- **End-to-End Tests**: Full LSP feature tests with real project structures

All tests follow Spock Framework conventions and use the new base test classes for consistent and maintainable testing.