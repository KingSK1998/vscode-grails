#!/bin/bash
set -e

echo "🔨 Building Language Server..."

cd server

# Build with Gradle
echo "📦 Building with Gradle..."
./gradlew build -x test

# Create distribution
echo "📦 Creating distribution..."
./gradlew installDist

echo "✅ Server build completed!"
echo "📁 JAR: build/libs/"
echo "📁 Distribution: build/install/Grails Language Server/"
