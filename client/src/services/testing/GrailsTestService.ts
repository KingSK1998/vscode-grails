import * as vscode from "vscode";
import { ServiceContainer } from "../../core/container/ServiceContainer";

export class GrailsTestService {
  private controller: vscode.TestController;
  private container: ServiceContainer = ServiceContainer.getInstance();

  constructor(private context: vscode.ExtensionContext) {
    this.controller = vscode.tests.createTestController("grailsTests", "Grails Tests");
    this.context.subscriptions.push(this.controller);

    this.controller.createRunProfile("Run", vscode.TestRunProfileKind.Run, (request, token) => {
      void this.runTests(request, token);
    });

    this.controller.resolveHandler = async item => {
      if (!item) {
        await this.discoverAllTests();
      }
    };
  }

  private async discoverAllTests() {
    try {
      const client = this.container.languageServerManager.languageClient;
      if (!client) {
        return;
      }

      const projects = this.container.projectService.getProjects();
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
      console.error("Test discovery failed", error);
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

      // Mock run: In real scenario, we would trigger 'gradle test --tests <className>'
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
