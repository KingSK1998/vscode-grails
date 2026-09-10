#!/usr/bin/env node
/**
 * R1-05 Benchmark Runner
 * Runs the reproducible performance baseline test suite, validates scorecard budgets,
 * and prints an evidence summary table.
 *
 * Usage: node scripts/run-benchmarks.js
 */

const { execSync } = require("node:child_process");
const { existsSync, readFileSync, mkdirSync, writeFileSync } = require("node:fs");
const { resolve, join } = require("node:path");
const os = require("node:os");

const ROOT = resolve(__dirname, "..");
const SERVER_REPORT = join(ROOT, "server", "build", "reports", "performance-baseline.json");
const ROOT_REPORT = join(ROOT, "reports", "performance-baseline.json");

function main() {
  console.log("===============================================================================");
  console.log(" R1-05: Running Performance and Resource Baselines Benchmark");
  console.log("===============================================================================");
  console.log(`Platform: ${os.platform()} ${os.release()} (${os.arch()})`);
  console.log(`CPUs:     ${os.cpus().length} cores (${os.cpus()[0]?.model || "unknown"})`);
  console.log(`Memory:   ${Math.round(os.totalmem() / (1024 * 1024 * 1024))} GB RAM`);
  console.log(`Node:     ${process.version}`);
  console.log("-------------------------------------------------------------------------------");
  console.log("Executing PerformanceBaselineSpec via Gradle...");

  try {
    execSync(
      "node scripts/gradle.js test --tests kingsk.grails.lsp.perf.PerformanceBaselineSpec --rerun-tasks --console=plain",
      { cwd: ROOT, stdio: "inherit" }
    );
  } catch (error) {
    console.error("\n[BENCHMARK] Benchmark test suite execution failed.");
    process.exit(1);
  }

  if (!existsSync(SERVER_REPORT)) {
    console.error(`\n[BENCHMARK] Missing report file at: ${SERVER_REPORT}`);
    process.exit(1);
  }

  const raw = readFileSync(SERVER_REPORT, "utf-8");
  const report = JSON.parse(raw);

  // Ensure root reports directory has copy
  const reportsDir = join(ROOT, "reports");
  if (!existsSync(reportsDir)) mkdirSync(reportsDir, { recursive: true });
  writeFileSync(ROOT_REPORT, raw, "utf-8");

  console.log("\n===============================================================================");
  console.log(" BENCHMARK SCORECARD SUMMARY (R1-05)");
  console.log("===============================================================================");

  const benchmarks = report.benchmarks || {};
  const comparisons = report.scorecardComparisons || {};

  console.log("\n1. LATENCY METRICS");
  console.log("-------------------------------------------------------------------------------");
  console.log(
    "Metric".padEnd(28) +
    "Samples".padStart(9) +
    "p50 (ms)".padStart(11) +
    "p95 (ms)".padStart(11) +
    "Mean (ms)".padStart(11) +
    "Max (ms)".padStart(10)
  );
  console.log("-".repeat(80));

  for (const [key, val] of Object.entries(benchmarks)) {
    if (val.sampleCount !== undefined) {
      console.log(
        key.padEnd(28) +
        String(val.sampleCount).padStart(9) +
        Number(val.p50Ms).toFixed(2).padStart(11) +
        Number(val.p95Ms).toFixed(2).padStart(11) +
        Number(val.meanMs).toFixed(2).padStart(11) +
        Number(val.maxMs).toFixed(2).padStart(10)
      );
    }
  }

  console.log("\n2. RESOURCE & LIFECYCLE METRICS");
  console.log("-------------------------------------------------------------------------------");
  const mem = benchmarks.memoryStats || {};
  console.log(`Initial Heap:     ${(mem.initialHeapBytes / (1024 * 1024)).toFixed(1)} MiB`);
  console.log(`Peak Heap:        ${(mem.peakHeapBytes / (1024 * 1024)).toFixed(1)} MiB`);
  console.log(`Settled Heap:     ${mem.settledHeapMiB} MiB`);
  console.log(`Heap Growth:      ${mem.heapGrowthRatio}x (after 25 lifecycle cycles)`);
  console.log(`ClassLoader Detach: ${mem.classloaderDetached ? "PASS (DetachedASTAccessor verified)" : "FAIL"}`);

  const limits = benchmarks.numericLimits || {};
  console.log(`Max Auto File:    ${(limits.maxAutomaticDocumentBytes / (1024 * 1024)).toFixed(0)} MiB (oversized skipped = ${limits.largeFileAutomaticCompileSkipped})`);
  console.log(`Max Queue Bytes:  ${limits.maxPendingBytes / 1024} KiB (maxPendingDocs = ${limits.maxPendingDocuments})`);
  console.log(`Tracked Files:    ${limits.maxTrackedFiles} max files, ${limits.maxFqcnEntries} max FQCNs`);

  console.log("\n3. ROADMAP SCORECARD TARGETS");
  console.log("-------------------------------------------------------------------------------");
  let allPass = true;
  for (const [name, check] of Object.entries(comparisons)) {
    const statusStr = check.status === "PASS" ? "PASS" : "FAIL";
    if (check.status !== "PASS") allPass = false;
    const obs = check.observedP95Ms !== undefined ? ` (observed p95: ${check.observedP95Ms} ms)` : "";
    console.log(`[${statusStr}] ${name.padEnd(30)} -> Target: ${check.target}${obs}`);
  }

  console.log("===============================================================================");
  if (allPass) {
    console.log(" ALL R1-05 ACCEPTANCE TARGETS PASSED");
    console.log(` Evidence saved to: reports/performance-baseline.json`);
    console.log("===============================================================================");
    process.exit(0);
  } else {
    console.error(" SOME ROADMAP SCORECARD TARGETS FAILED");
    process.exit(1);
  }
}

main();

