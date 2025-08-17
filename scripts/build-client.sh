#!/bin/bash
set -e

echo "🔨 Building VS Code Extension (Client)..."

cd client

# Install dependencies
echo "📦 Installing dependencies..."
npm install

# Compile TypeScript
echo "🔧 Compiling TypeScript..."
npm run compile

# Lint the code
echo "🧹 Running linter..."
npm run lint

echo "✅ Client build completed!"
echo "📁 Output: client/out/"