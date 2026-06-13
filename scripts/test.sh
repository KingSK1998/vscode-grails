#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "🧪 Running all tests..."

# Test server
echo "🧪 Running server tests..."
cd server
./gradlew test jacocoTestReport
echo "📊 Coverage report: build/reports/jacoco/test/html/index.html"
cd ..

# Test client
echo "🧪 Running client tests..."
npm run compile
node ./client/out/test/runTest.js

echo "✅ All tests completed!"
