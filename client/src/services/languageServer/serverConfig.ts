import * as fs from "fs";
import * as net from "net";
import * as path from "path";
import type { ExtensionContext } from "vscode";
import { TransportKind, type ServerOptions, type StreamInfo } from "vscode-languageclient/node";
import type { ConfigurationService } from "../workspace/ConfigurationService";

interface DebugOptions {
  port: number;
  suspend: boolean;
}

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
  const jvmArgs = config.serverJvmArgs;
  const debugOptions = getDebugConfiguration();

  return {
    run: {
      command: "java",
      args: [...jvmArgs, "-jar", serverJar],
      transport: TransportKind.stdio,
      options: {
        env: process.env,
      },
    },
    debug: {
      command: "java",
      args: [...jvmArgs, ...getJavaDebugArgs(debugOptions), "-jar", serverJar],
      transport: TransportKind.stdio,
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
  const serverDir = context.asAbsolutePath(path.join("client", "server"));

  if (!fs.existsSync(serverDir)) {
    throw new Error(`Server directory not found: ${serverDir}`);
  }

  const files = fs.readdirSync(serverDir);
  const jarFile = files.find(
    file => file.startsWith("grails-language-server-") && file.endsWith("-all.jar")
  );

  if (!jarFile) {
    throw new Error(
      `No JAR file found matching "grails-language-server-*-all.jar" in ${serverDir}`
    );
  }

  const jarPath = path.join(serverDir, jarFile);
  console.log(`[ServerConfig] Found language server JAR: ${jarPath}`);
  return jarPath;
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
  const port = config.serverPort;
  const host = "localhost";

  console.log(`[ServerConfig] Attempting to connect to remote server at ${host}:${port}`);

  return new Promise((resolve, reject) => {
    const serverConnection = net.connect({ port, host }, () => {
      console.log(`[ServerConfig] Successfully connected to remote Grails Language Server`);
      resolve({
        writer: serverConnection,
        reader: serverConnection,
      });
    });

    // Disable timeout (or increase it)
    serverConnection.setTimeout(0);

    serverConnection.once("timeout", () => {
      console.warn(`[ServerConfig] Connection timeout`);
      // Let error handler deal with retry if desired
    });

    serverConnection.once("error", (err: Error) => {
      console.warn(`[ServerConfig] Connection error: ${err.message}`);
      // Let caller handle errors or add retry logic here
      reject(err);
    });

    serverConnection.once("close", () => {
      console.log("[ServerConfig] Remote server connection closed");
      // Handle cleanup or reconnect as appropriate
    });
  });
}
