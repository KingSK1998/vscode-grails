const { copyFileSync, mkdirSync, readdirSync, unlinkSync } = require("node:fs");
const { join } = require("node:path");

// A stable destination prevents directory order from selecting an older build.
function copyServer(
  sourceDirectory = join(__dirname, "..", "server", "build", "libs"),
  destinationDirectory = join(__dirname, "..", "client", "server")
) {
  const jars = readdirSync(sourceDirectory).filter(name =>
    /^grails-language-server-.+-all\.jar$/.test(name)
  );
  if (jars.length !== 1) {
    throw new Error(`Expected exactly one language server fat JAR in ${sourceDirectory}; found ${jars.length}. Remove obsolete build outputs and run npm run build:server.`);
  }

  mkdirSync(destinationDirectory, { recursive: true });
  const destination = join(destinationDirectory, "grails-language-server-current-all.jar");
  copyFileSync(join(sourceDirectory, jars[0]), destination);

  // Prune obsolete JARs from destination to eliminate selection ambiguity
  for (const name of readdirSync(destinationDirectory)) {
    if (name.startsWith("grails-language-server-") && name.endsWith(".jar") && name !== "grails-language-server-current-all.jar") {
      unlinkSync(join(destinationDirectory, name));
    }
  }

  return destination;
}

module.exports = { copyServer };

if (require.main === module) {
  try {
    console.log(`[BUILD] Language server copied to ${copyServer()}`);
  } catch (error) {
    console.error(`[BUILD] ${error.message}`);
    process.exitCode = 1;
  }
}
