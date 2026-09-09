const { existsSync, rmSync, readdirSync } = require("node:fs");
const { join } = require("node:path");
const { spawnSync } = require("node:child_process");

const root = join(__dirname, "..");

console.log("🧹 Cleaning server build artifacts...");
const gradleResult = spawnSync(process.execPath, [join(__dirname, "gradle.js"), "clean"], {
  cwd: root,
  stdio: "inherit",
  windowsHide: true,
});
if (gradleResult.status !== 0) {
  process.exitCode = gradleResult.status ?? 1;
}

console.log("🧹 Cleaning client build outputs...");
const clientOut = join(root, "client", "out");
if (existsSync(clientOut)) {
  rmSync(clientOut, { recursive: true, force: true });
}

const nodeModulesCache = join(root, "node_modules", ".cache");
if (existsSync(nodeModulesCache)) {
  rmSync(nodeModulesCache, { recursive: true, force: true });
}

console.log("🧹 Cleaning distribution outputs and packages...");
const distDir = join(root, "dist");
if (existsSync(distDir)) {
  rmSync(distDir, { recursive: true, force: true });
}

for (const file of readdirSync(root)) {
  if (file.endsWith(".vsix")) {
    rmSync(join(root, file), { force: true });
  }
}

console.log("✅ Clean completed successfully!");

