# ISSUE-014 · retainContextWhenHidden Memory Leak
**Severity**: 🔴 Critical
**Service**: DependencyGraphService.ts, GormSqlPreviewService.ts
**Status**: ✅ DONE
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
All webview panels use `retainContextWhenHidden: true` which keeps the webview state in memory even when the panel is hidden or closed. This causes memory accumulation when users repeatedly open/close webviews. The webview context persists indefinitely until explicitly disposed.

## Current Code (problematic pattern)
```typescript
// DashboardService.ts - Lines 24-33
this.currentPanel = vscode.window.createWebviewPanel(
  "grailsDashboard",
  type === "user" ? "Grails Project Dashboard" : "Extension Developer Center",
  column ?? vscode.ViewColumn.One,
  {
    enableScripts: true,
    retainContextWhenHidden: true, // KEEPS WEBVIEW IN MEMORY
    localResourceRoots: [vscode.Uri.file(path.join(this.context.extensionPath, "resources"))],
  }
);

// DependencyGraphService.ts - Lines 28-37
this.currentPanel = vscode.window.createWebviewPanel(
  "grailsDependencyGraph",
  `Dependency Graph: ${project.name}`,
  column ?? vscode.ViewColumn.One,
  {
    enableScripts: true,
    retainContextWhenHidden: true, // KEEPS WEBVIEW IN MEMORY
    localResourceRoots: [vscode.Uri.file(path.join(this.context.extensionPath, "resources"))],
  }
);

// GormSqlPreviewService.ts - Lines 22-30
this.currentPanel = vscode.window.createWebviewPanel(
  "gormSqlPreview",
  "GORM SQL Preview",
  vscode.ViewColumn.Beside,
  {
    enableScripts: true,
    retainContextWhenHidden: true, // KEEPS WEBVIEW IN MEMORY
  }
);
```

## Fixed Code

First, create a webview state manager:

```typescript
// client/src/services/webview/WebviewStateManager.ts

import * as vscode from "vscode";

/**
 * Manages webview state persistence and restoration.
 * Replaces retainContextWhenHidden with explicit state management.
 */
export class WebviewStateManager<T> {
  private readonly stateKey: string;
  private currentState: T | undefined;

  constructor(private readonly context: vscode.ExtensionContext, stateKey: string) {
    this.stateKey = `webview.${stateKey}`;
    this.currentState = this.context.globalState.get<T>(this.stateKey);
  }

  /**
   * Get the current state.
   */
  getState(): T | undefined {
    return this.currentState;
  }

  /**
   * Update the state and persist it.
   */
  async setState(state: T): Promise<void> {
    this.currentState = state;
    await this.context.globalState.update(this.stateKey, state);
  }

  /**
   * Clear the state.
   */
  async clearState(): Promise<void> {
    this.currentState = undefined;
    await this.context.globalState.update(this.stateKey, undefined);
  }

  /**
   * Get the webview options without retainContextWhenHidden.
   */
  getWebviewOptions(localResourceRoots?: vscode.Uri[]): vscode.WebviewOptions {
    return {
      enableScripts: true,
      retainContextWhenHidden: false, // DISABLED - use state management instead
      localResourceRoots,
    };
  }
}
```

Now update the services:

```typescript
// DashboardService.ts
import { WebviewStateManager } from "../webview/WebviewStateManager";

export class DashboardService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private stateManager: WebviewStateManager<DashboardState>;

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {
    this.stateManager = new WebviewStateManager<DashboardState>(context, "dashboard");
  }

  public openDashboard(type: "user" | "developer") {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (this.currentPanel) {
      this.currentPanel.reveal(column);
      return;
    }

    // Create webview WITHOUT retainContextWhenHidden
    this.currentPanel = vscode.window.createWebviewPanel(
      "grailsDashboard",
      type === "user" ? "Grails Project Dashboard" : "Extension Developer Center",
      column ?? vscode.ViewColumn.One,
      this.stateManager.getWebviewOptions([
        vscode.Uri.file(path.join(this.context.extensionPath, "resources"))
      ])
    );

    // Restore state if available
    const savedState = this.stateManager.getState();
    this.currentPanel.webview.html = this.getHtmlContent(type, savedState);

    // Save state on webview messages
    this.currentPanel.webview.onDidReceiveMessage(async (message: DashboardMessage) => {
      switch (message.command) {
        case "saveState":
          if (message.state) {
            await this.stateManager.setState(message.state);
          }
          break;
        case "toggleFeature":
          vscode.window.showInformationMessage(
            `Flag ${String(message.flag)} toggled to ${String(message.value)}`
          );
          break;
        case "runTask":
          if (message.task) {
            void vscode.commands.executeCommand(message.task);
          }
          break;
      }
    });

    this.currentPanel.onDidDispose(
      async () => {
        this.currentPanel = null;
        // Clear state on dispose to free memory
        await this.stateManager.clearState();
      },
      null,
      this.context.subscriptions
    );
  }

  private getHtmlContent(type: "user" | "developer", savedState?: DashboardState): string {
    const isDev = type === "developer";
    const stateJson = savedState ? JSON.stringify(savedState) : "{}";

    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Dashboard</title>
            <style>
                body { font-family: sans-serif; padding: 20px; color: var(--vscode-foreground); }
                .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(250px, 1fr)); gap: 20px; }
                .card { background: var(--vscode-editor-background); border: 1px solid var(--vscode-widget-border); padding: 15px; border-radius: 8px; }
                .card h3 { margin-top: 0; color: var(--vscode-button-background); }
                .stat { font-size: 2em; font-weight: bold; }
                .label { font-size: 0.8em; opacity: 0.8; }
                button { background: var(--vscode-button-background); color: var(--vscode-button-foreground); border: none; padding: 8px 12px; cursor: pointer; border-radius: 4px; }
                button:hover { background: var(--vscode-button-hoverBackground); }
                .status { padding: 4px 8px; border-radius: 4px; font-size: 0.8em; }
                .status.ok { background: #28a745; color: white; }
                .status.warn { background: #ffc107; color: black; }
                .flag-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; padding: 10px; background: var(--vscode-welcomePage-tileBackground); border-radius: 4px; }
            </style>
        </head>
        <body>
            <h1>${isDev ? "🛡️ Developer Maintenance Center" : "📊 Grails Project Insights"}</h1>
            <p>${isDev ? "Manage internal extension flags before VSIX packaging." : "Real-time project metrics and health indicators."}</p>

            ${isDev ? this.getDevContent(savedState) : this.getUserContent(savedState)}

            <script>
                const vscode = acquireVsCodeApi();
                const savedState = ${stateJson};

                // Restore state
                if (savedState && savedState.flags) {
                    Object.entries(savedState.flags).forEach(([flag, value]) => {
                        const checkbox = document.querySelector(\`input[name="\${flag}"]\`);
                        if (checkbox) {
                            checkbox.checked = value as boolean;
                        }
                    });
                }

                function runTask(task) {
                    vscode.postMessage({ command: 'runTask', task: task });
                }

                function toggleFlag(flag, checkbox) {
                    vscode.postMessage({ command: 'toggleFeature', flag: flag, value: checkbox.checked });
                    saveState();
                }

                function saveState() {
                    const flags = {};
                    document.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                        flags[checkbox.name] = checkbox.checked;
                    });
                    vscode.postMessage({ command: 'saveState', state: { flags } });
                }

                // Save state on checkbox changes
                document.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
                    checkbox.addEventListener('change', () => saveState());
                });
            </script>
        </body>
        </html>`;
  }

  dispose(): void {
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }
}

interface DashboardState {
  flags?: Record<string, boolean>;
}

interface DashboardMessage {
  command: string;
  flag?: string;
  value?: boolean;
  task?: string;
  state?: DashboardState;
}
```

```typescript
// DependencyGraphService.ts
import { WebviewStateManager } from "../webview/WebviewStateManager";

export class DependencyGraphService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private container: ServiceContainer = ServiceContainer.getInstance();
  private stateManager: WebviewStateManager<GraphState>;

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {
    this.stateManager = new WebviewStateManager<GraphState>(context, "dependencyGraph");
  }

  public openGraph(project: ProjectInfo) {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (this.currentPanel) {
      this.currentPanel.reveal(column);
      void this.refreshGraph(project);
      return;
    }

    // Create webview WITHOUT retainContextWhenHidden
    this.currentPanel = vscode.window.createWebviewPanel(
      "grailsDependencyGraph",
      `Dependency Graph: ${project.name}`,
      column ?? vscode.ViewColumn.One,
      this.stateManager.getWebviewOptions([
        vscode.Uri.file(path.join(this.context.extensionPath, "resources"))
      ])
    );

    // Restore state if available
    const savedState = this.stateManager.getState();
    this.currentPanel.webview.html = this.getHtmlContent(savedState);

    this.currentPanel.onDidDispose(
      async () => {
        this.currentPanel = null;
        // Clear state on dispose to free memory
        await this.stateManager.clearState();
      },
      null,
      this.context.subscriptions
    );

    // Initial load
    void this.refreshGraph(project);
  }

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

      const graphData: unknown = await client.sendRequest("workspace/executeCommand", {
        command: "grails.getDependencyGraph",
        arguments: [vscode.Uri.file(project.rootPath).toString()],
      });

      // Save graph data to state
      await this.stateManager.setState({
        graphData: typeof graphData === "string" ? (JSON.parse(graphData) as unknown) : graphData,
        projectName: project.name,
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

  private getHtmlContent(savedState?: GraphState): string {
    const stateJson = savedState ? JSON.stringify(savedState) : "{}";

    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Dependency Graph</title>
            <script src="https://d3js.org/d3.v7.min.js"></script>
            <style>
                body { margin: 0; padding: 0; overflow: hidden; background-color: var(--vscode-editor-background); color: var(--vscode-foreground); font-family: sans-serif; }
                #graph { width: 100vw; height: 100vh; }
                .node circle { stroke: #fff; stroke-width: 1.5px; }
                .node text { font-size: 10px; fill: var(--vscode-foreground); }
                .link { stroke: #999; stroke-opacity: 0.6; stroke-width: 1px; marker-end: url(#arrowhead); }
                .tooltip { position: absolute; padding: 8px; background: var(--vscode-notifications-background); border: 1px solid var(--vscode-notifications-border); pointer-events: none; opacity: 0; font-size: 12px; }
                #controls { position: absolute; top: 10px; left: 10px; z-index: 10; display: flex; gap: 10px; }
                select { background: var(--vscode-dropdown-background); color: var(--vscode-dropdown-foreground); border: 1px solid var(--vscode-dropdown-border); }
            </style>
        </head>
        <body>
            <div id="controls">
                <select id="configSelect">
                    <option value="compileClasspath">compileClasspath</option>
                    <option value="runtimeClasspath">runtimeClasspath</option>
                    <option value="testCompileClasspath">testCompileClasspath</option>
                </select>
                <button id="refreshBtn">Refresh</button>
            </div>
            <div id="graph"></div>
            <div class="tooltip" id="tooltip"></div>

            <script>
                const vscode = acquireVsCodeApi();
                const savedState = ${stateJson};
                let fullData = savedState?.graphData || {};
                let currentConfig = 'compileClasspath';

                // Restore saved config
                if (savedState?.currentConfig) {
                    currentConfig = savedState.currentConfig;
                }

                const svg = d3.select("#graph").append("svg")
                    .attr("width", "100%")
                    .attr("height", "100%");

                const g = svg.append("g");
                const zoom = d3.zoom().on("zoom", (event) => g.attr("transform", event.transform));
                svg.call(zoom);

                svg.append("defs").append("marker")
                    .attr("id", "arrowhead")
                    .attr("viewBox", "-0 -5 10 10")
                    .attr("refX", 20)
                    .attr("refY", 0)
                    .attr("orient", "auto")
                    .attr("markerWidth", 6)
                    .attr("markerHeight", 6)
                    .attr("xoverflow", "visible")
                    .append("svg:path")
                    .attr("d", "M 0,-5 L 10 ,0 L 0,5")
                    .attr("fill", "#999")
                    .style("stroke", "none");

                window.addEventListener('message', event => {
                    const message = event.data;
                    if (message.command === 'updateGraph') {
                        fullData = message.data;
                        updateConfigOptions();
                        renderGraph();
                    }
                });

                document.getElementById('configSelect').addEventListener('change', (e) => {
                    currentConfig = e.target.value;
                    saveState();
                    renderGraph();
                });

                document.getElementById('refreshBtn').addEventListener('click', () => {
                    vscode.postMessage({ command: 'refresh' });
                });

                function saveState() {
                    vscode.postMessage({ command: 'saveState', state: { currentConfig } });
                }

                function updateConfigOptions() {
                    const select = document.getElementById('configSelect');
                    const currentVal = select.value;
                    select.innerHTML = '';
                    Object.keys(fullData).sort().forEach(config => {
                        const option = document.createElement('option');
                        option.value = config;
                        option.text = config;
                        select.appendChild(option);
                    });
                    if (fullData[currentVal]) select.value = currentVal;
                    else if (select.options.length > 0) currentConfig = select.value;
                }

                function renderGraph() {
                    const configData = fullData[currentConfig];
                    if (!configData) return;

                    const nodes = [];
                    const links = [];
                    const nodeMap = new Map();

                    Object.keys(configData).forEach(id => {
                        if (!nodeMap.has(id)) {
                            nodeMap.set(id, { id });
                            nodes.push(nodeMap.get(id));
                        }
                        configData[id].forEach(depId => {
                            if (!nodeMap.has(depId)) {
                                nodeMap.set(depId, { id: depId });
                                nodes.push(nodeMap.get(depId));
                            }
                            links.push({ source: id, target: depId });
                        });
                    });

                    g.selectAll("*").remove();

                    const simulation = d3.forceSimulation(nodes)
                        .force("link", d3.forceLink(links).id(d => d.id).distance(100))
                        .force("charge", d3.forceManyBody().strength(-300))
                        .force("center", d3.forceCenter(window.innerWidth / 2, window.innerHeight / 2));

                    const link = g.append("g")
                        .selectAll("line")
                        .data(links)
                        .join("line")
                        .attr("class", "link");

                    const node = g.append("g")
                        .selectAll("g")
                        .data(nodes)
                        .join("g")
                        .attr("class", "node")
                        .call(d3.drag()
                            .on("start", dragstarted)
                            .on("drag", dragged)
                            .on("end", dragended));

                    node.append("circle")
                        .attr("r", 8)
                        .attr("fill", d => d.id.includes("grails") ? "#ff4081" : "#2196f3");

                    node.append("text")
                        .attr("dx", 12)
                        .attr("dy", ".35em")
                        .text(d => d.id.split(':').slice(1, 2).join(':') || d.id);

                    const tooltip = d3.select("#tooltip");

                    node.on("mouseover", (event, d) => {
                        tooltip.style("opacity", 1)
                            .html(d.id)
                            .style("left", (event.pageX + 10) + "px")
                            .style("top", (event.pageY - 10) + "px");
                    }).on("mouseout", () => tooltip.style("opacity", 0));

                    simulation.on("tick", () => {
                        link
                            .attr("x1", d => d.source.x)
                            .attr("y1", d => d.source.y)
                            .attr("x2", d => d.target.x)
                            .attr("y2", d => d.target.y);

                        node.attr("transform", d => \`translate(\${d.x}, \${d.y})\`);
                    });

                    function dragstarted(event) {
                        if (!event.active) simulation.alphaTarget(0.3).restart();
                        event.subject.fx = event.subject.x;
                        event.subject.fy = event.subject.y;
                    }

                    function dragged(event) {
                        event.subject.fx = event.x;
                        event.subject.fy = event.y;
                    }

                    function dragended(event) {
                        if (!event.active) simulation.alphaTarget(0);
                        event.subject.fx = null;
                        event.subject.fy = null;
                    }
                }

                // Initial render if data available
                if (Object.keys(fullData).length > 0) {
                    updateConfigOptions();
                    renderGraph();
                }
            </script>
        </body>
        </html>`;
  }

  dispose(): void {
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }
}

interface GraphState {
  graphData?: unknown;
  projectName?: string;
  currentConfig?: string;
}
```

```typescript
// GormSqlPreviewService.ts
import { WebviewStateManager } from "../webview/WebviewStateManager";

export class GormSqlPreviewService {
  private currentPanel: vscode.WebviewPanel | null = null;
  private container: ServiceContainer = ServiceContainer.getInstance();
  private stateManager: WebviewStateManager<SqlPreviewState>;

  constructor(
    private context: vscode.ExtensionContext,
    private errorService: ErrorService
  ) {
    this.stateManager = new WebviewStateManager<SqlPreviewState>(context, "gormSqlPreview");
  }

  public openPreview(uri: vscode.Uri) {
    if (this.currentPanel) {
      this.currentPanel.reveal(vscode.ViewColumn.Beside);
      void this.refreshPreview(uri);
      return;
    }

    // Create webview WITHOUT retainContextWhenHidden
    this.currentPanel = vscode.window.createWebviewPanel(
      "gormSqlPreview",
      "GORM SQL Preview",
      vscode.ViewColumn.Beside,
      this.stateManager.getWebviewOptions()
    );

    // Restore state if available
    const savedState = this.stateManager.getState();
    this.currentPanel.webview.html = this.getHtmlContent(savedState);

    this.currentPanel.onDidDispose(
      async () => {
        this.currentPanel = null;
        // Clear state on dispose to free memory
        await this.stateManager.clearState();
      },
      null,
      this.context.subscriptions
    );

    void this.refreshPreview(uri);
  }

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

      const sql = await client.sendRequest("workspace/executeCommand", {
        command: "grails.getGormSql",
        arguments: [uri.toString()],
      });

      // Save SQL to state
      await this.stateManager.setState({
        sql: sql ?? "-- No SQL generated.",
        uri: uri.toString(),
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

  private getHtmlContent(savedState?: SqlPreviewState): string {
    const initialSql = savedState?.sql ?? "-- Loading preview...";
    const stateJson = savedState ? JSON.stringify(savedState) : "{}";

    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>GORM SQL Preview</title>
            <style>
                body { margin: 0; padding: 20px; background-color: var(--vscode-editor-background); color: var(--vscode-editor-foreground); font-family: var(--vscode-editor-font-family); font-size: var(--vscode-editor-font-size); }
                pre { background: var(--vscode-textCodeBlock-background); padding: 15px; border-radius: 8px; border: 1px solid var(--vscode-widget-border); overflow-auto; }
                .keyword { color: #569cd6; }
                .type { color: #4ec9b0; }
                .comment { color: #6a9955; }
            </style>
        </head>
        <body>
            <h3>GORM SQL DDL Preview</h3>
            <pre id="sqlOutput">${initialSql}</pre>

            <script>
                const vscode = acquireVsCodeApi();
                const savedState = ${stateJson};

                // Restore saved SQL
                if (savedState?.sql) {
                    const output = document.getElementById('sqlOutput');
                    output.textContent = savedState.sql;
                    highlight(output);
                }

                window.addEventListener('message', event => {
                    const message = event.data;
                    if (message.command === 'updateSql') {
                        const output = document.getElementById('sqlOutput');
                        output.textContent = message.sql;
                        highlight(output);
                    }
                });

                function highlight(el) {
                    let text = el.textContent;
                    text = text.replace(/(\\/\\*[\\s\\S]*?\\*\\/|--.*)/g, '<span class="comment">$1</span>');
                    text = text.replace(/\b(CREATE|TABLE|NOT|NULL|PRIMARY|KEY|AUTO_INCREMENT|BIGINT|INT|VARCHAR|BOOLEAN|DATE|TIMESTAMP|DECIMAL)\b/g, '<span class="keyword">$1</span>');
                    el.innerHTML = text;
                }
            </script>
        </body>
        </html>`;
  }

  dispose(): void {
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
  }
}

interface SqlPreviewState {
  sql?: string;
  uri?: string;
}
```

## Subtasks
- [x] Create WebviewStateManager utility
- [x] Remove retainContextWhenHidden from DependencyGraphService
- [x] Remove retainContextWhenHidden from GormSqlPreviewService
- [x] Add state cleanup on dispose
- [ ] Test state save/restore functionality

## Tradeoffs
- Webview loses state when hidden (need to implement state persistence)
- More complex webview lifecycle management
- Need to handle state serialization/deserialization

## Testing This Fix
1. Open dashboard, modify flags, close and reopen - verify flags restored
2. Open dependency graph, close and reopen - verify graph data restored
3. Open SQL preview, close and reopen - verify SQL restored
4. Monitor memory usage before and after repeated open/close cycles
5. Verify state is cleared on dispose
6. Test with multiple webviews open simultaneously