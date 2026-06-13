#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "📦 Packaging vscode-gng-support for distribution..."

# Build everything first
./scripts/build.sh

# Package VS Code extension
# Package VS Code extension
echo "📦 Packaging VS Code extension..."
# Install vsce if not present
if ! command -v vsce &> /dev/null; then
    echo "📦 Installing vsce..."
    npm install -g vsce
fi

# Package the extension
vsce package --out dist/vscode-gng-support.vsix

# Copy server JAR to dist
mkdir -p dist
cp "server/build/libs/Grails Language Server-0.5.0-SNAPSHOT-all.jar" dist/

echo "✅ Packaging completed!"
echo "📦 Extension VSIX: dist/*.vsix"
echo "📦 Server JAR: dist/*.jar"