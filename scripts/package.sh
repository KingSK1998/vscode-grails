#!/bin/bash
set -e

echo "📦 Packaging vscode-gng-support for distribution..."

# Build everything first
./scripts/build.sh

# Package VS Code extension
echo "📦 Packaging VS Code extension..."
cd client

# Install vsce if not present
if ! command -v vsce &> /dev/null; then
    echo "📦 Installing vsce..."
    npm install -g vsce
fi

# Package the extension
vsce package --out ../dist/

cd ..

# Copy server JAR to dist
mkdir -p dist
cp "server/build/libs/Grails Language Server-0.3.1-SNAPSHOT.jar" dist/

echo "✅ Packaging completed!"
echo "📦 Extension VSIX: dist/*.vsix"
echo "📦 Server JAR: dist/*.jar"