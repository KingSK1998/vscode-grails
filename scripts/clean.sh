#!/bin/bash
set -e

echo "🧹 Cleaning build artifacts..."

# Clean server
echo "🧹 Cleaning server..."
cd server
./gradlew clean
cd ..

# Clean client
echo "🧹 Cleaning client..."
cd client
rm -rf out/
rm -rf node_modules/.cache/
cd ..

# Clean root
echo "🧹 Cleaning root..."
rm -rf dist/

echo "✅ Clean completed!"
