#!/usr/bin/env node
/**
 * Syncs line-number references across agent config files.
 * Scans AGENTS.md for `## §N TITLE (LXX-LYY)` headers,
 * then updates all references in CLAUDE.md, GEMINI.md, and .claude/agents/*.md.
 *
 * Run: node scripts/sync-line-refs.js
 * Run after: any edit to AGENTS.md
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const AGENTS_FILE = path.join(ROOT, 'AGENTS.md');

// Files that reference AGENTS.md line numbers
const REF_FILES = [
    'CLAUDE.md',
    'GEMINI.md',
    'docs/skills/architect.md',
    'docs/skills/review.md',
    'docs/skills/recover.md',
].map(f => path.join(ROOT, f)).filter(f => fs.existsSync(f));

// Step 1: Parse AGENTS.md for actual section positions
function parseSections(filePath) {
    const lines = fs.readFileSync(filePath, 'utf-8').split('\n');
    const sections = {};

    for (let i = 0; i < lines.length; i++) {
        const match = lines[i].match(/^## §(\d+)\s+(.+?)\s+\(L\d+-L\d+\)/);
        if (match) {
            const sectionNum = match[1];
            const title = match[2];
            const startLine = i + 1; // 1-indexed

            // Find end: next section header or EOF
            let endLine = lines.length;
            for (let j = i + 1; j < lines.length; j++) {
                if (lines[j].match(/^## §\d+/)) {
                    endLine = j; // line before next section (0-indexed)
                    break;
                }
            }

            sections[sectionNum] = { title, startLine, endLine };
        }
    }

    return { lines, sections };
}

// Step 2: Update AGENTS.md headers with correct line numbers
function updateAgentsHeaders(filePath, lines, sections) {
    let changed = false;
    const updated = [...lines];

    for (const [num, sec] of Object.entries(sections)) {
        const lineIdx = sec.startLine - 1;
        const oldLine = updated[lineIdx];
        const newLine = oldLine.replace(
            /\(L\d+-L\d+\)/,
            `(L${sec.startLine}-L${sec.endLine})`
        );
        if (oldLine !== newLine) {
            updated[lineIdx] = newLine;
            changed = true;
        }
    }

    if (changed) {
        fs.writeFileSync(filePath, updated.join('\n'));
        console.log(`✅ AGENTS.md — headers updated`);
        // Re-parse since line numbers are now correct
        return parseSections(filePath);
    }
    console.log(`✓  AGENTS.md — headers already correct`);
    return { lines: updated, sections };
}

// Step 3: Update references in other files
function updateRefs(filePath, sections) {
    let content = fs.readFileSync(filePath, 'utf-8');
    let changed = false;
    const basename = path.relative(ROOT, filePath);

    // Pattern: `AGENTS.md` §N (LXX-LYY) — update LXX-LYY
    for (const [num, sec] of Object.entries(sections)) {
        const pattern = new RegExp(
            `(AGENTS\\.md['\`\\s]+§${num}\\s+\\(L)\\d+-L\\d+(\\))`,
            'g'
        );
        const replacement = `$1${sec.startLine}-L${sec.endLine}$2`;
        const newContent = content.replace(pattern, replacement);
        if (newContent !== content) {
            content = newContent;
            changed = true;
        }

        // Also handle: `AGENTS.md` §N (LXX-LYY) with backtick variations
        const pattern2 = new RegExp(
            `(AGENTS\\.md\`\\s*§${num}\\s*\\(L)\\d+-L\\d+(\\))`,
            'g'
        );
        const newContent2 = content.replace(pattern2, replacement);
        if (newContent2 !== content) {
            content = newContent2;
            changed = true;
        }
    }

    if (changed) {
        fs.writeFileSync(filePath, content);
        console.log(`✅ ${basename} — refs updated`);
    } else {
        console.log(`✓  ${basename} — refs already correct`);
    }
}

// Main
function main() {
    console.log('🔄 Syncing AGENTS.md line references...\n');

    let { lines, sections } = parseSections(AGENTS_FILE);

    // Update AGENTS.md headers first
    ({ lines, sections } = updateAgentsHeaders(AGENTS_FILE, lines, sections));

    // Update all referencing files
    for (const refFile of REF_FILES) {
        updateRefs(refFile, sections);
    }

    console.log(`\nDone. ${Object.keys(sections).length} sections, ${REF_FILES.length + 1} files checked.`);
}

main();
