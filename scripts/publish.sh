#!/bin/bash
set -e

echo "🚀 Publishing vscode-grails..."

# Check if we're on main branch
BRANCH=$(git rev-parse --abbrev-ref HEAD)
if [ "BRANCH" != "main" ]; then
    echo "❌ Must be on main branch to publish. Current: $BRANCH"
    exit 1
fi

# Check for uncommitted changes
if [ -n "$(git status --porcelain)" ]; then
    echo "❌ Uncommitted changes detected. Please commit first."
    exit 1
fi

# Build and package
./scripts/package.sh

# Publish to VS Code Marketplace
echo "🚀 Publishing to VS Code Marketplace..."
cd client

if [ -z "$VSCE_PAT" ]; then
    echo "❌ VSCE_PAT environment variable required"
    exit 1
fi

vsce publish --pat "$VSCE_PAT"

cd ..

echo "✅ Published successfully!"