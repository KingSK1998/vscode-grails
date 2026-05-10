# Agent Guidelines

## Project Structure

```
ROOT/
├── client/           # VS Code extension - TypeScript business logic
│   ├── src/          # Client source code (TypeScript)
│   └── STATUS.md     # Feature status tracking
├── server/           # Language Server - Groovy LSP (Gradle app)
│   ├── src/          # Server source code (Groovy)
│   └── STATUS.md     # Feature status tracking
├── shared/           # Shared schemas, configs, templates between client/server
│   ├── configs/      # Shared TypeScript configs
│   ├── resources/    # Artifact generation templates
│   └── schemas/      # LSP protocol schemas
├── resources/        # VS Code extension resources (independent from client)
│   ├── icons/        # Extension icons
│   ├── language-configurations/  # Language configs (groovy, gsp)
│   ├── snippets/    # Code snippets
│   ├── styles/      # CSS for webviews
│   └── syntaxes/    # TextMate grammars
├── docs/             # Documentation (user-guide, developer-guide)
├── scripts/          # Helper scripts for dev/test
└── .github/workflows/ # CI/CD pipeline
```

### Key Points
- **Root package.json**: Coordinates client + server builds, CI/CD
- **client/**: TypeScript code, VS Code extension UI, commands, tree views
- **server/**: Groovy LSP with LSP4J + Gradle Tooling API
- **shared/**: Templates and configs used by both client and server
- **resources/**: VS Code syntaxes, snippets, icons - bundled directly into extension

## 🔴 MANDATORY RULES
- NO EXCEPTIONS
- **Status Tracking**: After EVERY task touching client or server code, update `client/STATUS.md` or `server/STATUS.md` respectively.
  - Update feature row: ⬜ → 🟡 → ✅ (or 🔴 if broken).
  - Add new features as rows; add regressions to Known Issues.
  - Update `Last updated` date.
- **Clean Up**: Prefix all temporary files with `tmp_rovodev_` and delete them upon task completion.

## High-Signal Commands
- **Client**:
  - Lint: `npm run lint` (strictly enforced)
  - Types: `npm run check-types`
  - Build: `npm run bundle` (prod) or `npm run compile` (dev)
- **Server**:
  - Build: `npm run build:server` (creates shadow JAR)
  - Test: `cd server && ./gradlew test`
  - All Checks: `cd server && ./gradlew checkAll`

## Workflow Sequence
1. **Client Changes**: `npm run compile` -> `npm run check-types` -> `npm run lint`
2. **Server Changes**: `npm run build:server` -> `cd server && ./gradlew test`

## Architecture Notes
- **Client/Server Split**: Client (TypeScript/VS Code) <-> Server (Groovy/LSP4J/Gradle Tooling API).
- **Composition Root**: `ServiceContainer.ts` manages client service lifecycles and DI.
- **Entry Points**:
  - Client: `extension.ts`
  - Server: `GrailsLanguageServer.groovy`
- **Server Provider Pattern**: LSP features are implemented via modular providers extending `BaseProvider.groovy`.

## Critical Constraints
- **Server Build**: Always rebuild the shadow JAR after Groovy changes for the extension to pick up updates.
- **Single Root**: Only the first workspace folder (`workspaceFolders[0]`) is currently indexed by the server.
- **Server Performance**: Use `@CompileStatic` for performance-critical Groovy code.
- **Local Docs**: Refer to `client/RULES.md` and `server/RULES.md`.
