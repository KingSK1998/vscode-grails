# Grails Language Server

A comprehensive **Language Server Protocol (LSP)** implementation that provides intelligent code support for Groovy and
Grails projects. Designed to work seamlessly with VS Code and other LSP-compatible editors.

## 🚀 Features

### **Code Intelligence**

- **Smart Code Completion** - Context-aware autocomplete for Grails artifacts, Groovy syntax, and dependencies
- **Real-time Diagnostics** - Syntax errors, compilation issues, and Grails-specific validations
- **Go to Definition** - Navigate to classes, methods, properties across your project
- **Find References** - Locate all usages of symbols throughout your codebase
- **Hover Information** - Rich documentation and type information on hover

### **Grails-Specific Support**

- **Artifact Recognition** - Controllers, Services, Domains, TagLibs, Jobs, and more
- **Convention Awareness** - Understands Grails project structure and conventions
- **Plugin Support** - Works with Grails plugins and extensions
- **Build Integration** - Gradle Tooling API integration for dependency resolution

### **Advanced Features**

- **Inlay Hints** - Type hints for local variables
- **Code Lens** - Test runners and method markers
- **Document Symbols** - AST-based file structure navigation
- **Signature Help** - Method parameter assistance
- **Workspace Symbols** - Project-wide symbol search

## 📋 Requirements

- **Java**: 17.0.8+ LTS
- **Groovy**: 4.0.23+
- **Gradle**: 8.12+
- **VS Code**: Latest version with LSP support

## 🛠️ Installation

### **From Source**

```bash
# Contact the developer for access (single developer project, not open-source yet)
# git clone <repository-url>
# cd grails-language-server

# Build the project
./gradlew build

# Run the language server
./gradlew run
```

### **VS Code Extension**

*In development - VS Code extension for seamless integration (single developer project)*

## 🏗️ Architecture

### **Core Components**

- **GrailsCompiler** - Advanced compilation engine with incremental updates
- **GrailsService** - Central service orchestrating all LSP features
- **Provider System** - Modular providers for each LSP feature
- **AST Visitor** - Intelligent AST analysis and caching
- **Gradle Integration** - Project structure and dependency resolution

### **Package Structure**

```
kingsk.grails.lsp/
├── core/                   # Core compilation and AST processing
│   ├── compiler/           # Groovy/Grails compilation engine
│   ├── gradle/             # Gradle integration and project building
│   └── visitor/            # AST visitors and analysis
├── model/                  # Data models and configuration
├── providersDocument/      # LSP textDocument feature providers
│   └── completions/        # Modular completion strategies
├── providersWorkspace/     # LSP workspace feature providers
├── services/               # Core services and utilities
└── utils/                  # Helper classes and utilities
```

## 🧪 Testing

### **Run Tests**

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew jacocoTestReport

# Run all checks (tests + coverage + verification)
./gradlew checkAll
```

### **Test Infrastructure**

- **Framework**: Spock 2.3 with JUnit Jupiter
- **Base Classes**: Specialized test classes for each LSP feature
- **Coverage**: JaCoCo with 60% minimum threshold
- **Project Types**: Support for DUMMY, GROOVY, and GRAILS test projects

## 📊 Performance

### **Compilation Speed**

- **Full project**: ~2-5 seconds for typical Grails projects
- **Incremental**: ~50-200ms for individual files
- **Smart caching**: Avoids recompilation of unchanged dependencies

### **Memory Efficiency**

- **Intelligent state management**: Minimal memory footprint
- **Garbage collection friendly**: Proper resource cleanup
- **Scalable architecture**: Handles large Grails projects efficiently

## 🔧 Configuration

### **Logging**

Configure logging in `src/main/resources/logback.xml`:

```xml
<!-- Set log level -->
<root level="INFO">
    <appender-ref ref="STDOUT"/>
</root>
```

### **Compiler Options**

Customize compilation behavior through `GrailsLspConfig`:

```groovy
// Default configuration
compilerPhase = Phases.SEMANTIC_ANALYSIS
enableIncrementalCompilation = true
```

## 📚 Documentation

- **[Monorepo LSP reference](../docs/developer-guide.md#language-server-reference)** — `ServerCapabilities` vs disabled features
- **[Testing Guide](docs/TESTING.md)** - Testing infrastructure and best practices
- **[Compiler Architecture](docs/COMPILER.md)** - GrailsCompiler implementation details
- **[Feature Responsibilities](docs/FEATURE_RESPONSIBILITIES.md)** - Feature ownership matrix
- **[Logging Guide](docs/LOGGING_GUIDE.md)** - Logging standards and practices

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guidelines](CONTRIBUTING.md) for details on:

- Code style and conventions
- Testing requirements
- Pull request process
- Development setup

## 📈 Project Status

### **Current Achievements (2024)**

- ✅ Central AST Resolution Architecture - Revolutionary performance improvements
- ✅ Provider Infrastructure Refactoring - 33% average code reduction across providers
- ✅ Enhanced Utility Usage - Better integration of DocumentationHelper, ASTUtils, etc.
- ✅ Unified Completion System - 97% code reduction in completion logic

### **Roadmap**

- 🔄 Multi-root workspace support
- 🔄 Enhanced Grails plugin integration
- 🔄 ML-based completion suggestions
- 🔄 Cross-file analysis optimization
- 🔄 Performance monitoring and metrics

## 📄 License

This project is licensed under the [MIT License](LICENSE).

## 🆘 Support

- **Issues**: Contact the developer directly (single developer project)
- **Discussions**: Not yet available (project not open-source yet)
- **Documentation**: See docs/ folder in project

---

**Built with ❤️ for the Grails community**