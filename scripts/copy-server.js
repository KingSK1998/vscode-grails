const fs = require("fs");
const path = require("path");

async function copyServer() {
  const serverDir = path.join(__dirname, "..", "server", "build", "libs");
  const targetDir = path.join(__dirname, "..", "client", "lsp");

  try {
    // Ensure server directories exists
    if (!fs.existsSync(serverDir)) {
      console.error('❌ Server build directory not found. Run `npm run build-server` first.');
      process.exit(1);
    }

    // Find the shadow jar file
    const files = fs.readdirSync(serverDir);
    const jarFile = files.find(file => file.endsWith("-all.jar"));

    if (!jarFile) {
      console.error('❌ No JAR file found matching "*-all.jar" pattern in', serverDir);
      process.exit(1);
    }

    const sourcePath = path.join(serverDir, jarFile);
    const targetPath = path.join(targetDir, "server.jar");

    // Copy the file
    fs.copyFileSync(sourcePath, targetPath);
    console.log(`✅ Copied ${jarFile} to client/lsp/server.jar`, targetPath);
  } catch (error) {
    console.error("❌ Error copying server JAR file:", error);
    process.exit(1);
  }
}

copyServer();