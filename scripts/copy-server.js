const fs = require("fs");
const path = require("path");

function copyServer() {
  const serverDir = path.join(__dirname, "..", "server", "build", "libs");
  const targetDir = path.join(__dirname, "..", "client", "server");

  try {
    if (!fs.existsSync(serverDir)) {
      console.error("❌ Server build directory not found. Run `npm run build-server` first.");
      process.exit(1);
    }

    const files = fs.readdirSync(serverDir).filter(f => f.endsWith(".jar"));

    if (files.length === 0) {
      console.error("❌ No JAR files found in", serverDir);
      process.exit(1);
    }

    // Pick the largest jar (usually shadow/fat jar)
    const jarFile = files
      .map(f => ({
        name: f,
        size: fs.statSync(path.join(serverDir, f)).size,
      }))
      .sort((a, b) => b.size - a.size)[0];

    const sourcePath = path.join(serverDir, jarFile.name);
    const targetPath = path.join(targetDir, jarFile.name);

    fs.mkdirSync(targetDir, { recursive: true });
    fs.copyFileSync(sourcePath, targetPath);

    console.log(`✅ Copied ${jarFile.name} (${jarFile.size} bytes) to client/server/`);
  } catch (error) {
    console.error("❌ Error copying server JAR file:", error);
    process.exit(1);
  }
}

copyServer();
