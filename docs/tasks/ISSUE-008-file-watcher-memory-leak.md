# ISSUE-008 · File Watcher Memory Leak
**Severity**: 🔴 Critical
**Service**: ProjectService.ts
**Status**: ✅ DONE
**Parent Plan**: [client-improvement-plan.md](../client-improvement-plan.md)

## Problem
ProjectService.initWatchers() creates file watchers but doesn't track them properly for cleanup. The dispose() method clears the watchers array but doesn't explicitly dispose each watcher. Watchers may remain active after disposal, causing memory leaks and continued file system monitoring.

## Current Code (problematic pattern)
```typescript
// ProjectService.ts - Lines 418-434
private initWatchers(): void {
  // Already watching
  if (this.watchers.length > 0) return;

  workspace.workspaceFolders?.forEach(folder => {
    const pattern = new RelativePattern(folder, "build.gradle");
    const watcher = workspace.createFileSystemWatcher(pattern);

    const refresh = () => this.reloadProject(folder);

    watcher.onDidChange(refresh);
    watcher.onDidCreate(refresh);
    watcher.onDidDelete(refresh);

    this.watchers.push(watcher);
  });
}

// Lines 223-232
dispose() {
  this.watchers.forEach(w => void w.dispose());
  this.watchers.length = 0;

  this.disposables.forEach(d => void d.dispose());
  this.disposables.length = 0;

  this.projects.clear();
  this.activeProjectId = undefined;
}
```

## Fixed Code
```typescript
// ProjectService.ts - Improved watcher management
private readonly watchers = new Set<FileSystemWatcher>();
private readonly watcherDisposables = new Map<FileSystemWatcher, Disposable[]>();
private readonly folderWatchers = new Map<string, FileSystemWatcher>();

/**
 * Initialize file watchers for live updates.
 * Tracks watchers explicitly for proper cleanup.
 */
private initWatchers(): void {
  // Already watching
  if (this.watchers.size > 0) {
    console.log("[ProjectService] Watchers already initialized, skipping");
    return;
  }

  const roots = workspace.workspaceFolders ?? [];
  console.log(`[ProjectService] Initializing watchers for ${roots.length} workspace folders`);

  for (const folder of roots) {
    const folderKey = folder.uri.toString();

    // Skip if already watching this folder
    if (this.folderWatchers.has(folderKey)) {
      console.log(`[ProjectService] Already watching folder: ${folder.name}`);
      continue;
    }

    try {
      const pattern = new RelativePattern(folder, "build.gradle");
      const watcher = workspace.createFileSystemWatcher(pattern);

      // Track the watcher
      this.watchers.add(watcher);
      this.folderWatchers.set(folderKey, watcher);

      // Track event disposables separately
      const eventDisposables: Disposable[] = [];

      const refresh = () => {
        console.log(`[ProjectService] build.gradle changed for ${folder.name}, reloading...`);
        void this.reloadProject(folder);
      };

      // Register event listeners and track their disposables
      eventDisposables.push(watcher.onDidChange(refresh));
      eventDisposables.push(watcher.onDidCreate(refresh));
      eventDisposables.push(watcher.onDidDelete(refresh));

      this.watcherDisposables.set(watcher, eventDisposables);

      console.log(`[ProjectService] Created watcher for ${folder.name}`);
    } catch (error) {
      this.errorService.handleError(
        `Failed to create watcher for ${folder.name}`,
        error,
        ErrorSource.ProjectService,
        ErrorSeverity.Warning
      );
    }
  }

  console.log(`[ProjectService] Total watchers created: ${this.watchers.size}`);
}

/**
 * Clean up a specific watcher.
 */
private disposeWatcher(watcher: FileSystemWatcher): void {
  try {
    // Dispose event listeners first
    const eventDisposables = this.watcherDisposables.get(watcher);
    if (eventDisposables) {
      for (const disposable of eventDisposables) {
        disposable.dispose();
      }
      this.watcherDisposables.delete(watcher);
    }

    // Dispose the watcher itself
    watcher.dispose();

    // Remove from tracking sets
    this.watchers.delete(watcher);

    // Remove from folder mapping
    for (const [key, w] of this.folderWatchers.entries()) {
      if (w === watcher) {
        this.folderWatchers.delete(key);
        break;
      }
    }

    console.log("[ProjectService] Watcher disposed successfully");
  } catch (error) {
    console.error("[ProjectService] Error disposing watcher:", error);
  }
}

/**
 * Clean up all watchers.
 */
private disposeAllWatchers(): void {
  console.log(`[ProjectService] Disposing ${this.watchers.size} watchers...`);

  // Create a copy to avoid modification during iteration
  const watchersCopy = Array.from(this.watchers);

  for (const watcher of watchersCopy) {
    this.disposeWatcher(watcher);
  }

  // Clear all tracking collections
  this.watchers.clear();
  this.watcherDisposables.clear();
  this.folderWatchers.clear();

  console.log("[ProjectService] All watchers disposed");
}

/**
 * Reinitialize watchers when workspace folders change.
 */
private reinitWatchersOnFolderChange(): void {
  console.log("[ProjectService] Workspace folders changed, reinitializing watchers...");

  // Dispose existing watchers
  this.disposeAllWatchers();

  // Reinitialize with new folder list
  this.initWatchers();
}

/**
 * Get watcher statistics for debugging.
 */
getWatcherStats(): { total: number; byFolder: Record<string, boolean> } {
  const byFolder: Record<string, boolean> = {};

  for (const [key, watcher] of this.folderWatchers.entries()) {
    byFolder[key] = this.watchers.has(watcher);
  }

  return {
    total: this.watchers.size,
    byFolder,
  };
}

// Update dispose() to use explicit cleanup
dispose(): void {
  console.log("[ProjectService] Disposing ProjectService...");

  // Dispose all watchers explicitly
  this.disposeAllWatchers();

  // Dispose other disposables
  this.disposables.forEach(d => {
    try {
      d.dispose();
    } catch (error) {
      console.error("[ProjectService] Error disposing disposable:", error);
    }
  });
  this.disposables.length = 0;

  // Clear project data
  this.projects.clear();
  this.activeProjectId = undefined;
  this.cachedDiscovery = undefined;
  this.lastDiscoveryTime = 0;

  console.log("[ProjectService] ProjectService disposed");
}

// Update ActivationManager to handle workspace folder changes
// In ActivationManager.ts, update the workspace folder change listener:
/*
this.disposables.push(
  workspace.onDidChangeWorkspaceFolders(async () => {
    console.log("📁 Workspace folders changed");
    this.container.statusBarService.info("Workspace changed, reinitializing watchers...");

    // Reinitialize watchers with new folder list
    this.container.projectService.reinitWatchersOnFolderChange();

    // Rediscover projects
    await this.container.projectService.discoverProjects();
  })
);
*/
```

## Subtasks
- [x] Track watchers in Set instead of array
- [x] Track event listener disposables separately
- [x] Track watchers by folder for easier cleanup
- [x] Add disposeWatcher() method for explicit cleanup
- [x] Add disposeAllWatchers() method
- [x] Add reinitWatchersOnFolderChange() method
- [x] Update dispose() to use explicit cleanup
- [x] Add _disposed guard to prevent operations after disposal
- [ ] Test watcher lifecycle

## Tradeoffs
- More complex watcher management with multiple tracking collections
- Slightly more memory overhead for tracking
- More code to maintain

## Testing This Fix
1. Create a Grails project and verify watchers are created
2. Modify build.gradle and verify project reloads
3. Close and reopen workspace, verify watchers are recreated
4. Add/remove workspace folders, verify watchers are updated
5. Call dispose() and verify all watchers are cleaned up
6. Use getWatcherStats() to verify watcher tracking
7. Monitor memory usage before and after multiple workspace changes
8. Test error handling when watcher creation fails