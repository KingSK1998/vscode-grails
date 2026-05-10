import * as vscode from "vscode";
import { debounce } from "../../utils/DebounceUtils";
import type { ErrorService } from "../errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../errors/errorTypes";
import type { LanguageServerManager } from "../languageServer/LanguageServerManager";
import type { ProjectService } from "../workspace/ProjectService";

export class GrailsTestService implements vscode.Disposable {
  private controller: vscode.TestController;
  private disposed = false;

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
    this.controller.dispose();
  }

  private async doDiscoverAllTests(): Promise<void> {
    try {
      const client = this.languageServerManager.languageClient;
      if (!client) {
        return;
      }

      const projects = this.projectService.getProjects();
      this.controller.items.replace([]);

      for (const project of projects) {
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