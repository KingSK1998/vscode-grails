#!/bin/bash
set -e

echo "🧪 Running all tests..."

# Test server
echo "🧪 Running server tests..."
cd server
./gradlew test jacocoTestReport
echo "📊 Coverage report: build/reports/jacoco/test/html/index.html"
cd ..

# Test client
echo "🧪 Running client tests..."
cd client
npm test
cd ..

echo "✅ All tests completed!"
