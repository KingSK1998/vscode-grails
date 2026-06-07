# Grails Framework Support for VS Code

[![Visual Studio Marketplace Version](https://img.shields.io/visual-studio-marketplace/v/KingSK1998.vscode-gng-support)](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-gng-support)
[![GitHub](https://img.shields.io/github/license/KingSK1998/vscode-gng-support)](https://github.com/KingSK1998/vscode-gng-support/blob/main/LICENSE)
[![Build Status](https://github.com/KingSK1998/vscode-gng-support/workflows/CI/CD%20Pipeline/badge.svg)](https://github.com/KingSK1998/vscode-gng-support/actions)

A comprehensive VS Code extension that provides full-featured support for **Grails** and **Groovy** development with intelligent Language Server Protocol (LSP) integration.

![Grails Action](resources/images/action.png)

## ✨ Core Features

- **Grails 7+ Support**: Full compatibility with the latest Grails framework versions, Groovy 4 dependencies, and Spring Boot 3 integration.
- **Advanced Type Interference**: Robust type analysis for Groovy's dynamic capabilities, ensuring accurate intellisense.
- **Auto Import Generation**: Automatically suggests and manages your class imports based on context and workspace index powered by ClassGraph.
- **Closure Delegates DSL**: Intelligent processing of standard Grails closures (e.g. constraints, mapping) giving precise auto-completions.
- **Named Parameters**: Seamless completion for Grails-specific named params (e.g. renders, redirects, mapping rules).
- **Multi Root Workspace**: Out-of-the-box support for multiple Grails applications and plugins loaded in the same VS Code window.
- **Advanced Language Support**: Powered by a robust Core LSP engine with AST Visitor and comprehensive symbol diagnostics.
- **Experimental AI Intellisense**: Context-aware line completion explorations using ML-based offline suggestions trained on Groovy and Grails code.
- **Operational Dashboards**: Dual-mode management webview dashboards (User Insights and Developer Diagnostics) running inside the IDE.
- **In-Editor Quick Fixes**: Smart code actions for missing dependencies (Auto-Dependency Injection for services) and missing methods.
- **Project Tree Explorer**: Graphical hierarchy specifically structured for Grails artifacts (Controllers, Domains, Services, Views).
- **Artifact Wizards**: Command-based UI for instantly generating Controllers, Services, and Domains.
- **Core LSP Engine**: Efficient multi-threaded server architecture communicating instantaneously with the extension client.
- **Inlay Type Hints**: Semantic type and parameter hints seamlessly floating inline in variable assignments and method calls.
- **Gutter Navigation**: Intuitive decorators allowing rapid jumps between a Controller action and its View equivalent.
- **Virtual Groovy Mapping**: Abstracted GSP scriptlet capabilities simulating Groovy script parsing for framework wiring.
- **Smart Action Links**: Readily identifying actions and domain mapping relationships.
- **Asset Pipeline Support**: Asset pipeline static resolution improvements for Javascript and CSS imports.
- **i18n Property Indexing**: Full autocompletion and diagnostic capabilities for your message bundle property files.
- **Semantic Highlighting**: Syntactic text decoration mapping injected properties, variables, and Grails tags.
- **Custom TagLib Discovery**: Discovers workspace GSP tags and evaluates them dynamically in GSP templates.
- **In-Editor Log Stream**: Developer application logs streamed in realtime directly within VS Code's Output channels.
- **Documentations**: Extended documentation and reference hovering derived straight from standard Groovy, Java, and Grails API/sources.

## 📋 Requirements

### Prerequisites

- **VS Code**: `^1.103.0` (see `package.json` `engines.vscode`)
- **Java**: JDK 17+ (for Language Server)
- **Grails**: 4.0+ recommended (supports 3.x with limitations)
- **Gradle**: 7.0+ for project build management
- **Node.js**: 20.x recommended (CI uses 20.18.1; see [docs/developer-guide.md#ci](./docs/developer-guide.md#ci))

### Required Extensions

- [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle) - Automatically installed as dependency

### Recommended Extensions

- [Java Extension Pack](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
- [Prettier - Code Formatter](https://marketplace.visualstudio.com/items?itemName=esbenp.prettier-vscode)
- [GitLens](https://marketplace.visualstudio.com/items?itemName=eamodio.gitlens)

## 🚀 Quick Start

### For Users

1. **Install from [VS Code Marketplace](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-gng-support)**
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

| Guide | For |
|-------|-----|
| [**User guide**](./docs/user-guide.md) | Install, settings, features, troubleshooting |
| [**Developer guide**](./docs/developer-guide.md) | Build, debug, testing, compiler, logging, CI |
| [**Architecture**](./docs/architecture.md) | Client ↔ server design, provider tiers, feature ownership |
| [Server rules](./server/RULES.md) | Server architecture rules (for AI agents) |
| [Client rules](./client/RULES.md) | Client architecture rules (for AI agents) |

## 🛠️ Development Workflow

### Prerequisites for Development

- **Git** for version control
- **Java 17+** for Language Server development
- **Node.js 20+** for VS Code extension development (match CI)
- **IntelliJ IDEA** (recommended for server development)
- **VS Code** (for extension development)

### Step-by-Step Setup

#### 1. Clone and Setup

```bash
# Clone the repository
git clone https://github.com/KingSK1998/vscode-gng-support.git
cd vscode-gng-support

# Initial setup (installs dependencies and builds both components)
npm run setup
```

#### 2. IDE Workspace Setup

**VS Code Workspace (Recommended for Extension Development):**

- Open **root folder** (`vscode-gng-support/`) in VS Code
- This gives you access to:
  - npm scripts for build automation
  - Launch configurations (F5 debugging)
  - Multi-folder workspace with both client and server
  - Integrated terminal for running commands

**IntelliJ IDEA Workspace (Recommended for Server Development):**

- Open **server folder** (`vscode-gng-support/server/`) in IntelliJ IDEA
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

See [docs/developer-guide.md](./docs/developer-guide.md) for more details.

## 📊 Project status

See **[docs/user-guide.md#status-and-roadmap](./docs/user-guide.md#status-and-roadmap)** (version **0.0.2**). High level:

- LSP: completion, hover, diagnostics, definition, implementation, references, symbols, code lens, inlay hints, rename (best-effort). Not yet: formatting, folding, semantic tokens, code actions / quick fixes.
- Roadmap: true multi-root indexing, richer refactorings, performance hardening for very large repos.

## 🐛 Known Issues

- Language server may take a few moments to initialize on first startup
- Large projects (1000+ files) might experience slower completion response times
- **Rename** is best-effort; complex Groovy AST transformations (metaprogramming) may not always be tracked perfectly
- No LSP formatting, semantic highlighting, or quick fixes yet ([developer guide — LSP reference](./docs/developer-guide.md#language-server-reference))

Report issues on our [GitHub Issues page](https://github.com/KingSK1998/vscode-gng-support/issues).

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

> 💡 **Need help?** Check our [documentation](./docs/) or [open an issue](https://github.com/KingSK1998/vscode-gng-support/issues/new)

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

- Workflow: **[CI/CD Pipeline on GitHub Actions](https://github.com/KingSK1998/vscode-gng-support/actions)** (Java 17, Node 20.18.1; server build **skips tests**; client `compile` + `bundle`). Details: [docs/developer-guide.md#ci](./docs/developer-guide.md#ci).
- Local release prep: **`npm ci && npm run vscode:prepublish`** for a clean, reproducible build.
