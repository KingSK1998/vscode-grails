import { deepStrictEqual, equal, ok, throws } from "node:assert/strict";
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { test } from "node:test";

import type { ExtensionContext } from "vscode";

import { getServerOptions } from "../services/languageServer/serverConfig";
import type { ConfigurationService } from "../services/workspace/ConfigurationService";

interface Manifest {
  contributes: { configuration: { properties: Record<string, { default: unknown }> }[] };
}

void test("installed extensions start their bundled server by default", () => {
  const manifest = JSON.parse(readFileSync(resolve("package.json"), "utf8")) as Manifest;
  const defaults = manifest.contributes.configuration.flatMap(section =>
    Object.entries(section.properties).filter(([key]) => key === "grails.server.developmentMode")
  );
  equal(defaults.length, 1);
  equal(defaults[0][1].default, false);
});

void test("application port has a single default independent of the development LSP port", () => {
  const manifest = JSON.parse(readFileSync(resolve("package.json"), "utf8")) as Manifest;
  const defaults = manifest.contributes.configuration.flatMap(section =>
    Object.entries(section.properties).filter(([key]) => key === "grails.server.port")
  );
  equal(defaults.length, 1);
  equal(defaults[0][1].default, 8080);
});

void test("bundled launch uses the configured Java installation and preserves JVM arguments", () => {
  const root = mkdtempSync(join(tmpdir(), "tmp_rovodev_server_launch_"));
  try {
    const serverDirectory = join(root, "client", "server");
    mkdirSync(serverDirectory, { recursive: true });
    writeFileSync(join(serverDirectory, "grails-language-server-current-all.jar"), "fixture");
    writeFileSync(join(serverDirectory, "grails-language-server-0.1-all.jar"), "obsolete fixture");
    const javaHome = join(root, "JDK with spaces");
    const javaCommand = join(javaHome, "bin", process.platform === "win32" ? "java.exe" : "java");
    mkdirSync(join(javaHome, "bin"), { recursive: true });
    writeFileSync(javaCommand, "fixture");
    const context = {
      asAbsolutePath: (relative: string): string => join(root, relative),
    } as ExtensionContext;
    const config = {
      developmentMode: false,
      javaHome,
      serverJvmArgs: ["-Xmx512m", "-Dexample=value with spaces"],
    } as ConfigurationService;
    const options = getServerOptions(context, config);
    ok(typeof options === "object" && "run" in options);
    ok("command" in options.run && "command" in options.debug);
    equal(options.run.command, javaCommand);
    equal(options.debug.command, javaCommand);
    deepStrictEqual(options.run.args, [
      ...config.serverJvmArgs,
      "-jar",
      join(serverDirectory, "grails-language-server-current-all.jar"),
    ]);

    throws(
      () =>
        getServerOptions(context, {
          ...config,
          javaHome: join(root, "missing JDK"),
        } as ConfigurationService),
      /Java executable not found/
    );

    rmSync(join(serverDirectory, "grails-language-server-current-all.jar"));
    writeFileSync(join(serverDirectory, "grails-language-server-0.2-all.jar"), "ambiguous fixture");
    throws(() => getServerOptions(context, config), /Expected one bundled language server/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
