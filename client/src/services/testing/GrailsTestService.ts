import * as vscode from "vscode";
import type { LanguageClient } from "vscode-languageclient/node";
import { debounce } from "../../utils/DebounceUtils";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { LanguageServerManager } from "../languageServer/LanguageServerManager";
import type { ProjectService } from "../workspace/ProjectService";

interface DiscoveredTest {
  className: string;
  uri: string;
  methods: string[];
}

interface BatchDiscoveredTest extends DiscoveredTest {
  projectPath: string;
}

export class GrailsTestService implements vscode.Disposable {
  private controller: vscode.TestController;
  private disposed = false;
  private cancellationTokenSource: vscode.CancellationTokenSource | null = null;
  private useBatching = true;

  private readonly debouncedDiscover = debounce(() => {
    if (!this.disposed) {
      void this.doDiscoverAllTests();
    }
  }, 500);

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly errorService: ErrorService,
    private readonly languageServerManager: LanguageServerManager,
    private readonly projectService: ProjectService
  ) {
    this.controller = vscode.tests.createTestController("grailsTests", "Grails Tests");
    this.context.subscriptions.push(this.controller);

    this.controller.createRunProfile("Run", vscode.TestRunProfileKind.Run, (request, token) => {
      void this.runTests(request, token);
    });

    this.controller.resolveHandler = item => {
      if (!item) {
        this.debouncedDiscover();
      }
    };
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.debouncedDiscover.cancel();
    this.cancelPendingRequest();
    this.controller.dispose();
  }

  private cancelPendingRequest(): void {
    if (this.cancellationTokenSource) {
      this.cancellationTokenSource.cancel();
      this.cancellationTokenSource.dispose();
      this.cancellationTokenSource = null;
    }
  }

  private async doDiscoverAllTests(): Promise<void> {
    this.cancelPendingRequest();
    this.cancellationTokenSource = new vscode.CancellationTokenSource();

    try {
      const client = this.languageServerManager.languageClient;
      if (!client) {
        return;
      }

      const token = this.cancellationTokenSource.token;
      const projects = this.projectService.getProjects();
      this.controller.items.replace([]);

      if (this.useBatching) {
        const batchSuccess = await this.tryBatchDiscovery(client, projects, token);
        if (!batchSuccess) {
          this.useBatching = false;
          await this.doIndividualDiscovery(client, projects, token);
        }
      } else {
        await this.doIndividualDiscovery(client, projects, token);
      }
    } catch (error) {
      if (this.cancellationTokenSource?.token.isCancellationRequested) {
        return;
      }
      this.errorService.handleError(
        "Test discovery failed",
        error,
        ErrorSource.Testing,
        ErrorSeverity.Error
      );
    } finally {
      if (this.cancellationTokenSource) {
        this.cancellationTokenSource.dispose();
        this.cancellationTokenSource = null;
      }
    }
  }

  private async tryBatchDiscovery(
    client: LanguageClient,
    projects: readonly { rootPath: string }[],
    token: vscode.CancellationToken
  ): Promise<boolean> {
    if (!client || projects.length === 0) {
      return true;
    }

    try {
      const projectPaths = projects.map(p => vscode.Uri.file(p.rootPath).toString());

      const batchResults = await client.sendRequest<BatchDiscoveredTest[]>(
        "workspace/executeCommand",
        {
          command: "grails.discoverTestsBatch",
          arguments: [projectPaths],
        },
        token
      );

      if (token.isCancellationRequested) {
        return false;
      }

      for (const test of batchResults) {
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

      return true;
    } catch {
      console.log("[GrailsTestService] Batch discovery not supported, falling back to individual");
      return false;
    }
  }

  private async doIndividualDiscovery(
    client: LanguageClient,
    projects: readonly { rootPath: string }[],
    token: vscode.CancellationToken
  ): Promise<void> {
    for (const project of projects) {
      if (token.isCancellationRequested) {
        return;
      }

      const tests = await client.sendRequest<DiscoveredTest[]>(
        "workspace/executeCommand",
        {
          command: "grails.discoverTests",
          arguments: [vscode.Uri.file(project.rootPath).toString()],
        },
        token
      );

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
  }

  private async runTests(request: vscode.TestRunRequest, token: vscode.CancellationToken) {
    const run = this.controller.createTestRun(request);
    const queue: vscode.TestItem[] = [];

    if (request.include) {
      request.include.forEach(test => queue.push(test));
    } else {
      this.controller.items.forEach(test => queue.push(test));
    }

    while (queue.length > 0 && !token.isCancellationRequested) {
      const test = queue.pop()!;
      run.started(test);

      await new Promise(resolve => setTimeout(resolve, 500));

      if (Math.random() > 0.1) {
        run.passed(test);
      } else {
        run.failed(test, new vscode.TestMessage("Test failed intentionally in prototype."));
      }
    }

    run.end();
  }
}
