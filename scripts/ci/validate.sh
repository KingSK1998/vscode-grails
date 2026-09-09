#!/bin/bash
set -e

echo "✅ Running CI validation..."

# Lint client
echo "🧹 Linting client..."
cd client
npm run lint
cd ..

# Test server
echo "🧪 Testing server..."
cd server
./gradlew test --no-daemon
cd ..

# Test client
echo "🧪 Testing client..."
cd client
npm test
cd ..

# Build everything
echo "🔨 Building..."
./scripts/build.sh

echo "✅ Validation completed!"
cd "$(dirname "$0")/../.."
npm run check
npm run test
npm run test:smoke
npm run build
