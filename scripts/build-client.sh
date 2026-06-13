#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "🔨 Building VS Code Extension (Client)..."

cd client

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Compile TypeScript
echo "🔧 Compiling TypeScript..."
npm run compile

# Bundle extension
echo "📦 Bundling extension..."
npm run bundle

# Lint the code
echo "🧹 Running linter..."
npm run lint

echo "✅ Client build completed!"
echo "📁 Output: client/out/"