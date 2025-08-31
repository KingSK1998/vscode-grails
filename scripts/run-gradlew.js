#!/usr/bin/env node
/**
 * Copies the latest built Grails language server JAR
 * into the client/server directory for packaging.
 */
const fs = require("fs");
const path = require("path");

// Directories
const serverBuildDir = path.join(__dirname, "..", "server", "build", "libs");
const clientServerDir = path.join(__dirname, "..", "client", "server");

// Ensure target directory exists
fs.mkdirSync(clientServerDir, { recursive: true });

// Find all .jar files in build/libs
if (!fs.existsSync(serverBuildDir)) {
  console.error("❌ server/build/libs does not exist. Did you run gradlew build?");
  process.exit(1);
}

const jarFiles = fs.readdirSync(serverBuildDir).filter(f => f.endsWith(".jar"));

if (jarFiles.length === 0) {
  console.error("❌ No JAR files found in server/build/libs/");
  process.exit(1);
}

// Pick the largest JAR (usually the shadow/fat JAR)
const jarFile = jarFiles
  .map(f => ({
    name: f,
    size: fs.statSync(path.join(serverBuildDir, f)).size,
  }))
  .sort((a, b) => b.size - a.size)[0].name;

const srcPath = path.join(serverBuildDir, jarFile);
const destPath = path.join(clientServerDir, jarFile);

// Copy JAR
fs.copyFileSync(srcPath, destPath);

console.log(`✅ Copied server JAR: ${jarFile}`);
console.log(`📦 Location: ${destPath}`);
