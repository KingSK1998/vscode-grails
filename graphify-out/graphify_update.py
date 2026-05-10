import json
from pathlib import Path
from graphify.detect import detect_incremental
from graphify.extract import extract
from graphify.cache import check_semantic_cache


CODE_EXTS = {
    '.py','.ts','.js','.go','.rs','.java','.cpp','.c','.rb',
    '.swift','.kt','.cs','.scala','.php','.cc','.cxx','.hpp',
    '.h','.kts','.lua','.toc'
}


def log(msg):
    print(msg, flush=True)


def run_update():
    root = Path('.')

    # ---------------- Detect ----------------
    log("[Detect] scanning for changes...")
    detect_result = detect_incremental(root)

    Path('.graphify_incremental.json').write_text(
        json.dumps(detect_result, indent=2)
    )

    new_total = detect_result.get('new_total', 0)

    if new_total == 0:
        log("[Detect] no changes")
        return

    log(f"[Detect] {new_total} changed files")

    # ---------------- Filter ----------------
    new_files = detect_result.get('new_files', {})
    all_changed = [f for files in new_files.values() for f in files]

    code_files = [
        Path(f) for f in all_changed
        if Path(f).suffix.lower() in CODE_EXTS
    ]

    log(f"[Filter] {len(code_files)} code files")

    # ---------------- AST ----------------
    if code_files:
        log("[AST] extracting...")
        ast_result = extract(code_files)
    else:
        ast_result = {'nodes': [], 'edges': []}

    Path('.graphify_ast.json').write_text(
        json.dumps(ast_result, indent=2)
    )

    # ---------------- Cache ----------------
    all_files = [
        f for files in detect_result.get('files', {}).values()
        for f in files
    ]

    cached_nodes, cached_edges, cached_hyperedges, uncached = \
        check_semantic_cache(all_files)

    Path('.graphify_cached.json').write_text(json.dumps({
        'nodes': cached_nodes,
        'edges': cached_edges,
        'hyperedges': cached_hyperedges
    }, indent=2))

    Path('.graphify_uncached.txt').write_text('\n'.join(uncached))

    log(f"[Cache] hit={len(all_files)-len(uncached)} miss={len(uncached)}")

    # ---------------- Merge semantic chunks ----------------
    log("[Semantic] merging chunks...")

    nodes, edges, hyperedges = [], [], []

    for chunk in sorted(root.glob('.graphify_chunk_*.json')):
        try:
            data = json.loads(chunk.read_text())
            nodes.extend(data.get('nodes', []))
            edges.extend(data.get('edges', []))
            hyperedges.extend(data.get('hyperedges', []))
        except Exception as e:
            log(f"[Warn] failed reading {chunk}: {e}")

    Path('.graphify_semantic_new.json').write_text(json.dumps({
        'nodes': nodes,
        'edges': edges,
        'hyperedges': hyperedges
    }, indent=2))

    log(f"[Semantic] nodes={len(nodes)} edges={len(edges)}")

    log("[Done]")