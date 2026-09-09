const { spawn } = require("node:child_process");
const { existsSync } = require("node:fs");
const { join, resolve } = require("node:path");
const { pathToFileURL, fileURLToPath } = require("node:url");
const { performance } = require("node:perf_hooks");

// Exercise the shipped transport, not an in-process server mock. No project
// sources are written: the smoke document lives only in the LSP open buffer.
const workspace = resolve(process.argv[2] || "server/src/test/resources/test-projects/grails-test-project");
const jar = resolve(process.argv[3] || "client/server/grails-language-server-current-all.jar");
if (!existsSync(jar)) throw new Error(`Missing ${jar}. Run npm run build first.`);
if (!existsSync(workspace)) throw new Error(`Workspace does not exist: ${workspace}`);

const rawJavaHome = (process.env.JAVA_HOME || "").trim().replace(/^"(.*)"$/, "$1").trim();
const java = rawJavaHome
  ? join(rawJavaHome, "bin", process.platform === "win32" ? "java.exe" : "java")
  : "java";
const server = spawn(java, ["-Xmx1g", "-jar", jar], { cwd: resolve("."), windowsHide: true });
const pending = new Map();
const listeners = new Map();
let nextId = 0;
let incoming = Buffer.alloc(0);
let stderr = "";
let finished = false;
let rejectFailure;
const failure = new Promise((_, reject) => { rejectFailure = reject; });
// The fatal race is attached below before any process events can fire.
const timeout = setTimeout(() => rejectFailure(new Error("Server smoke test exceeded 90 seconds")), 90_000);

server.stderr.on("data", data => { stderr = (stderr + data.toString()).slice(-6000); });
server.on("error", rejectFailure);
const exited = new Promise(resolveExit => server.on("exit", code => {
  resolveExit(code);
  if (!finished) rejectFailure(new Error(`Language server exited early: ${code}`));
}));

function send(message) {
  const body = JSON.stringify({ jsonrpc: "2.0", ...message });
  server.stdin.write(`Content-Length: ${Buffer.byteLength(body)}\r\n\r\n${body}`);
}

function request(method, params) {
  const id = ++nextId;
  return new Promise((resolveRequest, reject) => {
    pending.set(id, { resolve: resolveRequest, reject });
    send({ id, method, params });
  });
}

function notification(method, predicate = () => true) {
  return new Promise(resolveNotification => listeners.set(method, { predicate, resolve: resolveNotification }));
}

server.stdout.on("data", data => {
  try {
    incoming = Buffer.concat([incoming, data]);
    while (incoming.length > 0) {
      const headerEnd = incoming.indexOf("\r\n\r\n");
      // Detect application logging before waiting forever for an LSP header.
      if (incoming.length >= 15 && !incoming.subarray(0, 15).toString().toLowerCase().startsWith("content-length:")) {
        throw new Error(`Non-protocol output on stdout: ${incoming.subarray(0, 120).toString()}`);
      }
      if (headerEnd < 0) return;
      const header = incoming.subarray(0, headerEnd).toString();
      const length = /^Content-Length: (\d+)\r?$/im.exec(header);
      if (!length) throw new Error(`Invalid LSP header: ${header}`);
      const end = headerEnd + 4 + Number(length[1]);
      if (incoming.length < end) return;
      const message = JSON.parse(incoming.subarray(headerEnd + 4, end).toString());
      incoming = incoming.subarray(end);
      if (message.method && message.id !== undefined) {
        // Work-done progress registration is a normal server request.
        if (message.method === "window/workDoneProgress/create" || message.method === "client/registerCapability") {
          send({ id: message.id, result: null });
        } else {
          send({ id: message.id, error: { code: -32601, message: "Unsupported smoke-client request" } });
        }
      } else if (message.id !== undefined) {
        const waiter = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) waiter?.reject(new Error(JSON.stringify(message.error)));
        else waiter?.resolve(message.result);
      } else {
        const waiter = listeners.get(message.method);
        if (waiter?.predicate(message.params)) {
          listeners.delete(message.method);
          waiter.resolve(message.params);
        }
      }
    }
  } catch (error) {
    rejectFailure(error);
  }
});

async function smoke() {
  const discovered = notification("grails/allProjects", projects => projects?.length > 0);
  const started = performance.now();
  const initialized = await request("initialize", {
    processId: process.pid,
    rootUri: pathToFileURL(workspace).href,
    workspaceFolders: [{ uri: pathToFileURL(workspace).href, name: "Smoke workspace" }],
    capabilities: { workspace: { workspaceFolders: true }, window: { workDoneProgress: true } },
  });
  if (!initialized?.capabilities?.documentSymbolProvider) throw new Error("Missing document symbol capability");
  console.log(`[SMOKE] Initialize returned in ${Math.round(performance.now() - started)} ms (includes JVM startup)`);
  send({ method: "initialized", params: {} });
  const projects = await discovered;
  console.log(`[SMOKE] Discovered ${projects.length} project(s)`);

  const documentPath = join(workspace, "src", "main", "groovy", "SmokeCheck.groovy");
  const uri = pathToFileURL(documentPath).href;
  const diagnostics = notification("textDocument/publishDiagnostics", params => {
    const published = params.uri.startsWith("file:") ? fileURLToPath(params.uri) : params.uri;
    return resolve(published).toLowerCase() === documentPath.toLowerCase();
  });
  send({ method: "textDocument/didOpen", params: { textDocument: {
    uri, languageId: "groovy", version: 1,
    text: "class SmokeCheck { String greeting() { 'hello' } }",
  } } });
  await diagnostics;
  const symbols = await request("textDocument/documentSymbol", { textDocument: { uri } });
  if (!Array.isArray(symbols) || !symbols.some(symbol => symbol.name === "SmokeCheck")) {
    throw new Error(`Expected SmokeCheck document symbol, received ${JSON.stringify(symbols)}`);
  }
  console.log("[SMOKE] Open document, background diagnostics and document symbols succeeded");
  send({ method: "textDocument/didClose", params: { textDocument: { uri } } });
  await request("shutdown", null);
  finished = true;
  send({ method: "exit" });
  let exitTimeout;
  try {
    const code = await Promise.race([
      exited,
      new Promise((_, reject) => {
        exitTimeout = setTimeout(() => reject(new Error("Server did not exit after shutdown")), 5000);
      }),
    ]);
    if (code !== 0) throw new Error(`Unexpected server exit code: ${code}`);
  } finally {
    clearTimeout(exitTimeout);
  }
}

async function main() {
  try {
    await Promise.race([smoke(), failure]);
    console.log("[SMOKE] PASS");
  } catch (error) {
    console.error(`[SMOKE] FAIL: ${error.message}`);
    if (stderr) console.error(stderr);
    process.exitCode = 1;
  } finally {
    finished = true;
    clearTimeout(timeout);
    server.stdin.destroy();
    // This is only the JVM spawned by this smoke test, never a Gradle daemon.
    server.kill();
  }
}

void main();
