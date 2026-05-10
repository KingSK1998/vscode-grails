import * as path from "path";
import * as vscode from "vscode";
import type { ErrorService } from "../../services/errors/ErrorService";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import type { LanguageServerManager } from "../../services/languageServer/LanguageServerManager";
import { createWebviewStateManager } from "../../services/webview/WebviewStateManager";
import { debounce } from "../../utils/DebounceUtils";
import type { ProjectInfo } from "../models/modelTypes";

interface GraphState {
  currentConfig?: string;
}

export class DependencyGraphService implements vscode.Disposable {
  private currentPanel: vscode.WebviewPanel | null = null;
  private disposed = false;
  private stateManager: ReturnType<typeof createWebviewStateManager<GraphState>>;

  private readonly debouncedRefresh = debounce(
    (project: ProjectInfo) => {
      if (!this.disposed) {
        void this.doRefreshGraph(project);
      }
    },
    300
  );

  constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly errorService: ErrorService,
    private readonly languageServerManager: LanguageServerManager
  ) {
    this.stateManager = createWebviewStateManager<GraphState>(context, "dependencyGraph");
  }

  dispose(): void {
    if (this.disposed) return;
    this.disposed = true;
    this.debouncedRefresh.cancel();
    if (this.currentPanel) {
      this.currentPanel.dispose();
      this.currentPanel = null;
    }
    void this.stateManager.clearState();
  }

  public openGraph(project: ProjectInfo) {
    const column = vscode.window.activeTextEditor
      ? vscode.window.activeTextEditor.viewColumn
      : undefined;

    if (this.currentPanel) {
      this.currentPanel.reveal(column);
      this.debouncedRefresh(project);
      return;
    }

    const savedState = this.stateManager.getState();

    this.currentPanel = vscode.window.createWebviewPanel(
      "grailsDependencyGraph",
      `Dependency Graph: ${project.name}`,
      column ?? vscode.ViewColumn.One,
      this.stateManager.getWebviewOptions([
        vscode.Uri.file(path.join(this.context.extensionPath, "resources")),
      ])
    );

    this.currentPanel.webview.html = this.getHtmlContent(savedState);

    this.currentPanel.onDidDispose(
      () => {
        this.currentPanel = null;
      },
      null,
      this.context.subscriptions
    );

    this.debouncedRefresh(project);
  }

  private async doRefreshGraph(project: ProjectInfo) {
    if (!this.currentPanel || this.disposed) {
      return;
    }

    try {
      const client = this.languageServerManager.languageClient;
      if (!client) {
        vscode.window.showErrorMessage("Language server not running");
        return;
      }

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

  private getD3ScriptUri(): vscode.Uri {
    return vscode.Uri.joinPath(this.context.extensionUri, "resources", "lib", "d3.min.js");
  }

  private getHtmlContent(savedState?: GraphState): string {
    const currentConfig = savedState?.currentConfig ?? "compileClasspath";
    const d3ScriptUri = this.currentPanel!.webview.asWebviewUri(this.getD3ScriptUri()).toString();

    return `<!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Dependency Graph</title>
            <script src="${d3ScriptUri}"></script>
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
                let fullData = {};
                let currentConfig = '${currentConfig}';

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
                    renderGraph();
                });

                document.getElementById('refreshBtn').addEventListener('click', () => {
                    vscode.postMessage({ command: 'refresh' });
                });

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
            </script>
        </body>
        </html>`;
  }
}