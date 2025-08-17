#!/bin/bash
set -e

echo "🐛 Starting Language Server in Debug Mode..."

cd server

# Build first to ensure we have latest code
echo "📦 Building server..."
./gradlew build -x test

# Start with remote debugging enabled
echo "🚀 Starting server with remote debugging on port 5005..."
echo "💡 Connect IntelliJ debugger to localhost:5005"
echo "💡 Client will connect automatically when available"

java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
     -jar build/libs/Grails\ Language\ Server-0.3.1-SNAPSHOT.jar