#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "🚀 Setting up vscode-gng-support development environment..."

# Check prerequisites
echo "🔍 Checking prerequisites..."

# Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17+ required. Found Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java $JAVA_VERSION found"

# Check Node.js
if ! command -v node &> /dev/null; then
    echo "❌ Node.js not found. Please install Node.js 16+"
    exit 1
fi
echo "✅ Node.js $(node -v) found"

# Install client dependencies
echo "📦 Installing client dependencies..."
cd client
npm install
cd ..

# Setup server (download dependencies)
echo "📦 Setting up server dependencies..."
cd server
./gradlew build -x test
cd ..

# Create .vscode workspace settings
mkdir -p .vscode
cat > .vscode/settings.json << EOL
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.import.gradle.enabled": true,
  "typescript.preferences.importModuleSpecifier": "relative",
  "java.compile.nullAnalysis.mode": "disabled",
  "grails.javaHome": "",
  "grailsLsp.completionDetail": "ADVANCED",
  "grailsLsp.enableGrailsMagic": true
}
EOL

# Create launch configurations for debugging
cat > .vscode/launch.json << EOL
{
  "version": "0.2.0",
  "configurations": [
    {
      "name": "Launch Extension",
      "type": "extensionHost",
      "request": "launch",
      "args": [
        "--extensionDevelopmentPath=\${workspaceFolder}/client"
      ]
    },
    {
      "name": "Debug LSP Server",
      "type": "java",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
EOL

# Create tasks for VS Code
cat > .vscode/tasks.json << EOL
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "Build Client",
      "type": "shell",
      "command": "./scripts/build-client.sh",
      "group": "build",
      "presentation": {
        "echo": true,
        "reveal": "always",
        "panel": "new"
      }
    },
    {
      "label": "Build Server",
      "type": "shell",
      "command": "./scripts/build-server.sh",
      "group": "build",
      "presentation": {
        "echo": true,
        "reveal": "always",
        "panel": "new"
      }
    },
    {
      "label": "Start Server (Debug Mode)",
      "type": "shell",
      "command": "./scripts/start-server-debug.sh",
      "group": "build",
      "presentation": {
        "echo": true,
        "reveal": "always",
        "panel": "new"
      }
    }
  ]
}
EOL

echo "✅ Development environment ready!"
echo ""
echo "📝 Next steps:"
echo "1. Open IntelliJ IDEA and import the 'server' folder as Gradle project"
echo "2. Open VS Code and open this entire 'vscode-gng-support' folder"
echo "3. Use F5 in VS Code to launch the extension"
echo "4. Run './scripts/start-server-debug.sh' to start server in debug mode"