# Grails Framework Support for VS Code

[![Visual Studio Marketplace Version](https://img.shields.io/visual-studio-marketplace/v/KingSK1998.vscode-grails)](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-grails)
[![GitHub](https://img.shields.io/github/license/KingSK1998/vscode-grails)](https://github.com/KingSK1998/vscode-grails/blob/main/LICENSE)
[![Build Status](https://github.com/KingSK1998/vscode-grails/workflows/CI/badge.svg)](https://github.com/KingSK1998/vscode-grails/actions)

A comprehensive VS Code extension that provides full-featured support for **Grails** and **Groovy** development with intelligent Language Server Protocol (LSP) integration.

## ✨ Features

### 🚀 Language Server Capabilities

- **Smart Code Completion** - Context-aware autocomplete for Grails artifacts, services, and controllers
- **Real-time Diagnostics** - Syntax errors, compilation issues, and Grails-specific validations
- **Go to Definition** - Navigate to classes, methods, and properties across your entire project
- **Find References** - Locate all symbol usages throughout your codebase
- **Hover Information** - Rich documentation, type information, and method signatures
- **Inlay Hints** - Type hints for local variables and method parameters
- **Code Lens** - Test runners and method execution shortcuts
- **Document Symbols** - AST-based file structure navigation
- **Workspace Symbols** - Project-wide symbol search and navigation

### 📝 VS Code Integration

- **Project Explorer** - Dedicated Grails project tree view with Controllers, Services, and Domains
- **Command Palette** - Quick access to Grails commands (run, test, clean, compile)
- **Artifact Creation** - Interactive wizard for Controllers, Services, Domains, TagLibs, and Jobs
- **Syntax Highlighting** - Full Groovy and GSP (Groovy Server Pages) support
- **Code Snippets** - Rich snippet library for common Grails patterns and boilerplate
- **Terminal Integration** - Execute Grails commands directly in VS Code terminal
- **Status Bar** - Real-time extension status and language server connection indicators

### 🛠️ Development Tools

- **Gradle Integration** - Seamless integration with VS Code Gradle extension
- **Build Automation** - Automatic project compilation and dependency resolution
- **Error Handling** - Comprehensive error reporting with actionable suggestions
- **Hot Reload** - Development mode with automatic reconnection
- **Multi-Project Support** - Handle complex Grails applications with multiple modules

## 📋 Requirements

### Prerequisites

- **VS Code**: 1.93.0+
- **Java**: JDK 17+ (for Language Server)
- **Grails**: 4.0+ recommended (supports 3.x with limitations)
- **Gradle**: 7.0+ for project build management
- **Node.js**: 16+ (for extension development)

### Required Extensions

- [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) - Automatically installed as dependency

### Recommended Extensions

- [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- [Prettier - Code Formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)
- [GitLens](https://marketplace.visualstudio.com/items?itemName=eamodio.gitlens)

## 🚀 Quick Start

### For Users

1. **Install from [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-grails)**
2. **Open a Grails project** (contains `build.gradle` and `grails-app/` folder)
3. **Configure Java path** if needed: `Settings → Grails → Java Home`
4. **Start developing** with full LSP support and IntelliSense!

### Configuration Settings

Configure the extension through VS Code settings:

```json
{
 "grails.javaHome": "/path/to/java-17",
 "grailsLsp.completionDetail": "ADVANCED",
 "grailsLsp.enableGrailsMagic": true,
 "grailsLsp.codeLensMode": "ADVANCED"
}
```

## 🏗️ Architecture

This extension uses a **client-server architecture** for optimal performance:

- **[Client](./client/)** - VS Code extension (TypeScript)

  - User interface and VS Code integration
  - Command palette, views, and UI components
  - Language client that communicates with LSP server

- **[Server](./server/)** - Language Server (Groovy + LSP4j + Gradle Tooling API)
  - Advanced Groovy/Grails compilation engine
  - AST analysis and intelligent code features
  - Gradle project integration and dependency resolution

```text
┌─────────────────┐    LSP Protocol    ┌──────────────────┐
│   VS Code       │◄──────────────────►│  Language Server │
│   Extension     │    (JSON-RPC)      │   (Java/Groovy)  │
│  (TypeScript)   │                    │                  │
└─────────────────┘                    └──────────────────┘
```

## 📖 Documentation

- [Getting Started](./docs/getting-started.md) - Installation and basic setup
- [Features Overview](./docs/features.md) - Complete feature documentation
- [Configuration Guide](./docs/configuration.md) - Extension settings and customization
- [Development Setup](./docs/development.md) - Contributing and development guide
- [Architecture Details](./docs/architecture.md) - Technical implementation details
- [Troubleshooting](./docs/troubleshooting.md) - Common issues and solutions

## 🛠️ Development Workflow

### Prerequisites for Development

- **Git** for version control
- **Java 17+** for Language Server development
- **Node.js 16+** for VS Code extension development
- **IntelliJ IDEA** (recommended for server development)
- **VS Code** (for extension development)

### Step-by-Step Setup

#### 1. Clone and Setup

```bash
# Clone the repository
git clone https://github.com/KingSK1998/vscode-grails.git
cd vscode-grails

# Initial setup (installs dependencies and builds both components)
npm run setup
```

#### 2. IDE Workspace Setup

**VS Code Workspace (Recommended for Extension Development):**

- Open **root folder** (`vscode-grails/`) in VS Code
- This gives you access to:
  - npm scripts for build automation
  - Launch configurations (F5 debugging)
  - Multi-folder workspace with both client and server
  - Integrated terminal for running commands

**IntelliJ IDEA Workspace (Recommended for Server Development):**

- Open **server folder** (`vscode-grails/server/`) in IntelliJ IDEA
- Import as Gradle project

#### 3. Development Mode

**Terminal 1 - Start Language Server (Debug Mode):**

```bash
npm run dev:server
```

Or use IntelliJ IDEA and its F5/Debug configuration to launch in debug mode.

**Terminal 2 - Start VS Code Extension:**

- In VS Code, **press F5** to launch Extension Development Host

#### 4. Daily Development Workflow

1. **Edit server code** in IntelliJ IDEA
2. **Rebuild server** if needed: `npm run build:server`
3. **Restart language server** if running
4. **Test extension** in VS Code Extension Development Host
5. **Edit client code** in VS Code
6. **Reload extension** (**Ctrl+R** in Extension Development Host)

### Available npm Scripts

```bash
npm run setup # One-time setup: install deps + build everything
npm run build # Build both client and server
npm run build:client # Build VS Code extension only
npm run build:server # Build Language Server only
npm run dev:server # Start Language Server in debug mode
npm run test # Run all tests (server + client)
npm run clean # Clean all build artifacts
npm run package # Create .vsix file for VS Code Marketplace
```

### Debug Configuration (VS Code)

`.vscode/launch.json`:

```json
{
 "version": "0.2.0",
 "configurations": [
  {
   "name": "Launch Extension",
   "type": "extensionHost",
   "request": "launch",
   "args": ["--extensionDevelopmentPath=${workspaceFolder}/client"],
   "outFiles": ["${workspaceFolder}/client/out/**/*.js"],
   "preLaunchTask": "npm: compile - client"
  },
  {
   "name": "Debug LSP Server",
   "type": "java",
   "request": "attach",
   "hostName": "localhost",
   "port": 5005
  }
 ]
}
```

## 🔧 Configuration

### Extension Settings

| Setting                        | Default      | Description                                   |
| ------------------------------ | ------------ | --------------------------------------------- |
| `grails.javaHome`              | `""`         | Path to Java installation for Language Server |
| `grails.path`                  | `""`         | Path to Grails installation directory         |
| `grailsLsp.completionDetail`   | `"ADVANCED"` | Code completion detail level                  |
| `grailsLsp.maxCompletionItems` | `1000`       | Maximum completion items returned             |
| `grailsLsp.enableGrailsMagic`  | `true`       | Enable Grails-specific features               |
| `grailsLsp.codeLensMode`       | `"ADVANCED"` | Code lens configuration                       |

### Workspace Configuration Example

Create `.vscode/settings.json` in your Grails project:

```json
{
 "grails.javaHome": "/usr/lib/jvm/java-17-openjdk",
 "grailsLsp.completionDetail": "ADVANCED",
 "grailsLsp.enableGrailsMagic": true,
 "java.import.gradle.enabled": true
}
```

## 🤝 Contributing

We welcome contributions!

1. Fork the repository on GitHub
2. Follow the development workflow above to set up your environment
3. Create a feature branch: `git checkout -b feature/amazing-feature`
4. Make your changes and add tests
5. Ensure all tests pass: `npm run test`
6. Commit your changes: `git commit -m 'Add amazing feature'`
7. Push to your fork: `git push origin feature/amazing-feature`
8. Open a Pull Request with a clear description

See our [Development Guide](./docs/development.md) for more details.

## 📊 Project Status

### Current Features (v0.0.1)

- Basic LSP integration with code completion
- Grails artifact recognition and navigation
- Syntax highlighting for Groovy and GSP
- Project explorer with Grails structure
- Command palette integration

### Roadmap

- Enhanced debugging support
- Multi-root workspace support
- Grails plugin ecosystem integration
- Performance optimizations for large projects
- Advanced refactoring tools

## 🐛 Known Issues

- Language server may take a few moments to initialize on first startup
- Large projects (1000+ files) might experience slower completion response times
- GSP syntax highlighting may not work perfectly with complex nested expressions

Report issues on our [GitHub Issues page](https://github.com/KingSK1998/vscode-grails/issues).

## 📄 License

MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

Built with ❤️ for the Grails community using:

- [Grails Framework](https://grails.org/)
- [Eclipse LSP4j](https://github.com/eclipse/lsp4j)
- [Gradle Tooling API](https://docs.gradle.org/current/userguide/third_party_integration.html)
- [VS Code Extension API](https://code.visualstudio.com/api)

### Special Thanks

- Grails community for continuous feedback and support
- VS Code team for excellent extension development tools
- Contributors and early adopters who help improve the extension

---

**Happy Grails Development!** 🎉

> 💡 **Need help?** Check our [documentation](./docs/) or [open an issue](https://github.com/KingSK1998/vscode-grails/issues/new)

# VS Code Extension + Server

This project contains a VS Code extension (`client`) and a backend server (`server`).  
The client is built with **TypeScript** and bundled using **esbuild**.  
The server is built with **Gradle** (producing a `-all.jar`).

---

## 🚀 Commands

### Client (VS Code Extension)

- **`npm run build`** → Type-checks, lints, and compiles the extension with `tsc`.
- **`npm run watch`** → Recompiles on file changes (use during development).
- **`npm run lint`** → Runs ESLint checks on `client/src`.
- **`npm run format`** → Auto-formats code with Prettier.

### Server

- **`npm run build-server`** → Builds the server JAR with Gradle.
- **`npm run copy-server`** → Builds the server JAR and copies it into the `client/server/` folder.

### Packaging

- **`npm run vscode:prepublish`** → Prepares the extension for publishing (build + copy server).
- **`npm run test`** → Runs extension tests (compiles first, then executes tests).

---

## 🛠️ Development Workflow

1. Run **`npm run watch`** to keep the client extension rebuilding on changes.
2. If server code changes, run **`npm run copy-server`** to update the JAR inside the client.
3. Launch the extension in VS Code (`F5` → "Launch Extension").

---

## 📦 CI/CD

- In CI, use **`npm ci && npm run vscode:prepublish`** to ensure a clean, reproducible build.
