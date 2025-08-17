# Development Guide

This guide covers the development setup and workflow for the Grails Framework Support VS Code extension.

## 🛠️ Development Environment Setup

### Prerequisites

1. **Node.js** (v18 or higher)
2. **npm** (v8 or higher)
3. **VS Code** (latest version)
4. **Git**

### Initial Setup

```bash
# Clone the repository
git clone https://github.com/KingSK1998/vscode-grails-extension.git
cd vscode-grails-extension

# Install dependencies
npm install

# Compile TypeScript
npm run compile
```

## 🏗️ Project Architecture

### Directory Structure

```
src/
├── commands/           # Command implementations
│   └── GrailsCommands.ts
├── config/            # Configuration management
│   └── GrailsConfig.ts
├── languageClient/    # Language Server Protocol
│   ├── LanguageServerManager.ts
│   ├── clientOptions.ts
│   └── serverOptions.ts
├── projectInfo/       # Project explorer
│   └── GrailsExplorer.ts
├── services/          # Core services
│   ├── ErrorService.ts
│   ├── GradleService.ts
│   ├── StatusBarService.ts
│   └── gradleApiInterfaces/
├── utils/             # Utilities and constants
│   └── Constants.ts
└── extension.ts       # Main entry point
```

### Key Components

#### Extension Entry Point (`extension.ts`)
- Handles extension activation and deactivation
- Initializes core services
- Validates project structure
- Manages language server lifecycle

#### Core Services
- **GradleService**: Integrates with VS Code Gradle extension
- **StatusBarService**: Manages status bar display
- **ErrorService**: Centralized error handling

#### Language Client
- **LanguageServerManager**: Manages LSP lifecycle
- **clientOptions**: Configures LSP client
- **serverOptions**: Configures LSP server startup

## 🔧 Development Workflow

### Running in Development Mode

1. **Open in VS Code**
   ```bash
   code .
   ```

2. **Start Watch Mode**
   ```bash
   npm run watch
   ```

3. **Launch Extension Development Host**
   - Press `F5` in VS Code
   - Or use `Run > Start Debugging`

### Testing Changes

1. **In Extension Development Host**:
   - Open a Grails project
   - Test extension features
   - Check console for errors

2. **Run Automated Tests**:
   ```bash
   npm test
   ```

### Code Quality

```bash
# Lint code
npm run lint

# Fix linting issues
npm run lint -- --fix

# Compile TypeScript
npm run compile
```

## 🧪 Testing

### Test Structure

```
src/test/
├── extension.test.ts    # Main extension tests
└── suite/              # Test suites
```

### Running Tests

```bash
# Run all tests
npm test

# Run with coverage
npm run test:coverage
```

### Writing Tests

```typescript
import * as assert from 'assert';
import * as vscode from 'vscode';

suite('Extension Test Suite', () => {
  test('Extension should activate', async () => {
    const extension = vscode.extensions.getExtension('KingSK1998.vscode-grails-extension');
    assert.ok(extension);
    
    await extension.activate();
    assert.strictEqual(extension.isActive, true);
  });
});
```

## 📦 Building and Packaging

### Local Build

```bash
# Compile TypeScript
npm run compile

# Package extension
npm install -g @vscode/vsce
vsce package
```

### Publishing

```bash
# Login to marketplace
vsce login KingSK1998

# Publish extension
vsce publish
```

## 🔍 Debugging

### Extension Debugging

1. **Set Breakpoints** in TypeScript files
2. **Press F5** to launch Extension Development Host
3. **Debug Console** shows output and allows evaluation

### Language Server Debugging

1. **Configure Development Mode**:
   ```json
   {
     "grails.server.developmentMode": true,
     "grails.server.port": 5007
   }
   ```

2. **Start External Language Server** (if applicable)
3. **Connect via Remote Debug**

### Common Debug Scenarios

#### Extension Not Activating
- Check `activationEvents` in `package.json`
- Verify workspace contains `build.gradle`
- Check console for activation errors

#### Commands Not Working
- Verify command registration in `package.json`
- Check command implementation
- Ensure proper error handling

#### Language Server Issues
- Check Java installation and `JAVA_HOME`
- Verify language server JAR path
- Check network connectivity for remote mode

## 🎯 Development Best Practices

### Code Style

- **TypeScript**: Use strict mode
- **Naming**: PascalCase for classes, camelCase for functions
- **Error Handling**: Use ErrorService for consistent reporting
- **Async/Await**: Prefer over Promises for readability

### Performance

- **Lazy Loading**: Load heavy operations on demand
- **Caching**: Cache expensive computations
- **Progress Indicators**: Show progress for long operations
- **Memory Management**: Dispose resources properly

### Extension Guidelines

- **Activation**: Keep activation fast
- **Commands**: Register in package.json, implement in commands/
- **Settings**: Use workspace configuration
- **UI**: Follow VS Code design patterns

## 🚀 Release Process

### Version Management

1. **Update Version** in `package.json`
2. **Update CHANGELOG.md** with new features/fixes
3. **Create Git Tag**:
   ```bash
   git tag v0.1.0
   git push origin v0.1.0
   ```

### Release Checklist

- [ ] All tests pass
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] Version bumped in package.json
- [ ] Extension tested in clean environment
- [ ] Release notes prepared

## 📚 Resources

### VS Code Extension Development
- [VS Code Extension API](https://code.visualstudio.com/api)
- [Extension Guidelines](https://code.visualstudio.com/api/references/extension-guidelines)
- [Publishing Extensions](https://code.visualstudio.com/api/working-with-extensions/publishing-extension)

### Language Server Protocol
- [LSP Specification](https://microsoft.github.io/language-server-protocol/)
- [LSP Client Library](https://github.com/microsoft/vscode-languageserver-node)

### Grails and Groovy
- [Grails Framework](https://grails.org/)
- [Groovy Language](https://groovy-lang.org/)

## 🤝 Getting Help

- **GitHub Issues**: Report bugs and request features
- **GitHub Discussions**: Ask questions and share ideas
- **VS Code Extension Development**: Official documentation and samples