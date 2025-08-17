# Grails Framework Support

[![Visual Studio Marketplace Version](https://img.shields.io/visual-studio-marketplace/v/KingSK1998.vscode-grails-extension)](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-grails-extension)
[![Visual Studio Marketplace Downloads](https://img.shields.io/visual-studio-marketplace/d/KingSK1998.vscode-grails-extension)](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-grails-extension)
[![GitHub](https://img.shields.io/github/license/KingSK1998/vscode-grails-extension)](https://github.com/KingSK1998/vscode-grails-extension/blob/main/LICENSE)

A comprehensive VS Code extension that provides full-featured support for **Grails** and **Groovy** development. This extension integrates seamlessly with the VS Code Gradle extension and offers language server capabilities, project exploration, code snippets, and specialized development tools for Grails applications.

## ✨ Features

### 🚀 Core Functionality
- **Language Server Integration**: Advanced Groovy and GSP language support with IntelliSense
- **Project Explorer**: Dedicated Grails project tree view with Controllers, Services, and Domains
- **Command Palette Integration**: Quick access to Grails commands (run, test, clean, compile)
- **Artifact Creation Wizard**: Create Controllers, Services, Domains, TagLibs, and more
- **Workspace Setup**: Automatic configuration for optimal Grails development

### 📝 Language Support
- **Groovy Syntax Highlighting**: Full syntax support with proper tokenization
- **GSP (Groovy Server Pages)**: Complete GSP syntax highlighting and IntelliSense
- **Code Snippets**: Rich snippet library for common Grails patterns
- **Auto-completion**: Context-aware code completion for Grails artifacts
- **Bracket Matching**: Smart bracket pairing for Groovy and GSP syntax

### 🛠️ Development Tools
- **Gradle Integration**: Seamless integration with VS Code Gradle extension
- **Terminal Integration**: Execute Grails commands directly in VS Code terminal
- **Error Handling**: Comprehensive error reporting with actionable suggestions
- **Status Bar**: Real-time extension status and progress indicators

## 📋 Requirements

### Prerequisites
- **VS Code**: Version 1.93.0 or higher
- **Java**: JDK 11 or higher (for Language Server)
- **Grails**: Grails 4.0+ recommended
- **Gradle**: For project build management

### Required Extensions
- [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) - Automatically installed as dependency

### Recommended Extensions
- [Prettier - Code formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)
- [ESLint](https://marketplace.visualstudio.com/items?itemName=dbaeumer.vscode-eslint)
- [HTML CSS Support](https://marketplace.visualstudio.com/items?itemName=ecmel.vscode-html-css)

## ⚙️ Extension Settings

This extension contributes the following settings:

### Grails Configuration
- `grails.path`: Path to Grails installation directory
- `grails.javaHome`: Java installation path for Language Server
- `grails.server.port`: Development server port (default: 5007)
- `grails.server.host`: Development server host (default: localhost)
- `grails.server.jvmArgs`: JVM arguments for Grails server
- `grails.server.developmentMode`: Enable development mode (default: true)

### Language Server Settings
- `grailsLsp.completionDetail`: Completion detail level (BASIC/STANDARD/ADVANCED)
- `grailsLsp.maxCompletionItems`: Maximum completion items (default: 100)
- `grailsLsp.includeSnippets`: Include code snippets in completion
- `grailsLsp.enableGrailsMagic`: Enable Grails-specific features
- `grailsLsp.codeLensMode`: CodeLens configuration (BASIC/ADVANCED)
- `grailsLsp.compilerPhase`: Groovy compiler phase (default: 2)
- `grailsLsp.shouldRecompileOnChange`: Auto-recompile on changes

## 🚀 Getting Started

1. **Install the Extension**: Search for "Grails Framework Support" in VS Code Extensions
2. **Open a Grails Project**: Open a folder containing a `build.gradle` file
3. **Configure Settings**: Set `grails.path` and `grails.javaHome` if needed
4. **Start Developing**: Use Command Palette (`Ctrl+Shift+P`) and search for "Grails" commands

## 📝 Available Commands

- `Grails: Run Application` - Start the Grails application
- `Grails: Run Tests` - Execute project tests
- `Grails: Clean` - Clean the project
- `Grails: Compile` - Compile the project
- `Grails: Create New Artifact` - Launch artifact creation wizard
- `Grails: Setup Workspace` - Configure workspace for Grails development
- `Grails: Restart Language Server` - Restart the language server

## 🐛 Known Issues

- Language server may take a few moments to initialize on first startup
- Large projects might experience slower completion response times
- GSP syntax highlighting may not work perfectly with complex nested expressions

Please report issues on our [GitHub Issues page](https://github.com/KingSK1998/vscode-grails-extension/issues).

## 📖 Documentation

- [Extension Documentation](https://github.com/KingSK1998/vscode-grails-extension#readme)
- [Grails Framework](https://grails.org/)
- [Groovy Language](https://groovy-lang.org/)

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

## 📄 License

This extension is licensed under the [MIT License](LICENSE).

## 🙏 Acknowledgments

- [Grails Framework](https://grails.org/) team for the amazing framework
- [Groovy](https://groovy-lang.org/) community for language support
- VS Code team for the excellent extension API

---

**Happy Grails Development!** 🎉
