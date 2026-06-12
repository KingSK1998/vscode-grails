#!/usr/bin/env node
/**
 * Pre-commit guard: blocks commit if STATUS.md is stale.
 * 
 * Rules:
 * - If client/ files changed → client/STATUS.md must be in staged changes
 * - If server/ files changed → server/STATUS.md must be in staged changes
 * - Warns if MEMORY.md not updated (non-blocking)
 * 
 * Install: copy to .git/hooks/pre-commit or run `node scripts/install-hooks.js`
 * Also callable standalone: `node scripts/pre-commit-guard.js`
 */

const { execSync } = require('child_process');

function getStagedFiles() {
    try {
        const output = execSync('git diff --cached --name-only', { encoding: 'utf-8' });
        return output.trim().split('\n').filter(Boolean);
    } catch {
        return [];
    }
}

function main() {
    const staged = getStagedFiles();
    if (staged.length === 0) return 0;

    const errors = [];
    const warnings = [];

    // Check: client code changed → client/STATUS.md must be staged
    const clientCode = staged.filter(f => 
        f.startsWith('client/src/') && !f.endsWith('.test.ts')
    );
    const clientStatusStaged = staged.includes('client/STATUS.md');

    if (clientCode.length > 0 && !clientStatusStaged) {
        errors.push(
            `❌ client/ code changed (${clientCode.length} files) but client/STATUS.md not updated.`,
            `   Changed: ${clientCode.slice(0, 3).join(', ')}${clientCode.length > 3 ? '...' : ''}`,
            `   Fix: Update client/STATUS.md then \`git add client/STATUS.md\``
        );
    }

    // Check: server code changed → server/STATUS.md must be staged
    const serverCode = staged.filter(f => 
        f.startsWith('server/src/') && !f.endsWith('Spec.groovy')
    );
    const serverStatusStaged = staged.includes('server/STATUS.md');

    if (serverCode.length > 0 && !serverStatusStaged) {
        errors.push(
            `❌ server/ code changed (${serverCode.length} files) but server/STATUS.md not updated.`,
            `   Changed: ${serverCode.slice(0, 3).join(', ')}${serverCode.length > 3 ? '...' : ''}`,
            `   Fix: Update server/STATUS.md then \`git add server/STATUS.md\``
        );
    }

    // Warn: MEMORY.md not updated (non-blocking)
    const codeChanged = clientCode.length > 0 || serverCode.length > 0;
    const memoryStaged = staged.includes('MEMORY.md');

    if (codeChanged && !memoryStaged) {
        warnings.push(
            `⚠️  Code changed but MEMORY.md not updated.`,
            `   Consider: Update MEMORY.md with decisions, patterns, open items.`
        );
    }

    // Output
    if (errors.length > 0 || warnings.length > 0) {
        console.log('\n🔒 Pre-commit guard (AGENTS.md §5)\n');
    }

    if (warnings.length > 0) {
        warnings.forEach(w => console.log(w));
        console.log('');
    }

    if (errors.length > 0) {
        errors.forEach(e => console.log(e));
        console.log('\n💡 Skip guard: git commit --no-verify');
        console.log('   But stale STATUS.md = stale agent context = worse AI output.\n');
        return 1;
    }

    return 0;
}

process.exit(main());
