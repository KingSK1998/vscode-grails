const { equal, throws } = require("node:assert/strict");
const { existsSync, mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } = require("node:fs");
const { tmpdir } = require("node:os");
const { join } = require("node:path");
const { test } = require("node:test");

test("packaging copies only the unambiguous fat JAR to a stable runtime name", () => {
  const root = mkdtempSync(join(tmpdir(), "tmp_rovodev_server_package_"));
  const source = join(root, "build", "libs");
  const destination = join(root, "client", "server");
  try {
    mkdirSync(source, { recursive: true });
    mkdirSync(destination, { recursive: true });
    writeFileSync(join(destination, "grails-language-server-0.1-all.jar"), "obsolete destination fixture");
    writeFileSync(join(source, "grails-language-server-1.0-sources.jar"), "sources".repeat(100));
    writeFileSync(join(source, "grails-language-server-1.0-all.jar"), "runtime");
    const { copyServer } = require("./copy-server");
    copyServer(source, destination);
    equal(readFileSync(join(destination, "grails-language-server-current-all.jar"), "utf8"), "runtime");
    equal(existsSync(join(destination, "grails-language-server-0.1-all.jar")), false);
    writeFileSync(join(source, "grails-language-server-2.0-all.jar"), "ambiguous");
    throws(() => copyServer(source, destination), /exactly one/);
    equal(readFileSync(join(destination, "grails-language-server-current-all.jar"), "utf8"), "runtime");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
