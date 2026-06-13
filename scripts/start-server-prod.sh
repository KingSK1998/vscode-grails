#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "🚀 Starting Language Server in Production Mode..."

cd server

# Ensure we have a built JAR
if [ ! -f "build/libs/Grails Language Server-0.5.0-SNAPSHOT-all.jar" ]; then
    echo "📦 JAR not found, building..."
    ./gradlew build -x test
fi

# Start server
echo "🚀 Starting server..."
java -jar "build/libs/Grails Language Server-0.5.0-SNAPSHOT-all.jar"
