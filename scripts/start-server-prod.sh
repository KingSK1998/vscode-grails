#!/bin/bash
set -e

echo "🚀 Starting Language Server in Production Mode..."

cd server

# Ensure we have a built JAR
if [ ! -f "build/libs/Grails Language Server-0.3.1-SNAPSHOT.jar" ]; then
    echo "📦 JAR not found, building..."
    ./gradlew build -x test
fi

# Start server
echo "🚀 Starting server..."
java -jar "build/libs/Grails Language Server-0.3.1-SNAPSHOT.jar"
