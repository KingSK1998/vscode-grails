const { equal, ok } = require("node:assert/strict");
const { existsSync, readFileSync } = require("node:fs");
const { join } = require("node:path");
const { test } = require("node:test");
const vsce = require("@vscode/vsce");

const root = join(__dirname, "..");

test("extension manifest specifies bundled entry point and bundled server default", () => {
  const manifest = JSON.parse(readFileSync(join(root, "package.json"), "utf8"));
  equal(manifest.main, "./client/out/extension.js");
  ok(existsSync(join(root, manifest.main)));
});

test("vsce package files contain current server JAR and exclude forbidden assets", async () => {
  const files = await vsce.listFiles({ cwd: root });

  // Required runtime files
  ok(files.includes("client/out/extension.js"), "must contain bundled client extension.js");
  ok(files.includes("client/server/grails-language-server-current-all.jar"), "must contain current server JAR");
  ok(files.includes("package.json"), "must contain package.json");
  ok(files.includes("README.md"), "must contain README.md");

  // No obsolete or ambiguous JARs
  const jarFiles = files.filter(f => f.endsWith(".jar"));
  equal(jarFiles.length, 1, `Expected exactly 1 JAR in package; found ${jarFiles.length}: ${jarFiles.join(", ")}`);
  equal(jarFiles[0], "client/server/grails-language-server-current-all.jar");

  // No sourcemaps in production package
  const mapFiles = files.filter(f => f.endsWith(".map"));
  equal(mapFiles.length, 0, `Expected 0 .map files; found: ${mapFiles.join(", ")}`);

  // No typescript source files
  const tsFiles = files.filter(f => f.endsWith(".ts"));
  equal(tsFiles.length, 0, `Expected 0 .ts files; found: ${tsFiles.join(", ")}`);

  // No test files or directories
  const testFiles = files.filter(f => /(^|\/)(test|__tests__)(\/|$)/i.test(f) || /\.test\.[a-z]+$/i.test(f));
  equal(testFiles.length, 0, `Expected 0 test files; found: ${testFiles.join(", ")}`);

  // No documentation or repository metadata
  const docFiles = files.filter(f => /^docs\//.test(f) || /^(AGENTS|CLAUDE|GEMINI|MEMORY|CODING_STANDARDS)\.md$/i.test(f));
  equal(docFiles.length, 0, `Expected 0 internal doc files; found: ${docFiles.join(", ")}`);

  // No log files
  const logFiles = files.filter(f => f.endsWith(".log") || /^logs\//.test(f));
  equal(logFiles.length, 0, `Expected 0 log files; found: ${logFiles.join(", ")}`);

  // No server source or build directory
  const serverSourceFiles = files.filter(f => /^server\/(src|build|gradle)\//.test(f));
  equal(serverSourceFiles.length, 0, `Expected 0 server source/build files; found: ${serverSourceFiles.join(", ")}`);
});

