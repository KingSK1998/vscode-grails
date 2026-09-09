const { equal } = require("node:assert/strict");
const { readFileSync } = require("node:fs");
const { createRequire } = require("node:module");
const { join } = require("node:path");
const { test } = require("node:test");
const { runInNewContext } = require("node:vm");

test("project discovery emitted during language client startup reaches the project event bus", async () => {
  const published = [];
  class LanguageClient {
    handlers = new Map();
    onNotification(method, handler) {
      this.handlers.set(method, handler);
      return { dispose: () => this.handlers.delete(method) };
    }
    async setTrace() {}
    async start() { this.handlers.get("grails/allProjects")?.([{ name: "Example" }]); }
    async stop() {}
  }

  const filename = join(__dirname, "..", "client", "out", "services", "languageServer", "LanguageServerManager.js");
  const localRequire = createRequire(filename);
  const platform = {
    vscode: {
      ProgressLocation: { Notification: 15 },
      window: { withProgress: async (_options, work) => await work({ report() {} }) },
    },
    "vscode-languageclient": { Trace: { Off: 0 } },
    "vscode-languageclient/node": { LanguageClient },
    "../../core/events/EventBus": { EventBus: { getInstance: () => ({ publish: event => published.push(event) }) } },
    "./clientConfig": { getClientOptions: () => ({}) },
    "./serverConfig": { getServerOptions: () => ({}) },
  };
  const exports = {};
  runInNewContext(readFileSync(filename, "utf8"), {
    exports,
    require: name => platform[name] ?? localRequire(name),
    console: { log() {}, warn() {} },
  }, { filename });
  const errors = [];
  const manager = new exports.LanguageServerManager(
    {}, { sync() {}, success() {} }, { handleError: (...args) => errors.push(args) }, { traceLevel: "off" }
  );
  await manager.start();
  equal(errors.length, 0);
  equal(published.length, 1);
  equal(published[0].projects[0].name, "Example");
  manager.dispose();
});
