# API Documentation

This document describes the internal APIs and extension points of the Grails Framework Support extension.

## Extension API

### Main Extension Class

The extension exports the following API for other extensions to interact with:

```typescript
interface GrailsExtensionAPI {
  readonly version: string;
  readonly isActive: boolean;
  
  // Services
  getGradleService(): GradleService;
  getStatusBarService(): StatusBarService;
  getErrorService(): ErrorService;
  
  // Language Server
  getLanguageClient(): LanguageClient | undefined;
  restartLanguageServer(): Promise<void>;
  
  // Project Information
  getProjectInfo(): Promise<GrailsProjectInfo>;
  isGrailsProject(workspaceFolder: string): boolean;
}
```

### Usage Example

```typescript
import * as vscode from 'vscode';

// Get the Grails extension
const grailsExtension = vscode.extensions.getExtension('KingSK1998.vscode-grails-extension');

if (grailsExtension) {
  await grailsExtension.activate();
  const api = grailsExtension.exports as GrailsExtensionAPI;
  
  // Check if current workspace is a Grails project
  const workspaceFolder = vscode.workspace.workspaceFolders?.[0];
  if (workspaceFolder && api.isGrailsProject(workspaceFolder.uri.fsPath)) {
    console.log('This is a Grails project!');
  }
}
```

## Core Services

### GradleService

Provides integration with the VS Code Gradle extension.

```typescript
class GradleService {
  /**
   * Initialize the Gradle API
   */
  async sync(): Promise<boolean>;
  
  /**
   * Run a Gradle task
   */
  async runGradleTask(
    projectFolder: string,
    taskName: string,
    onProgress?: (info: string) => void
  ): Promise<GradleTaskResult>;
}

interface GradleTaskResult {
  success: boolean;
  message: string;
  data?: string[];
}
```

### StatusBarService

Manages the extension's status bar display.

```typescript
class StatusBarService {
  /**
   * Update status bar with custom message
   */
  update(
    icon: STATUS_BAR_ICONS,
    text: string,
    tooltip?: string,
    type?: MODULE_TYPE,
    timeoutMs?: number
  ): void;
  
  /**
   * Show ready state
   */
  ready(type?: MODULE_TYPE, tooltip?: string): void;
  
  /**
   * Show syncing state
   */
  sync(type?: MODULE_TYPE, tooltip?: string): void;
  
  /**
   * Show error state
   */
  error(type?: MODULE_TYPE, tooltip?: string): void;
  
  /**
   * Show warning state
   */
  warning(type?: MODULE_TYPE, tooltip?: string): void;
}
```

### ErrorService

Centralized error handling and reporting.

```typescript
class ErrorService {
  /**
   * Handle and report an error
   */
  handle(
    error: unknown,
    source: MODULE_TYPE,
    severity?: ERROR_SEVERITY
  ): void;
}

enum ERROR_SEVERITY {
  INFO = "INFO",
  WARNING = "WARNING", 
  ERROR = "ERROR",
  FATAL = "FATAL"
}
```

## Language Server Integration

### LanguageServerManager

Manages the Grails Language Server lifecycle.

```typescript
class LanguageServerManager {
  /**
   * Start the language server
   */
  async start(): Promise<LanguageClient | undefined>;
  
  /**
   * Stop the language server
   */
  async stop(): Promise<void>;
  
  /**
   * Dispose resources
   */
  async dispose(): Promise<void>;
}
```

### Client Options

Configuration for the Language Server Protocol client.

```typescript
function getClientOptions(): LanguageClientOptions {
  return {
    documentSelector: [
      { scheme: "file", language: "groovy" },
      { scheme: "file", language: "gsp" }
    ],
    synchronize: {
      fileEvents: workspace.createFileSystemWatcher("**/*.{groovy,gsp}")
    },
    // ... other options
  };
}
```

### Server Options

Configuration for starting the language server.

```typescript
function getServerOptions(context: ExtensionContext): ServerOptions {
  // Returns configuration for local JAR or remote connection
}
```

## Project Explorer

### GrailsExplorerProvider

Tree data provider for the Grails project structure.

```typescript
class GrailsExplorerProvider implements vscode.TreeDataProvider<GrailsItem> {
  /**
   * Get tree item representation
   */
  getTreeItem(element: GrailsItem): vscode.TreeItem;
  
  /**
   * Get children of a tree item
   */
  getChildren(element?: GrailsItem): Promise<GrailsItem[]>;
  
  /**
   * Refresh the tree view
   */
  refresh(): void;
}

class GrailsItem extends vscode.TreeItem {
  constructor(
    public readonly label: string,
    public readonly collapsibleState: vscode.TreeItemCollapsibleState,
    public readonly contextValue: string
  );
}
```

## Configuration

### GrailsConfig

Centralized configuration management.

```typescript
class GrailsConfig {
  /**
   * Check if Grails is configured
   */
  static isGrailsConfigured(): boolean;
  
  /**
   * Get Grails installation path
   */
  static getGrailsPath(): string;
  
  /**
   * Get Java home for language server
   */
  static getJavaHome(): string | undefined;
  
  /**
   * Get Grails version
   */
  static getGrailsVersion(projectRoot?: string): string | undefined;
  
  /**
   * Get project root directory
   */
  static getProjectRoot(): string | undefined;
  
  /**
   * Check if directory is a Grails project
   */
  static isGrailsProjectFolder(folderPath: string): boolean;
  
  /**
   * Validate configuration
   */
  static validateConfig(): string | undefined;
}
```

## Commands

### Command Registration

Commands are registered in `package.json` and implemented in `GrailsCommands.ts`.

```typescript
export function registerCommands(
  context: ExtensionContext,
  gradleService: GradleService,
  projectRoot: string
): void;
```

### Available Commands

| Command ID | Title | Description |
|------------|-------|-------------|
| `grails.run` | Grails: Run Application | Start the Grails application |
| `grails.test` | Grails: Run Tests | Execute project tests |
| `grails.clean` | Grails: Clean | Clean the project |
| `grails.compile` | Grails: Compile | Compile the project |
| `grails.createArtifact` | Grails: Create New Artifact | Launch artifact creation wizard |
| `grails.setupWorkspace` | Grails: Setup Workspace | Configure workspace settings |
| `grails.restartServer` | Grails: Restart Language Server | Restart the language server |
| `grails.runGradleTask` | Grails: Run Gradle Task | Execute Gradle tasks |

## Events and Notifications

### Extension Events

The extension fires the following events:

```typescript
// Extension activation
vscode.commands.executeCommand('setContext', 'grails:activated', true);

// Project detection
vscode.commands.executeCommand('setContext', 'grails:projectDetected', true);

// Language server status
vscode.commands.executeCommand('setContext', 'grails:serverRunning', true);
```

### Language Server Notifications

```typescript
// Configuration changes
client.sendNotification('workspace/didChangeConfiguration', { settings });

// Progress notifications
client.onNotification('$/progress', (params) => {
  // Handle server progress updates
});
```

## Extension Points

### Contributing Commands

Other extensions can contribute commands to the Grails category:

```json
{
  "contributes": {
    "commands": [
      {
        "command": "myExtension.grailsCommand",
        "title": "My Grails Command",
        "category": "Grails"
      }
    ]
  }
}
```

### Contributing Menus

Add items to Grails Explorer context menu:

```json
{
  "contributes": {
    "menus": {
      "view/item/context": [
        {
          "command": "myExtension.contextCommand",
          "when": "view == grailsExplorer && viewItem == controller"
        }
      ]
    }
  }
}
```

## Constants and Utilities

### Constants

```typescript
// Module types
enum MODULE_TYPE {
  GRADLE = "Gradle",
  SERVER = "Server", 
  CLIENT = "Client",
  PROJECT = "Project",
  EXTENSION = "GFS"
}

// Status bar icons
enum STATUS_BAR_ICONS {
  SYNC_SPIN = "$(sync~spin)",
  ROCKET = "$(rocket)",
  ERROR = "$(error)",
  WARNING = "$(warning)",
  SUCCESS = "$(check)"
}

// File paths
const FILE_PATHS = {
  GRAILS_APP: "grails-app",
  BUILD_GRADLE: "build.gradle",
  CONTROLLERS_DIR: "grails-app/controllers",
  SERVICES_DIR: "grails-app/services",
  DOMAIN_DIR: "grails-app/domain"
};
```

## Error Handling

### Error Patterns

```typescript
try {
  // Extension operation
} catch (error) {
  errorService.handle(error, MODULE_TYPE.EXTENSION, ERROR_SEVERITY.ERROR);
}
```

### Custom Errors

```typescript
class GrailsExtensionError extends Error {
  constructor(
    message: string,
    public readonly source: MODULE_TYPE,
    public readonly severity: ERROR_SEVERITY
  ) {
    super(message);
    this.name = 'GrailsExtensionError';
  }
}
```

---

This API documentation is subject to change as the extension evolves. Please refer to the source code for the most up-to-date information.