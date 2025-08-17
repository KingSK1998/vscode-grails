# User Guide - Grails Framework Support

Welcome to the Grails Framework Support extension for VS Code! This guide will help you get started and make the most of the extension's features.

## 🚀 Quick Start

### Installation

1. **Install from VS Code Marketplace**:
   - Open VS Code
   - Go to Extensions (`Ctrl+Shift+X`)
   - Search for "Grails Framework Support"
   - Click "Install"

2. **Install Required Dependencies**:
   - The extension will automatically install the Gradle for Java extension
   - Ensure you have Java 11+ installed

### First Time Setup

1. **Open a Grails Project**:
   - Open a folder containing a `build.gradle` file
   - The extension will automatically activate

2. **Configure Settings** (Optional):
   - Open Settings (`Ctrl+,`)
   - Search for "Grails"
   - Set `grails.path` if Grails is not in your PATH
   - Set `grails.javaHome` if using a specific Java version

## 🎯 Core Features

### Project Explorer

The Grails Explorer provides a structured view of your project:

- **Controllers**: View all controller classes
- **Services**: Browse service classes
- **Domains**: Explore domain models

**Usage**:
- Click on the Grails icon in the Activity Bar
- Expand categories to see files
- Click on files to open them

### Command Palette Integration

Access Grails commands quickly:

1. Open Command Palette (`Ctrl+Shift+P`)
2. Type "Grails" to see available commands
3. Select the command you want to execute

**Available Commands**:
- `Grails: Run Application` - Start your Grails app
- `Grails: Run Tests` - Execute tests
- `Grails: Clean` - Clean the project
- `Grails: Compile` - Compile the project
- `Grails: Create New Artifact` - Launch creation wizard
- `Grails: Setup Workspace` - Configure workspace settings

### Artifact Creation Wizard

Create new Grails artifacts easily:

1. Run `Grails: Create New Artifact` command
2. Select artifact type (Controller, Service, Domain, etc.)
3. Enter the artifact name
4. The extension will generate the file with proper structure

**Supported Artifacts**:
- Controllers
- Services
- Domain Classes
- TagLibs
- Jobs
- Commands
- Interceptors

### Code Snippets

The extension provides rich snippets for common Grails patterns:

**Groovy Snippets**:
- `controller` - Create a controller class
- `service` - Create a service class
- `domain` - Create a domain class
- `test` - Create a unit test
- `action` - Create a controller action
- `method` - Create a service method

**GSP Snippets**:
- `layout` - Create a GSP layout
- `form` - Create a Grails form
- `each` - Create a g:each loop
- `if` - Create a g:if condition
- `link` - Create a g:link
- `message` - Create a g:message tag

**Usage**:
1. Start typing the snippet prefix
2. Press `Tab` to expand
3. Use `Tab` to navigate between placeholders

### Language Support

#### Groovy Files (.groovy)
- Syntax highlighting
- Auto-completion
- Bracket matching
- Comment toggling (`Ctrl+/`)

#### GSP Files (.gsp)
- HTML and Grails tag syntax highlighting
- Auto-completion for Grails tags
- Bracket matching for tags and expressions
- Comment toggling (`Ctrl+/`)

## ⚙️ Configuration

### Basic Settings

Open VS Code Settings and configure:

```json
{
  "grails.path": "/path/to/grails",
  "grails.javaHome": "/path/to/java",
  "grails.server.port": 5007,
  "grails.server.host": "localhost"
}
```

### Language Server Settings

Fine-tune the language server behavior:

```json
{
  "grailsLsp.completionDetail": "ADVANCED",
  "grailsLsp.maxCompletionItems": 100,
  "grailsLsp.includeSnippets": true,
  "grailsLsp.enableGrailsMagic": true
}
```

### Workspace Setup

Run `Grails: Setup Workspace` to automatically configure:
- Emmet support for GSP files
- Debug configuration for Grails apps
- Recommended extensions
- Optimal settings for Grails development

## 🛠️ Development Workflow

### Typical Workflow

1. **Open Project**: Open your Grails project folder
2. **Explore Structure**: Use Grails Explorer to navigate
3. **Create Artifacts**: Use the creation wizard for new files
4. **Write Code**: Leverage snippets and auto-completion
5. **Run Application**: Use `Grails: Run Application` command
6. **Test**: Use `Grails: Run Tests` command

### Working with Controllers

1. **Create Controller**:
   - Use `Grails: Create New Artifact` → Controller
   - Or use `controller` snippet in a .groovy file

2. **Add Actions**:
   - Use `action` snippet
   - Leverage auto-completion for Grails methods

3. **Create Views**:
   - Create corresponding GSP files in `grails-app/views/`
   - Use GSP snippets for common patterns

### Working with Services

1. **Create Service**:
   - Use artifact wizard or `service` snippet
   - Services are automatically transactional

2. **Inject into Controllers**:
   ```groovy
   class BookController {
       BookService bookService
       
       def index() {
           [books: bookService.listBooks()]
       }
   }
   ```

### Working with Domains

1. **Create Domain**:
   - Use artifact wizard or `domain` snippet
   - Define properties and constraints

2. **Use in Controllers**:
   ```groovy
   def save() {
       def book = new Book(params)
       if (book.save()) {
           redirect(action: 'show', id: book.id)
       } else {
           render(view: 'create', model: [book: book])
       }
   }
   ```

## 🔧 Troubleshooting

### Common Issues

#### Extension Not Activating
- **Check**: Ensure your project has a `build.gradle` file
- **Solution**: Create or verify the build.gradle file exists

#### Language Server Not Starting
- **Check**: Java installation and JAVA_HOME
- **Solution**: Set `grails.javaHome` in settings

#### Commands Not Available
- **Check**: Extension activation status
- **Solution**: Reload window (`Ctrl+Shift+P` → "Developer: Reload Window")

#### Gradle Extension Missing
- **Check**: Gradle for Java extension is installed
- **Solution**: Install from Extensions marketplace

### Debug Mode

Enable debug logging:

1. Open Output panel (`Ctrl+Shift+U`)
2. Select "Grails Framework Support" from dropdown
3. Check for error messages and warnings

### Reset Extension

If experiencing issues:

1. Disable the extension
2. Reload VS Code
3. Re-enable the extension
4. Run `Grails: Setup Workspace` if needed

## 📚 Tips and Tricks

### Productivity Tips

1. **Use Snippets**: Learn common snippet prefixes for faster coding
2. **Command Palette**: Use `Ctrl+Shift+P` for quick access to commands
3. **Explorer Integration**: Right-click in Grails Explorer for context actions
4. **Auto-completion**: Press `Ctrl+Space` to trigger suggestions

### Keyboard Shortcuts

- `Ctrl+Shift+P` - Command Palette
- `Ctrl+,` - Open Settings
- `Ctrl+Shift+X` - Extensions
- `Ctrl+Shift+U` - Output Panel
- `Ctrl+/` - Toggle Comment
- `F5` - Run/Debug (if configured)

### Best Practices

1. **Project Structure**: Follow Grails conventions for automatic detection
2. **Configuration**: Use workspace settings for project-specific config
3. **Version Control**: Include `.vscode/settings.json` for team consistency
4. **Extensions**: Install recommended extensions for full Grails support

## 🆘 Getting Help

### Documentation
- [Extension README](../README.md)
- [Development Guide](DEVELOPMENT.md)
- [Grails Documentation](https://grails.org/documentation.html)

### Support Channels
- [GitHub Issues](https://github.com/KingSK1998/vscode-grails-extension/issues) - Bug reports and feature requests
- [GitHub Discussions](https://github.com/KingSK1998/vscode-grails-extension/discussions) - Questions and community support

### Contributing
- See [Contributing Guide](../CONTRIBUTING.md) for how to contribute
- Report bugs and suggest features via GitHub Issues

---

Happy Grails development! 🎉