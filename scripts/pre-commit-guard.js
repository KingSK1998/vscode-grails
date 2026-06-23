#!/usr/bin/env node
/**
 * Pre-commit guard: blocks commit if STATUS.md or KB docs are stale.
 * 
 * Rules:
 * - If client/ files changed → client/STATUS.md must be in staged changes
 * - If server/ files changed → server/STATUS.md must be in staged changes
 * - KB update triggers (see docs/architecture/change-triggers.md):
 *   - GrailsService.groovy changed → docs/invariants.md must be staged (ownership may have changed)
 *   - New cache class detected → docs/invariants.md must be staged
 *   - New provider detected → docs/invariants.md must be staged
 *   - Bug-fix commit message → docs/failure-modes.md must be staged
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

function getCommitMessage() {
    try {
        // Read the commit message file if available (during hook execution)
        const fs = require('fs');
        const msgFile = '.git/COMMIT_EDITMSG';
        if (fs.existsSync(msgFile)) {
            return fs.readFileSync(msgFile, 'utf-8').toLowerCase();
        }
    } catch { /* ignore */ }
    return '';
}

function getStagedDiff() {
    try {
        const output = execSync('git diff --cached --unified=0', { encoding: 'utf-8' });
        return output;
    } catch {
        return '';
    }
}

// Patterns that indicate KB-relevant changes
const KB_TRIGGERS = {
    // Files that indicate ownership table may need update
    ownershipChange: [
        'server/src/main/groovy/kingsk/grails/lsp/GrailsService.groovy',
        'server/src/main/groovy/kingsk/grails/lsp/services/',
        'client/src/core/container/ServiceContainer.ts',
    ],
    // Patterns in diff that indicate new cache
    newCachePatterns: [
        /class\s+\w*Cache/,
        /new\s+ThreadSafeLruCache/,
        /private.*cache\s*=/i,
    ],
    // Patterns indicating new provider
    newProviderPatterns: [
        /extends\s+BaseProvider/,
        /class\s+Grails\w*Provider/,
    ],
    // Commit message patterns indicating bug fix
    bugFixPatterns: [
        /fix/i,
        /bug/i,
        /patch/i,
        /hotfix/i,
        /resolve/i,
    ],
};

function main() {
    const staged = getStagedFiles();
    if (staged.length === 0) return 0;

    const errors = [];
    const warnings = [];

    // ── STATUS.MD GUARDS (existing) ──

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

    // ── KB GUARDS (new) ──

    const invariantsStaged = staged.includes('docs/invariants.md');
    const failureModesStaged = staged.includes('docs/failure-modes.md');

    // Guard: GrailsService or ServiceContainer changed → invariants.md
    const ownershipFiles = staged.filter(f =>
        KB_TRIGGERS.ownershipChange.some(pattern => f.startsWith(pattern) || f === pattern)
    );
    if (ownershipFiles.length > 0 && !invariantsStaged) {
        warnings.push(
            `⚠️  Ownership-sensitive files changed but docs/invariants.md not updated.`,
            `   Changed: ${ownershipFiles.slice(0, 3).join(', ')}`,
            `   Check: Does the ownership table in docs/invariants.md §1 need a new row?`
        );
    }

    // Guard: New cache or provider in diff → invariants.md
    const diff = getStagedDiff();
    const hasNewCache = KB_TRIGGERS.newCachePatterns.some(p => p.test(diff));
    const hasNewProvider = KB_TRIGGERS.newProviderPatterns.some(p => p.test(diff));

    if (hasNewCache && !invariantsStaged) {
        warnings.push(
            `⚠️  New cache detected in staged changes but docs/invariants.md not updated.`,
            `   Required: Add cache to ownership table with invalidation trigger.`,
            `   See: docs/architecture/change-triggers.md`
        );
    }

    if (hasNewProvider && !invariantsStaged) {
        warnings.push(
            `⚠️  New provider detected in staged changes but docs/invariants.md not updated.`,
            `   Check: Does the ownership table need updating?`,
            `   See: docs/architecture/change-triggers.md`
        );
    }

    // Guard: Bug-fix commit → failure-modes.md
    const commitMsg = getCommitMessage();
    const isBugFix = KB_TRIGGERS.bugFixPatterns.some(p => p.test(commitMsg));
    if (isBugFix && !failureModesStaged) {
        warnings.push(
            `⚠️  Commit message looks like a bug fix but docs/failure-modes.md not updated.`,
            `   Rule: Every bug fix → failure mode entry within 30 minutes.`,
            `   See: docs/architecture/change-triggers.md`
        );
    }

    // ── MEMORY.MD GUARD (existing, non-blocking) ──

    const codeChanged = clientCode.length > 0 || serverCode.length > 0;
    const memoryStaged = staged.includes('MEMORY.md');

    if (codeChanged && !memoryStaged) {
        warnings.push(
            `⚠️  Code changed but MEMORY.md not updated.`,
            `   Consider: Update MEMORY.md with decisions, patterns, open items.`
        );
    }

    // ── OUTPUT ──

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
