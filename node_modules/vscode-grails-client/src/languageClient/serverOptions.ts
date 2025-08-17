import * as path from "path";
import * as net from "net";
import { ExtensionContext, workspace } from "vscode";
import { ServerOptions, StreamInfo, TransportKind } from "vscode-languageclient/node";

interface DebugOptions {
  port: number;
  suspend: boolean;
}

/**
 * Creates server options for local development
 */
export function getServerOptions(context: ExtensionContext): ServerOptions {
  const isDevMode =
    process.env.NODE_ENV === "development" || workspace.getConfiguration("grails").get("server.developmentMode", false);

  if (isDevMode) {
    console.log("Running in development mode - attempting remote connection");
    return async () => connectToRemoteServer();
  }

  console.log("Running in production mode - using local server");
  return getLocalServerOptions(context);
}

/**
 * Creates server options for local development with JAR file
 */
function getLocalServerOptions(context: ExtensionContext): ServerOptions {
  const serverModule = getServerJarPath(context);
  const debugOptions = getDebugConfiguration();

  return {
    run: {
      command: "java",
      args: ["-jar", serverModule],
      transport: TransportKind.stdio,
      options: {
        env: process.env,
      },
    },
    debug: {
      command: "java",
      args: ["-jar", ...getJavaDebugArgs(debugOptions), serverModule],
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
  return context.asAbsolutePath(path.join("server", "grails-language-server-1.0-all.jar"));
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
  return [`-agentlib:jdwp=transport=dt_socket,server=y,suspend=${suspend},address=${options.port},quiet=y`];
}

/**
 * Establishes connection to remote server
 */
function connectToRemoteServer(): Promise<StreamInfo> {
  const port = workspace.getConfiguration("grails").get<number>("server.port", 5007);
  const host = workspace.getConfiguration("grails").get<string>("server.host", "localhost");
  const maxRetries = 5;
  const retryDelay = 10000;

  return new Promise((resolve, reject) => {
    let attempt = 1;

    const tryConnect = () => {
      const serverConnection = net.connect({ port }, () => {
        console.log(`Connected to remote Grails Language Server on port ${port}`);
        resolve({
          writer: serverConnection,
          reader: serverConnection,
        });
      });

      serverConnection.on("error", (err) => {
        console.warn(`Connection attempt ${attempt} failed: ${err.message}`);
        serverConnection.destroy();

        if (attempt < maxRetries) {
          console.log(`Retrying in ${retryDelay / 1000} seconds...`);
          setTimeout(tryConnect, retryDelay);
        } else {
          reject(new Error(`Failed to connect after ${maxRetries} attempts: ${err.message}`));
        }
      });

      serverConnection.on("close", () => {
        console.log("Remote server connection closed");
      });
    };

    tryConnect();
  });
}
