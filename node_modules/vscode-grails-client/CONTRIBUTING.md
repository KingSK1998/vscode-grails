# Contributing to Grails Framework Support

Thank you for your interest in contributing to the Grails Framework Support VS Code extension! We welcome contributions from the community.

## 🚀 Getting Started

### Prerequisites

- **Node.js**: Version 18 or higher
- **npm**: Version 8 or higher
- **VS Code**: Latest version
- **Git**: For version control

### Development Setup

1. **Fork and Clone**
   ```bash
   git clone https://github.com/YOUR_USERNAME/vscode-grails-extension.git
   cd vscode-grails-extension
   ```

2. **Install Dependencies**
   ```bash
   npm install
   ```

3. **Build the Extension**
   ```bash
   npm run compile
   ```

4. **Run in Development Mode**
   - Press `F5` in VS Code to launch Extension Development Host
   - Or use `npm run watch` for continuous compilation

## 🛠️ Development Workflow

### Project Structure
```
src/
├── commands/          # Grails command implementations
├── config/           # Configuration management
├── languageClient/   # Language Server Protocol client
├── projectInfo/      # Project explorer and tree views
├── services/         # Core services (Gradle, Error, StatusBar)
├── utils/           # Constants and utilities
└── extension.ts     # Main extension entry point
```

### Code Style

We use the following tools for code quality:

- **TypeScript**: Strict mode enabled
- **ESLint**: For linting and code standards
- **Prettier**: For code formatting
- **EditorConfig**: For consistent editor settings

Run linting and formatting:
```bash
npm run lint          # Check for linting errors
npm run lint --fix    # Auto-fix linting issues
```

### Testing

```bash
npm test              # Run all tests
npm run pretest       # Compile and lint before testing
```

## 📝 Contribution Guidelines

### Reporting Issues

Before creating an issue, please:

1. **Search existing issues** to avoid duplicates
2. **Use the issue template** when available
3. **Provide detailed information**:
   - VS Code version
   - Extension version
   - Operating system
   - Steps to reproduce
   - Expected vs actual behavior
   - Screenshots/logs if applicable

### Submitting Pull Requests

1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make Your Changes**
   - Follow the existing code style
   - Add tests for new functionality
   - Update documentation as needed
   - Ensure all tests pass

3. **Commit Your Changes**
   ```bash
   git commit -m "feat: add new feature description"
   ```
   
   Use conventional commit messages:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `docs:` for documentation changes
   - `style:` for formatting changes
   - `refactor:` for code refactoring
   - `test:` for adding tests
   - `chore:` for maintenance tasks

4. **Push and Create PR**
   ```bash
   git push origin feature/your-feature-name
   ```
   
   Then create a pull request on GitHub with:
   - Clear title and description
   - Reference to related issues
   - Screenshots/demos if applicable

### Code Review Process

1. **Automated Checks**: All PRs must pass CI checks
2. **Code Review**: At least one maintainer review required
3. **Testing**: Ensure changes work in Extension Development Host
4. **Documentation**: Update relevant documentation

## 🎯 Areas for Contribution

### High Priority
- **Language Server Features**: Improve Groovy/GSP language support
- **Performance Optimization**: Reduce extension startup time
- **Error Handling**: Better error messages and recovery
- **Testing**: Increase test coverage

### Medium Priority
- **Documentation**: Improve user guides and API docs
- **Snippets**: Add more Grails code snippets
- **Commands**: New Grails-specific commands
- **UI/UX**: Improve project explorer and status indicators

### Good First Issues
- **Bug Fixes**: Small, well-defined bugs
- **Documentation**: Fix typos, improve clarity
- **Snippets**: Add missing Grails patterns
- **Configuration**: New configuration options

## 🔧 Technical Guidelines

### TypeScript Best Practices

- Use strict type checking
- Prefer interfaces over types for object shapes
- Use meaningful variable and function names
- Add JSDoc comments for public APIs
- Handle errors gracefully with try-catch blocks

### Extension Development

- **Activation**: Keep activation fast and lightweight
- **Commands**: Register commands in `package.json` and implement in `commands/`
- **Services**: Use dependency injection pattern for services
- **Error Handling**: Use `ErrorService` for consistent error reporting
- **Status Updates**: Use `StatusBarService` for user feedback

### Language Server Integration

- **Client Options**: Configure in `languageClient/clientOptions.ts`
- **Server Options**: Configure in `languageClient/serverOptions.ts`
- **Protocol**: Follow LSP specifications for communication

## 📚 Resources

### Documentation
- [VS Code Extension API](https://code.visualstudio.com/api)
- [Language Server Protocol](https://microsoft.github.io/language-server-protocol/)
- [Grails Framework](https://grails.org/documentation.html)
- [Groovy Language](https://groovy-lang.org/documentation.html)

### Tools
- [VS Code Extension Generator](https://github.com/Microsoft/vscode-generator-code)
- [Extension Test Runner](https://github.com/microsoft/vscode-test)

## 🤝 Community

- **GitHub Discussions**: For questions and feature discussions
- **Issues**: For bug reports and feature requests
- **Pull Requests**: For code contributions

## 📄 License

By contributing to this project, you agree that your contributions will be licensed under the MIT License.

---

Thank you for contributing to make Grails development in VS Code even better! 🎉