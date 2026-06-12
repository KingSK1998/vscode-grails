#!/usr/bin/env node
/**
 * Installs git hooks for the project.
 * Run: node scripts/install-hooks.js
 */

const fs = require('fs');
const path = require('path');

const hooksDir = path.join(__dirname, '..', '.git', 'hooks');
const hookSource = path.join(__dirname, 'pre-commit-guard.js');

// Pre-commit hook content — delegates to the guard script
const hookContent = `#!/bin/sh
# Installed by scripts/install-hooks.js
# Enforces AGENTS.md §5 — STATUS.md freshness guard
node scripts/pre-commit-guard.js
`;

const hookPath = path.join(hooksDir, 'pre-commit');

// Check if pre-commit already exists
if (fs.existsSync(hookPath)) {
    const existing = fs.readFileSync(hookPath, 'utf-8');
    if (existing.includes('pre-commit-guard')) {
        console.log('✅ Pre-commit hook already installed.');
        process.exit(0);
    }
    // Backup existing
    fs.copyFileSync(hookPath, hookPath + '.backup');
    console.log(`📋 Backed up existing pre-commit → pre-commit.backup`);
}

fs.writeFileSync(hookPath, hookContent, { mode: 0o755 });
console.log('✅ Pre-commit hook installed → .git/hooks/pre-commit');
console.log('   Guards: STATUS.md freshness (AGENTS.md §5)');
console.log('   Skip: git commit --no-verify');
