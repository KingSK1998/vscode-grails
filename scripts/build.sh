#!/bin/bash
set -e

# Navigate to project root
cd "$(dirname "$0")/.."

echo "🔨 Building vscode-gng-support..."

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Build server first (creates JAR for production)
echo -e "${BLUE}Building language server...${NC}"
cd server
./gradlew build -x test
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Server build completed${NC}"
else
    echo -e "${RED}❌ Server build failed${NC}"
    exit 1
fi
cd ..

# Build client
echo -e "${BLUE}Building VS Code extension...${NC}"
cd client
npm install
npm run compile
npm run bundle
if [ $? -eq 0 ]; then
    echo -e "${GREEN}✅ Client build completed${NC}"
else
    echo -e "${RED}❌ Client build failed${NC}"
    exit 1
fi
cd ..

echo -e "${GREEN}✅ Build completed successfully!${NC}"
echo "📦 Server JAR: server/build/libs/"
echo "📦 Client: client/out/"