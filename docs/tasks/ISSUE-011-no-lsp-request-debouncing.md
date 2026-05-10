# ISSUE-011 · No LSP Request Debouncing
**Severity**: 🔴 Critical
**Service**: DependencyGraphService.ts, GormSqlPreviewService.ts, GrailsTestService.ts
**Status**: TODO
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
LSP requests are sent immediately without debouncing. Rapid calls to refreshGraph(), refreshPreview(), and discoverAllTests() can flood the server with requests, causing performance degradation and potential crashes.

## Current Code (problematic pattern)
```typescript
// DependencyGraphService.ts - Lines 53-83
private async refreshGraph(project: ProjectInfo) {
  if (!this.currentPanel) {
    return;
  }

  try {
    const client = this.container.languageServerManager.languageClient;
    if (!client) {
      vscode.window.showErrorMessage("Language server not running");
      return;
    }

    // NO DEBOUNCING - request sent immediately
    const graphData: unknown = await client.sendRequest("workspace/executeCommand", {
      command: "grails.getDependencyGraph",
      arguments: [vscode.Uri.file(project.rootPath).toString()],
    });

    void this.currentPanel.webview.postMessage({
      command: "updateGraph",
      data: typeof graphData === "string" ? (JSON.parse(graphData) as unknown) : graphData,
    });
  } catch (error) {
    this.errorService.handleError(
      "Failed to get dependency graph",
      error,
      ErrorSource.LanguageServer,
      ErrorSeverity.Error
    );
  }
}

// GormSqlPreviewService.ts - Lines 45-74
private async refreshPreview(uri: vscode.Uri) {
  if (!this.currentPanel) {
    return;
  }

  try {
    const client = this.container.languageServerManager.languageClient;
    if (!client) {
      vscode.window.showErrorMessage("Language server not running");
      return;
    }

    // NO DEBOUNCING - request sent immediately
    const sql = await client.sendRequest("workspace/executeCommand", {
      command: "grails.getGormSql",
      arguments: [uri.toString()],
    });

    this.currentPanel.webview.postMessage({
      command: "updateSql",
      sql: sql ?? "-- No SQL generated.",
    });
  } catch (error) {
    this.errorService.handleError(
      "Failed to generate SQL preview",
      error,
      ErrorSource.LanguageServer,
      ErrorSeverity.Error
    );
  }
}

// GrailsTestService.ts - Lines 28-75
private async discoverAllTests() {
  try {
    const client = this.container.languageServerManager.languageClient;
    if (!client) {
      return;
    }

    const projects = this.container.projectService.getProjects();
    for (const project of projects) {
      // NO DEBOUNCING - request sent immediately for each project
      interface DiscoveredTest {
        className: string;
        uri: string;
        methods: string[];
      }
      const tests = await client.sendRequest<DiscoveredTest[]>("workspace/executeCommand", {
        command: "grails.discoverTests",
        arguments: [vscode.Uri.file(project.rootPath).toString()],
      });

      for (const test of tests) {
        const testItem = this.controller.createTestItem(
          test.className,
          test.className,
          vscode.Uri.parse(test.uri)
        );
        testItem.canResolveChildren = true;

        for (const methodName of test.methods) {
          const methodItem = this.controller.createTestItem(
            `${test.className}.${methodName}`,
            methodName,
            vscode.Uri.parse(test.uri)
          );
          testItem.children.add(methodItem);
        }

        this.controller.items.add(testItem);
      }
    }
  } catch (error) {
    this.errorService.handleError(
      "Test discovery failed",
      error,
      ErrorSource.Testing,
      ErrorSeverity.Error
    );
  }
}
```

## Fixed Code

First, create a debounce utility:

```typescript
// client/src/utils/DebounceUtils.ts

/**
 * Creates a debounced function that delays invoking func until after wait milliseconds
 * have elapsed since the last time the debounced function was invoked.
 *
 * @param func The function to debounce
 * @param wait The number of milliseconds to delay
 * @returns A new debounced function
 */
export function debounce<T extends (...args: any[]) => any>(
  func: T,
  wait: number
): (...args: Parameters<T>) => void {
  let timeoutId: NodeJS.Timeout | undefined;
  let lastArgs: Parameters<T> | undefined;

  const debounced = (...args: Parameters<T>) => {
    lastArgs = args;

    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    timeoutId = setTimeout(() => {
      timeoutId = undefined;
      func(...lastArgs!);
      lastArgs = undefined;
    }, wait);
  };

  // Add a method to cancel pending execution
  debounced.cancel = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutId = undefined;
      lastArgs = undefined;
    }
  };

  // Add a method to flush pending execution
  debounced.flush = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutId = undefined;
      func(...lastArgs!);
      lastArgs = undefined;
    }
  };

  return debounced as T & { cancel: () => void; flush: () => void };
}

/**
 * Creates a debounced async function that returns a promise.
 * Cancels any pending request when a new one is made.
 *
 * @param func The async function to debounce
 * @param wait The number of milliseconds to delay
 * @returns A new debounced async function
 */
export function debounceAsync<T extends (...args: any[]) => Promise<any>>(
  func: T,
  wait: number
): (...args: Parameters<T>) => Promise<ReturnType<T>> {
  let timeoutId: NodeJS.Timeout | undefined;
  let lastArgs: Parameters<T> | undefined;
  let lastResolve: ((value: ReturnType<T>) => void) | undefined;
  let lastReject: ((reason: any) => void) | undefined;

  const debounced = (...args: Parameters<T>): Promise<ReturnType<T>> => {
    // Cancel previous timeout
    if (timeoutId) {
      clearTimeout(timeoutId);
    }

    // Reject previous promise if still pending
    if (lastReject) {
      lastReject(new Error("Debounced request cancelled"));
      lastResolve = undefined;
      lastReject = undefined;
    }

    return new Promise<ReturnType<T>>((resolve, reject) => {
      lastArgs = args;
      lastResolve = resolve;
      lastReject = reject;

      timeoutId = setTimeout(async () => {
        timeoutId = undefined;
        const currentResolve = lastResolve;
        const currentReject = lastReject;
        const currentArgs = lastArgs;

        lastResolve = undefined;
        lastReject = undefined;
        lastArgs = undefined;

        try {
          const result = await func(...currentArgs!);
          currentResolve!(result);
        } catch (error) {
          currentReject!(error);
        }
      }, wait);
    });
  };

  // Add a method to cancel pending execution
  debounced.cancel = () => {
    if (timeoutId) {
      clearTimeout(timeoutId);
      timeoutId = undefined;
    }
    if (lastReject) {
      lastReject(new Error("Debounced request cancelled"));
      lastResolve = undefined;
      lastReject = undefined;
    }
    lastArgs = undefined;
  };

  return debounced as T & { cancel: () => void };
}
```

Now update the services:

```typescript
// DependencyGraphService.ts
import { debounceAsync } from "../../utils/DebounceUtils";

export class DependencyGraphService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private container: ServiceContainer = ServiceContainer.getInstance();

  // Debounced refresh with 300ms delay
  private readonly debouncedRefresh = debounceAsync(
    (project: ProjectInfo) => this.doRefreshGraph(project),
    300
  );

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {}

  public openGraph(project: ProjectInfo) {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (this.currentPanel) {
      this.currentPanel.reveal(column);
      void this.debouncedRefresh(project);
      return;
    }

    this.currentPanel = vscode.window.createWebviewPanel(
      "grailsDependencyGraph",
      `Dependency Graph: ${project.name}`,
      column ?? vscode.ViewColumn.One,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
        localResourceRoots: [vscode.Uri.file(path.join(this.context.extensionPath, "resources"))],
      }
    );

    this.currentPanel.webview.html = this.getHtmlContent();

    this.currentPanel.onDidDispose(
      () => {
        this.currentPanel = null;
        // Cancel any pending refresh
        this.debouncedRefresh.cancel();
      },
      null,
      this.context.subscriptions
    );

    // Initial load
    void this.debouncedRefresh(project);
  }

  /**
   * Internal refresh method - does the actual LSP request.
   */
  private async doRefreshGraph(project: ProjectInfo): Promise<void> {
    if (!this.currentPanel) {
      return;
    }

    try {
      const client = this.container.languageServerManager.languageClient;
      if (!client) {
        vscode.window.showErrorMessage("Language server not running");
        return;
      }

      console.log(`[DependencyGraphService] Requesting graph for ${project.name}`);

      const graphData: unknown = await client.sendRequest("workspace/executeCommand", {
        command: "grails.getDependencyGraph",
        arguments: [vscode.Uri.file(project.rootPath).toString()],
      });

      void this.currentPanel.webview.postMessage({
        command: "updateGraph",
        data: typeof graphData === "string" ? (JSON.parse(graphData) as unknown) : graphData,
      });

      console.log(`[DependencyGraphService] Graph updated for ${project.name}`);
    } catch (error) {
      this.errorService.handleError(
        "Failed to get dependency graph",
        error,
        ErrorSource.LanguageServer,
        ErrorSeverity.Error
      );
    }
  }

  dispose(): void {
    this.debouncedRefresh.cancel();
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }
}

// GormSqlPreviewService.ts
import { debounceAsync } from "../../utils/DebounceUtils";

export class GormSqlPreviewService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private container: ServiceContainer = ServiceContainer.getInstance();

  // Debounced refresh with 300ms delay
  private readonly debouncedRefresh = debounceAsync(
    (uri: vscode.Uri) => this.doRefreshPreview(uri),
    300
  );

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {}

  public openPreview(uri: vscode.Uri) {
    if (this.currentPanel) {
      this.currentPanel.reveal(vscode.ViewColumn.Beside);
      void this.debouncedRefresh(uri);
      return;
    }

    this.currentPanel = vscode.window.createWebviewPanel(
      "gormSqlPreview",
      "GORM SQL Preview",
      vscode.ViewColumn.Beside,
      {
        enableScripts: true,
        retainContextWhenHidden: true,
      }
    );

    this.currentPanel.webview.html = this.getHtmlContent();

    this.currentPanel.onDidDispose(
      () => {
        this.currentPanel = null;
        // Cancel any pending refresh
        this.debouncedRefresh.cancel();
      },
      null,
      this.context.subscriptions
    );

    void this.debouncedRefresh(uri);
  }

  /**
   * Internal refresh method - does the actual LSP request.
   */
  private async doRefreshPreview(uri: vscode.Uri): Promise<void> {
    if (!this.currentPanel) {
      return;
    }

    try {
      const client = this.container.languageServerManager.languageClient;
      if (!client) {
        vscode.window.showErrorMessage("Language server not running");
        return;
      }

      console.log(`[GormSqlPreviewService] Requesting SQL for ${uri.toString()}`);

      const sql = await client.sendRequest("workspace/executeCommand", {
        command: "grails.getGormSql",
        arguments: [uri.toString()],
      });

      this.currentPanel.webview.postMessage({
        command: "updateSql",
        sql: sql ?? "-- No SQL generated.",
      });

      console.log(`[GormSqlPreviewService] SQL updated for ${uri.toString()}`);
    } catch (error) {
      this.errorService.handleError(
        "Failed to generate SQL preview",
        error,
        ErrorSource.LanguageServer,
        ErrorSeverity.Error
      );
    }
  }

  dispose(): void {
    this.debouncedRefresh.cancel();
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }
}

// GrailsTestService.ts
import { debounceAsync } from "../../utils/DebounceUtils";

export class GrailsTestService {
  private controller: vscode.TestController;
  private container: ServiceContainer = ServiceContainer.getInstance();

  // Debounced test discovery with 500ms delay (longer for test discovery)
  private readonly debouncedDiscover = debounceAsync(
    () => this.doDiscoverAllTests(),
    500
  );

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {
    this.controller = vscode.tests.createTestController("grailsTests", "Grails Tests");
    this.context.subscriptions.push(this.controller);

    this.controller.createRunProfile("Run", vscode.TestRunProfileKind.Run, (request, token) => {
      void this.runTests(request, token);
    });

    this.controller.resolveHandler = async item => {
      if (!item) {
        await this.debouncedDiscover();
      }
    };
  }

  /**
   * Internal discovery method - does the actual LSP requests.
   */
  private async doDiscoverAllTests(): Promise<void> {
    try {
      const client = this.container.languageServerManager.languageClient;
      if (!client) {
        return;
      }

      const projects = this.container.projectService.getProjects();
      console.log(`[GrailsTestService] Discovering tests for ${projects.length} projects`);

      // Clear existing test items
      this.controller.items.replace([]);

      for (const project of projects) {
        console.log(`[GrailsTestService] Discovering tests for ${project.name}`);

        interface DiscoveredTest {
          className: string;
          uri: string;
          methods: string[];
        }

        const tests = await client.sendRequest<DiscoveredTest[]>("workspace/executeCommand", {
          command: "grails.discoverTests",
          arguments: [vscode.Uri.file(project.rootPath).toString()],
        });

        console.log(`[GrailsTestService] Found ${tests.length} test classes in ${project.name}`);

        for (const test of tests) {
          const testItem = this.controller.createTestItem(
            test.className,
            test.className,
            vscode.Uri.parse(test.uri)
          );
          testItem.canResolveChildren = true;

          for (const methodName of test.methods) {
            const methodItem = this.controller.createTestItem(
              `${test.className}.${methodName}`,
              methodName,
              vscode.Uri.parse(test.uri)
            );
            testItem.children.add(methodItem);
          }

          this.controller.items.add(testItem);
        }
      }

      console.log(`[GrailsTestService] Test discovery complete`);
    } catch (error) {
      this.errorService.handleError(
        "Test discovery failed",
        error,
        ErrorSource.Testing,
        ErrorSeverity.Error
      );
    }
  }

  dispose(): void {
    this.debouncedDiscover.cancel();
    this.controller.dispose();
  }
}
```

## Subtasks
- [ ] Create DebounceUtils.ts with debounce and debounceAsync
- [ ] Add debounce to DependencyGraphService.refreshGraph()
- [ ] Add debounce to GormSqlPreviewService.refreshPreview()
- [ ] Add debounce to GrailsTestService.discoverAllTests()
- [ ] Add cancel() calls in dispose() methods
- [ ] Test debouncing behavior with rapid calls
- [ ] Test cancellation on dispose

## Tradeoffs
- Slight delay in response (300-500ms)
- More complex request management
- Need to handle cancellation properly

## Testing This Fix
1. Open dependency graph and rapidly switch between projects
2. Verify only one request is sent after rapid switches
3. Open SQL preview and rapidly switch between files
4. Verify only one request is sent after rapid switches
5. Trigger test discovery multiple times rapidly
6. Verify only one discovery runs after rapid triggers
7. Test cancellation on dispose
8. Measure server request rate before and after