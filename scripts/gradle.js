const { spawnSync } = require("node:child_process");
const { join } = require("node:path");

const args = process.argv.slice(2);
const options = { cwd: join(__dirname, "..", "server"), stdio: "inherit", windowsHide: true };
let result;

if (process.platform === "win32") {
  // Batch arguments must not contain shell expansion characters. Gradle task
  // names, ordinary paths and test patterns need none.
  if (args.some(arg => /["%!\r\n&|<>^]/.test(arg))) {
    throw new Error("Unsupported shell metacharacter in Gradle argument.");
  }
  result = spawnSync("gradlew.bat", args, { ...options, shell: true });
} else {
  result = spawnSync("./gradlew", args, options);
}

if (result.error) console.error(`[BUILD] ${result.error.message}`);
process.exitCode = result.status ?? 1;
