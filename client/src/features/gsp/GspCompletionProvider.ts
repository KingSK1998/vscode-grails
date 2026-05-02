import * as fs from "fs";
import * as path from "path";
import * as vscode from "vscode";

/**
 * Provides IntelliSense for Grails GSP files.
 * Supports HTML/CSS/JS (via VS Code), I18n, Path Intellisense, TagLibs, and Scriptlets.
 */
export class GspCompletionProvider implements vscode.CompletionItemProvider {
  async provideCompletionItems(
    document: vscode.TextDocument,
    position: vscode.Position,
    _token: vscode.CancellationToken,
    _context: vscode.CompletionContext
  ): Promise<vscode.CompletionItem[] | vscode.CompletionList> {
    const linePrefix = document.lineAt(position).text.substring(0, position.character);
    const completions: vscode.CompletionItem[] = [];

    // 1. TagLibs Custom Completions (g: / asset: / etc)
    this.addTagLibCompletions(linePrefix, completions, document);

    // 2. I18N properties
    this.addI18nCompletions(linePrefix, document, completions);

    // 3. Path Intellisense for assets / web
    this.addPathCompletions(linePrefix, document, completions);

    // 4. Fallback to VS Code's built-in HTML/CSS/JS capability
    const htmlCompletions = await this.getHtmlCompletions(document, position);
    if (htmlCompletions?.items) {
      // Avoid duplicating items if they already exist
      const existingLabels = new Set(
        completions.map(c => (typeof c.label === "string" ? c.label : c.label.label))
      );

      for (const item of htmlCompletions.items) {
        const itemLabel = typeof item.label === "string" ? item.label : item.label.label;
        if (!existingLabels.has(itemLabel)) {
          completions.push(item);
        }
      }
    }

    return new vscode.CompletionList(completions, false);
  }

  private addTagLibCompletions(
    prefix: string,
    completions: vscode.CompletionItem[],
    document: vscode.TextDocument
  ) {
    // Basic Taglib completions specifically for `<g:` or `<asset:`
    const gspTags = [
      {
        tag: "g:each",
        detail: "Iterate over a collection",
        insert: 'g:each in="${$1}" var="${2:item}">\n\t$0\n</g:each>',
      },
      { tag: "g:if", detail: "Conditional if", insert: 'g:if test="${$1}">\n\t$0\n</g:if>' },
      { tag: "g:else", detail: "Conditional else", insert: "g:else>\n\t$0\n</g:else>" },
      {
        tag: "g:elseif",
        detail: "Conditional elseif",
        insert: 'g:elseif test="${$1}">\n\t$0\n</g:elseif>',
      },
      {
        tag: "g:set",
        detail: "Sets a variable",
        insert: 'g:set var="${1:name}" value="${$2}" />$0',
      },
      {
        tag: "g:render",
        detail: "Renders a template",
        insert: 'g:render template="${1:templateName}" model="[${2:key}: ${3:value}]" />$0',
      },
      {
        tag: "g:message",
        detail: "I18n message",
        insert: 'g:message code="${1:code.key}" default="${2:default}" />$0',
      },
      {
        tag: "g:form",
        detail: "GSP Form",
        insert: 'g:form controller="${1:controller}" action="${2:action}">\n\t$0\n</g:form>',
      },
      {
        tag: "g:textField",
        detail: "Text Field",
        insert: 'g:textField name="${1:name}" value="${$2}" />$0',
      },
      {
        tag: "g:submitButton",
        detail: "Submit Button",
        insert: 'g:submitButton name="${1:submit}" value="${2:Submit}" />$0',
      },
      {
        tag: "g:link",
        detail: "Create a link",
        insert: 'g:link controller="${1:controller}" action="${2:action}">$3</g:link>$0',
      },
      {
        tag: "asset:stylesheet",
        detail: "Asset pipeline stylesheet",
        insert: 'asset:stylesheet src="${1:application.css}" />$0',
      },
      {
        tag: "asset:javascript",
        detail: "Asset pipeline javascript",
        insert: 'asset:javascript src="${1:application.js}" />$0',
      },
      {
        tag: "asset:image",
        detail: "Asset pipeline image",
        insert: 'asset:image src="${1:logo.png}" alt="${2:Logo}" />$0',
      },
    ];

    if (prefix.match(/<[gaa-z]*:?$/)) {
      gspTags.forEach(t => {
        const item = new vscode.CompletionItem(t.tag, vscode.CompletionItemKind.Property);
        item.detail = t.detail;
        item.insertText = new vscode.SnippetString(t.insert);
        completions.push(item);
      });

      // Scan dynamic custom taglibs
      const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
      if (workspaceFolder) {
        const taglibDir = path.join(workspaceFolder.uri.fsPath, "grails-app", "taglib");
        if (fs.existsSync(taglibDir)) {
          this.recursivelyExtractCustomTags(taglibDir, completions);
        }
      }
    }
  }

  private recursivelyExtractCustomTags(dir: string, completions: vscode.CompletionItem[]) {
    try {
      const files = fs.readdirSync(dir);
      for (const f of files) {
        const fullPath = path.join(dir, f);
        const stat = fs.statSync(fullPath);
        if (stat.isDirectory()) {
          this.recursivelyExtractCustomTags(fullPath, completions);
        } else if (f.endsWith("TagLib.groovy") || f.endsWith("Taglib.groovy")) {
          const content = fs.readFileSync(fullPath, "utf8");
          // Match standard namespace: static namespace = "my"
          let namespace = "g";
          const nsMatch = content.match(/static\s+namespace\s*=\s*['"]([^'"]+)['"]/);
          if (nsMatch) {
            namespace = nsMatch[1];
          }

          // Match closures like: def myCustomTag = { attrs, body ->
          const tagRegex = /(?:def|Closure)\s+([a-zA-Z0-9_]+)\s*=\s*\{/g;
          let match;
          while ((match = tagRegex.exec(content)) !== null) {
            const tagName = match[1];
            const tag = `${namespace}:${tagName}`;
            const item = new vscode.CompletionItem(tag, vscode.CompletionItemKind.Function);
            item.detail = `Custom TagLib (${f})`;
            item.insertText = new vscode.SnippetString(`${tag} $1>$0</${tag}>`);
            completions.push(item);
          }
        }
      }
    } catch (e) {
      console.warn("Failed to extract custom tags", e);
    }
  }

  private addI18nCompletions(
    prefix: string,
    document: vscode.TextDocument,
    completions: vscode.CompletionItem[]
  ) {
    // If inside a g:message code="" block
    const messageCodeRegex = /<g:message[^>]*code=['"]([^'"]*)$/;
    if (messageCodeRegex.test(prefix)) {
      const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
      if (workspaceFolder) {
        const i18nDir = path.join(workspaceFolder.uri.fsPath, "grails-app", "i18n");
        if (fs.existsSync(i18nDir)) {
          const files = fs.readdirSync(i18nDir).filter(f => f.endsWith(".properties"));
          const keys = new Set<string>();
          files.forEach(f => {
            const content = fs.readFileSync(path.join(i18nDir, f), "utf-8");
            const lines = content.split("\n");
            lines.forEach(line => {
              line = line.trim();
              if (line && !line.startsWith("#")) {
                const parts = line.split("=");
                if (parts.length > 0) {
                  keys.add(parts[0].trim());
                }
              }
            });
          });

          keys.forEach(key => {
            const item = new vscode.CompletionItem(key, vscode.CompletionItemKind.Value);
            item.detail = "I18n Application Message Key";
            completions.push(item);
          });
        }
      }
    }
  }

  private addPathCompletions(
    prefix: string,
    document: vscode.TextDocument,
    completions: vscode.CompletionItem[]
  ) {
    // E.g., <asset:image src=" or <asset:javascript src="
    const srcRegex = /<asset:(image|javascript|stylesheet)[^>]*src=['"]([^'"]*)$/;
    const srcMatch = prefix.match(srcRegex);

    if (srcMatch) {
      const type = srcMatch[1];
      const workspaceFolder = vscode.workspace.getWorkspaceFolder(document.uri);
      if (workspaceFolder) {
        let assetDir = path.join(workspaceFolder.uri.fsPath, "grails-app", "assets");

        if (type === "image") {
          assetDir = path.join(assetDir, "images");
        } else if (type === "javascript") {
          assetDir = path.join(assetDir, "javascripts");
        } else if (type === "stylesheet") {
          assetDir = path.join(assetDir, "stylesheets");
        }

        if (fs.existsSync(assetDir)) {
          this.recursivelyAddFiles(assetDir, "", completions);
        }
      }
    }
  }

  private recursivelyAddFiles(
    baseDir: string,
    subPath: string,
    completions: vscode.CompletionItem[]
  ) {
    const currentDir = path.join(baseDir, subPath);
    if (!fs.existsSync(currentDir)) {
      return;
    }

    const files = fs.readdirSync(currentDir);
    for (const f of files) {
      const fullPath = path.join(currentDir, f);
      const stat = fs.statSync(fullPath);
      const relativePath = subPath ? `${subPath}/${f}` : f;

      if (stat.isDirectory()) {
        this.recursivelyAddFiles(baseDir, relativePath, completions);
      } else {
        const item = new vscode.CompletionItem(relativePath, vscode.CompletionItemKind.File);
        completions.push(item);
      }
    }
  }

  private async getHtmlCompletions(
    document: vscode.TextDocument,
    position: vscode.Position
  ): Promise<vscode.CompletionList | undefined> {
    try {
      // Provide an in-memory URI with html extension so the built-in HTML provider responds to it.
      // E.g. untitled:test.html
      // Better approach: use vscode.executeCompletionItemProvider pointing to the current document.
      // Wait, VS Code html provider only registers on HTML. We must temporarily change the lang id or use a virtual doc.
      // But we can also use Emmet configuration to tell HTML extension about GSP.

      // Attempting a direct call:
      const result = await vscode.commands.executeCommand<vscode.CompletionList>(
        "vscode.executeCompletionItemProvider",
        document.uri,
        position
      );
      return result;
    } catch (e) {
      console.warn("Failed to get HTML completions", e);
      return undefined;
    }
  }
}
