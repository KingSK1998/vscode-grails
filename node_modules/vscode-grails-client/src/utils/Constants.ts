export const GRADLE_EXTENSION_ID = "vscjava.vscode-gradle";

/**
 * Messages for Grails Language Server status and operations
 */
export enum GRAILS_MESSAGE {
  EXTENSION_STARTUP = "Starting Grails Extension...",
  EXTENSION_READY = "Grails Extension is ready.",
  EXTENSION_SYNCING = "Syncing Grails project...",
  EXTENSION_SYNC_FAILED = "Failed to sync Grails project.",

  // Server Lifecycle
  SERVER_STARTUP_MESSAGE = "Starting Grails Language Server...",
  SERVER_STARTUP_SUCCESS = "Grails Language Server started successfully.",
  SERVER_STARTUP_FAILED = "Failed to start Grails Language Server.",
  SERVER_STOPPED = "Grails Language Server stopped.",
  SERVER_STOPPED_SUCCESS = "Grails Language Server stopped successfully.",
  SERVER_STOPPED_FAILED = "Failed to stop Grails Language Server.",
  SERVER_RESTARTED = "Grails Language Server restarted successfully.",
  SERVER_RESTART_FAILED = "Failed to restart Grails Language Server.",
  SERVER_NOT_RUNNING = "Grails Language Server: Not Running",

  // Error States (templated)
  SERVER_ERROR = "Grails Server Error: {0}",
  CLIENT_ERROR = "Grails Client Error: {0}",
  GRADLE_ERROR = "Gradle Error: {0}",
  LANGUAGE_SERVER_ERROR = "Language Server Error: {0}",

  // Project Messages
  INVALID_PROJECT = "Not a valid Groovy or Grails project",

  // Gradle Messages
  GRADLE_SYNC_STARTED = "Syncing Gradle project...",
  GRADLE_SYNC_IN_PROGRESS = "Gradle sync in progress...",
  GRADLE_SYNC_COMPLETED = "Gradle sync completed",
  GRADLE_SYNC_FAILED = "Gradle sync failed",

  // Gradle API Messages
  GRADLE_API_INITIALIZING = "Initializing Gradle API...",
  GRADLE_API_INITIALIZED = "Gradle API initialized successfully",
  GRADLE_API_INITIALIZATION_FAILED = "Failed to initialize Gradle API",

  GRADLE_EXTENSION_NOT_FOUND = "Gradle extension not found. Please install it.",
  // Gradle Task Messages
  GRADLE_TASK_NOT_FOUND = "Gradle task not found: {0}",
  GRADLE_TASK_STARTED = "Running Gradle task: {0}",
  GRADLE_TASK_COMPLETED = "Gradle task completed: {0}",
  GRADLE_TASK_CANCELED = "Gradle task canceled: {0}",
  GRADLE_TASK_CANCELLATION_FAILED = "Failed to cancel Gradle task: {0}",

  GRADLE_TASK_PROVIDER_MISSING = "Gradle Task Provider not available.",
  GRADLE_TASKS_LOADED = "Gradle tasks loaded.",
  GRADLE_TASK_RUNNING = "Running Gradle task: {0}",
  GRADLE_TASK_SUCCESS = 'Gradle task "{0}" completed successfully',
  GRADLE_TASK_FAILED = 'Failed to run Gradle task "{0}"',
  GRADLE_API_INIT_SUCCESS = "Gradle API initialized successfully.",
  GRADLE_API_NOT_AVAILABLE = "Gradle API is not available. Please check the extension.",
  GRADLE_API_ACTIVATION_FAILED = "Unable to activate the Gradle extension.",
  GRADLE_SYNC_PROGRESS_TITLE = "Waiting for Gradle to finish project sync...",

  EMPTY = "",
}

/**
 * Short status labels shown in status bar
 */
export enum STATUS_TEXT_MESSAGES {
  STARTING = "Starting", // when the server or client is starting
  SYNC = "Syncing", // when workspace setup is in progress
  RESTARTING = "Restarting", // when the server or client is restarting
  RUNNING = "Running", // when the server or client is running
  STOPPED = "Stopped", // when the server or client is stopped
  READY = "Ready", // when the server or client is restarting
  ERROR = "Error", // when there is an error in the server or client
  WARNING = "Warning", // when there is a warning in the server or client
  SUCCESS = "Success", // when the server or client has successfully completed an operation
  INFO = "Info", // when there is an informational message in the server or client
  EMPTY = "",
}

/**
 * VSCode-specific status bar icons
 */
export enum STATUS_BAR_ICONS {
  SYNC_SPIN = "$(sync~spin)",
  ROCKET = "$(rocket)",
  CIRCLE_SLASH = "$(circle-slash)",
  CROSS = "$(x)",
  ALERT = "$(alert)",
  ERROR = "$(error)",
  WARNING = "$(warning)",
  SUCCESS = "$(check)",
  INFO = "$(info)",
  DEBUG = "$(debug)",
  TERMINAL = "$(terminal)",
  EMPTY = "",
}

/**
 * Error severity levels for error handling
 */
export enum ERROR_SEVERITY {
  INFO = "INFO", // Informational messages
  WARNING = "WARNING", // Warnings that don't prevent operation
  ERROR = "ERROR", // Errors that affect functionality
  FATAL = "FATAL", // Critical errors that prevent operation
}

/**
 * Common file paths and patterns used in the extension
 */
export const FILE_PATHS = {
  GRAILS_APP: "grails-app",
  BUILD_GRADLE: "build.gradle",
  APPLICATION_YML: "grails-app/conf/application.yml",
  APPLICATION_GROOVY: "grails-app/conf/application.groovy",
  CONTROLLERS_DIR: "grails-app/controllers",
  DOMAIN_DIR: "grails-app/domain",
  VIEWS_DIR: "grails-app/views",
  SERVICES_DIR: "grails-app/services",
} as const;

/**
 * Command IDs used in the extension
 */
export const COMMAND_IDS = {
  RUN_APP: "grails.runApp",
  STOP_APP: "grails.stopApp",
  CREATE_CONTROLLER: "grails.createController",
  CREATE_DOMAIN: "grails.createDomain",
  SYNC_PROJECT: "grails.syncProject",
  RESTART_SERVER: "grails.restartServer",
} as const;

export enum PROGRESS_PHASE {
  EXTENSION_STARTUP = "extension/startup",
  GRADLE_SYNC = "gradle/sync",
  SERVER_STARTUP = "server/startup",
  INDEXING = "indexing",
  WORKSPACE_SETUP = "workspace/setup",
  DIAGNOSTIC = "diagnostic",
  IDLE = "idle",
}

/**
 * Types of Modules or Components in the Grails ecosystem
 */
export enum MODULE_TYPE {
  GRADLE = "Gradle",
  SERVER = "Server",
  CLIENT = "Client",
  TASK = "Task",
  PROJECT = "Project",
  EXTENSION = "GFS",
  UNKNOWN = "Unknown",
  GRAILS_FRAMEWORK_SUPPORT = "Grails Framework Support",
}
