const { buildSync } = require("esbuild");
const { join } = require("node:path");

const root = join(__dirname, "..");
const isProduction = process.argv.includes("--production");

try {
  buildSync({
    entryPoints: [join(root, "client", "src", "extension.ts")],
    bundle: true,
    platform: "node",
    target: "node20",
    format: "cjs",
    external: ["vscode"],
    outfile: join(root, "client", "out", "extension.js"),
    sourcemap: !isProduction,
    minify: isProduction,
  });
  console.log("[BUILD] Client bundled to client/out/extension.js");
} catch (error) {
  console.error("[BUILD] Client bundling failed:", error);
  process.exitCode = 1;
}

