#!/bin/bash
set -e

echo "📦 Installing CI dependencies..."

# Java is usually pre-installed in CI
java -version

# Install Node.js dependencies
cd client
npm ci
cd ..

# Download Gradle dependencies
cd server
./gradlew build -x test --no-daemon
cd ..

echo "✅ Dependencies installed!"
