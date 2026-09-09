const { readdirSync } = require("node:fs");
const { join } = require("node:path");

// Run node:test suites in this process, including on hosts that cannot spawn a
// Node process per file. Editor integration tests require a separate VS Code host.
const testDirectory = join(__dirname, "..", "client", "out", "test");
const tests = readdirSync(testDirectory).filter(name => name.endsWith(".unit.test.js")).sort();
if (tests.length === 0) throw new Error("No compiled client unit tests. Run npm run compile first.");
for (const file of tests) require(join(testDirectory, file));
require("./copy-server.test");
require("./language-server-manager.test");
require("./package.test");
