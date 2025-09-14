// -------- Status Labels --------
export enum StatusText {
  // Lifecycle
  STARTING = "Starting",
  RUNNING = "Running",
  STOPPED = "Stopped",
  RESTARTING = "Restarting",
  // Operations
  SYNC = "Syncing",
  INFO = "Info",
  READY = "Ready",
  SUCCESS = "Success",
  WARNING = "Warning",
  ERROR = "Error",
  TESTING = "Testing",
  // Server states
  CONNECTING = "Connecting",
  DISCONNECTED = "Disconnected",
  EMPTY = "",
}

// -------- VS Code Status Bar Icons --------

/**
 * Icons used in the status bar to represent various states.
 * Each icon maps to a VS Code Codicon string.
 */
export enum StatusBarIcon {
  ROCKET = "$(rocket)", // Ready state
  SYNC_SPIN = "$(sync~spin)", // Loading/syncing
  ERROR = "$(error)", // Error state
  WARNING = "$(warning)", // Warning state
  SUCCESS = "$(check)", // Success state
  INFO = "$(info)",
  PLAY = "$(play)", // Running server
  TEST = "$(beaker)", // Testing
  DISCONNECTED = "$(plug)", // LSP disconnected
  EMPTY = "",

  // Build & deployment
  BUILD = "$(tools)", // Building (from lsp)
  GRADLE = "$(package)", // Run tasks from Gradle
}
