import { existsSync, readdirSync } from "node:fs";
import { connect } from "node:net";
import { join } from "node:path";
import type { ExtensionContext } from "vscode";
import type { ServerOptions, StreamInfo } from "vscode-languageclient/node";
import type { ConfigurationService } from "../workspace/ConfigurationService";

interface DebugOptions {
  port: number;
  suspend: boolean;
}

const CONNECTION_TIMEOUT_MS = 10_000;
const BUNDLED_SERVER_JAR = "grails-language-server-current-all.jar";

/**
 * Creates server options for local development
 */
export function getServerOptions(
  context: ExtensionContext,
  config: ConfigurationService
): ServerOptions {
  // Use remote server in development mode
  const isDev = config.developmentMode;

  if (isDev) {
    return async () => connectToRemoteServer(config);
  }

  return getLocalServerOptions(context, config);
}

/**
 * Creates server options for local development with JAR file
 */
function getLocalServerOptions(
  context: ExtensionContext,
  config: ConfigurationService
): ServerOptions {
  const serverJar = getServerJarPath(context);
  const javaCommand = getJavaCommand(config);
  const jvmArgs = config.serverJvmArgs;
  const debugOptions = getDebugConfiguration();

  return {
    run: {
      command: javaCommand,
      args: [...jvmArgs, "-jar", serverJar],
      options: {
        env: process.env,
      },
    },
    debug: {
      command: javaCommand,
      args: [...jvmArgs, ...getJavaDebugArgs(debugOptions), "-jar", serverJar],
      options: {
        env: process.env,
      },
    },
  };
}

/**
 * Gets the path to the language server JAR file
 */
function getServerJarPath(context: ExtensionContext): string {
  const serverDir = context.asAbsolutePath(join("client", "server"));

  if (!existsSync(serverDir)) {
    throw new Error(`Server directory not found: ${serverDir}`);
  }

  const bundledJar = join(serverDir, BUNDLED_SERVER_JAR);
  if (existsSync(bundledJar)) return bundledJar;

  const files = readdirSync(serverDir);
  const jars = files.filter(
    file => file.startsWith("grails-language-server-") && file.endsWith("-all.jar")
  );

  if (jars.length !== 1) {
    throw new Error(
      `Expected one bundled language server in ${serverDir}, found ${jars.length}. Rebuild with npm run copy-server or reinstall the extension.`
    );
  }

  return join(serverDir, jars[0]);
}

function getJavaCommand(config: ConfigurationService): string {
  const javaHome = config.javaHome.trim() || process.env.JAVA_HOME?.trim();
  if (!javaHome) return "java";

  const command = join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
  if (!existsSync(command)) {
    throw new Error(`Java executable not found at ${command}. Check grails.javaHome or JAVA_HOME.`);
  }
  return command;
}

/**
 * Gets debug configuration for Java
 */
function getDebugConfiguration(): DebugOptions {
  return {
    port: 5005,
    suspend: false,
  };
}

/**
 * Generates Java debug arguments
 */
function getJavaDebugArgs(options: DebugOptions): string[] {
  const suspend = options.suspend ? "y" : "n";
  return [
    `-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspend},address=${options.port},quiet=y`,
  ];
}

/**
 * Establishes connection to remote server
 */
function connectToRemoteServer(config: ConfigurationService): Promise<StreamInfo> {
  const port = config.languageServerDevelopmentPort;
  const host = "localhost";

  return new Promise((resolve, reject) => {
    const serverConnection = connect({ port, host }, () => {
      // The timeout only bounds connection establishment, not an idle LSP session.
      serverConnection.setTimeout(0);
      resolve({
        writer: serverConnection,
        reader: serverConnection,
      });
    });

    serverConnection.setTimeout(CONNECTION_TIMEOUT_MS);

    serverConnection.once("timeout", () => {
      serverConnection.destroy(
        new Error(`Language server connection timed out at ${host}:${port}`)
      );
    });

    serverConnection.once("error", (err: Error) => {
      serverConnection.destroy();
      reject(err);
    });
  });
}
