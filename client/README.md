# Grails & Groovy Support — extension client

This folder holds the **VS Code extension client** (TypeScript). The shipping manifest, marketplace metadata, and bundled assets live at the **repository root** [`package.json`](../package.json).

## Marketplace

- **Extension id:** `KingSK1998.vscode-grails`
- **Marketplace:** [Grails & Groovy Support](https://marketplace.visualstudio.com/items?itemName=KingSK1998.vscode-grails)

## Requirements

- **VS Code** version per root `package.json` `engines.vscode` (currently `^1.103.0`)
- **JDK 17+** for the language server (`grails.javaHome` when needed)

## Documentation

- [Documentation hub](../docs/README.md)
- [User guide](../docs/user-guide.md)
- [Developer guide](../docs/developer-guide.md)

## Build (from repo root)

```bash
npm ci
npm run build:client    # compile + bundle → client/out/extension.js
```

Do not rely on older marketplace ids (`vscode-grails-extension`) or JDK 11 notes — those referred to a different packaging era.
