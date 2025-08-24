import * as fs from "fs";
import * as path from "path";
import * as net from "net";
import { ExtensionContext, workspace } from "vscode";
import { ServerOptions, StreamInfo, TransportKind } from "vscode-languageclient/node";
import { ConfigurationService } from "../workspace/ConfigurationService";

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
  const maxRetries = 5;
  const retryDelay = 5000;

  console.log(`[ServerConfig] Attempting to connect to remote server at ${host}:${port}`);

  return new Promise((resolve, reject) => {
    let attempt = 1;

    const tryConnect = () => {
      console.log(`[ServerConfig] Connection attempt ${attempt}/${maxRetries} to ${host}:${port}`);

      const serverConnection = net.connect({ port, host }, () => {
        console.log(`[ServerConfig] Successfully connected to remote Grails Language Server`);
        resolve({
          writer: serverConnection,
          reader: serverConnection,
        });
      });

      // Set connection timeout
      serverConnection.setTimeout(5000);

      serverConnection.on("timeout", () => {
        console.warn(`[ServerConfig] Connection timeout on attempt ${attempt}`);
        serverConnection.destroy();
        handleRetry();
      });

      serverConnection.on("error", (err: Error) => {
        console.warn(`[ServerConfig] Connection attempt ${attempt} failed: ${err.message}`);
        serverConnection.destroy();
        handleRetry();
      });

      serverConnection.on("close", () => {
        console.log("[ServerConfig] Remote server connection closed");
      });

      const handleRetry = () => {
        attempt++;
        if (attempt <= maxRetries) {
          console.log(`[ServerConfig] Retrying in ${retryDelay / 1000} seconds...`);
          setTimeout(tryConnect, retryDelay);
        } else {
          reject(
            new Error(
              `Failed to connect to remote server after ${maxRetries} attempts. Is the server running on ${host}:${port}?`
            )
          );
        }
      };
    };

    tryConnect();
  });
}
