const esbuild = require("esbuild");

const production = process.argv.includes("--production");
const watch = process.argv.includes("--watch");

/**
 * @type {import('esbuild').Plugin}
 */
const esbuildProblemMatcherPlugin = {
  name: "esbuild-problem-matcher",

  setup(build) {
    build.onStart(() => {
      console.log("[watch] build started");
    });
    build.onEnd(result => {
      result.errors.forEach(({ text, location }) => {
        console.error(`✘ [ERROR] ${text}`);
        console.error(`    ${location.file}:${location.line}:${location.column}:`);
      });
      console.log(`[watch] build finished ${result.errors.length > 0 ? " with errors" : ""}`);
    });
  },
};

/**
 * @type {import('esbuild').Plugin}
 */
const copyResourcePlugin = {
  name: "copy-resource",
  setup(build) {
    build.onEnd(() => {
      // Copy resources will be handled by scripts
      console.log("Build completed");
    });
  },
};

async function main() {
  const ctx = await esbuild.context({
    entryPoints: ["client/src/extension.ts"],
    bundle: true,
    format: "cjs",
    minify: production,
    sourcemap: !production,
    sourcesContent: false,
    platform: "node",
    outfile: "dist/extension.js",
    external: ["vscode"],
    logLevel: "silent",
    target: "node20",
    plugins: [esbuildProblemMatcherPlugin, copyResourcePlugin],
    define: {
      "process.env.NODE_ENV": production ? "production" : "development",
    },
  });

  if (watch) {
    await ctx.watch();
  } else {
    await ctx.rebuild();
    await ctx.dispose();
  }
}

main().catch(e => {
  console.error(e);
  process.exit(1);
});
