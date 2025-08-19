export const GRADLE_EXTENSION_ID = "vscjava.vscode-gradle";

// -------- Extension & Gradle IDs --------
export const EXTENSION_IDS = {
  GRADLE: "vscjava.vscode-gradle",
} as const;

// -------- Status and Operation Messages --------
export enum GrailsMessage {
  EXTENSION_STARTUP = "Starting Grails Extension...",
  EXTENSION_READY = "Grails Extension is ready.",
  EXTENSION_SYNCING = "Syncing Grails project...",
  EXTENSION_SYNC_FAILED = "Failed to sync Grails project.",
  // Server Lifecycle
  SERVER_STARTUP = "Starting Grails Language Server...",
  SERVER_STARTED = "Grails Language Server started successfully.",
  SERVER_START_FAILED = "Failed to start Grails Language Server.",
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
  GRADLE_SYNC_START = "Syncing Gradle project...",
  GRADLE_SYNC_PROGRESS = "Gradle sync in progress...",
  GRADLE_SYNC_COMPLETE = "Gradle sync completed",
  GRADLE_SYNC_FAILED = "Gradle sync failed",
  // Gradle API
  GRADLE_API_INITIALIZING = "Initializing Gradle API...",
  GRADLE_API_INITIALIZED = "Gradle API initialized successfully",
  GRADLE_API_INIT_FAILED = "Failed to initialize Gradle API",
  GRADLE_EXTENSION_MISSING = "Gradle extension not found. Please install it.",
  // Gradle Task
  GRADLE_TASK_NOT_FOUND = "Gradle task not found: {0}",
  GRADLE_TASK_STARTED = "Running Gradle task: {0}",
  GRADLE_TASK_COMPLETED = "Gradle task completed: {0}",
  GRADLE_TASK_CANCELED = "Gradle task canceled: {0}",
  GRADLE_TASK_CANCEL_FAILED = "Failed to cancel Gradle task: {0}",
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

// -------- Short Status Labels --------
export enum StatusText {
  STARTING = "Starting",
  SYNC = "Syncing",
  RESTARTING = "Restarting",
  RUNNING = "Running",
  STOPPED = "Stopped",
  READY = "Ready",
  ERROR = "Error",
  WARNING = "Warning",
  SUCCESS = "Success",
  INFO = "Info",
  EMPTY = "",
}

// -------- VS Code Status Bar Icons --------
export enum StatusBarIcon {
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

// -------- Error Severity --------
export enum ErrorSeverity {
  INFO = "INFO", // Informational messages
  WARNING = "WARNING", // Warnings that don't prevent operation
  ERROR = "ERROR", // Errors that affect functionality
  FATAL = "FATAL", // Critical errors that prevent operation
}

// -------- Common File Paths --------
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

// File type patterns
export const FILE_PATTERNS = {
  GROOVY: "**/*.groovy",
  GSP: "**/*.gsp",
  YML: "**/*.yml",
  YAML: "**/*.yaml",
  PROPERTIES: "**/*.properties",
  JS: "**/*.js",
  CSS: "**/*.css",
  SCSS: "**/*.scss",
} as const;

// -------- Command IDs --------
export const COMMAND_IDS = {
  RUN_APP: "grails.runApp",
  STOP_APP: "grails.stopApp",
  CREATE_CONTROLLER: "grails.createController",
  CREATE_DOMAIN: "grails.createDomain",
  SYNC_PROJECT: "grails.syncProject",
  RESTART_SERVER: "grails.restartServer",
} as const;

// -------- Progress Phases --------
export enum ProgressPhase {
  EXTENSION_STARTUP = "extension/startup",
  GRADLE_SYNC = "gradle/sync",
  SERVER_STARTUP = "server/startup",
  INDEXING = "indexing",
  WORKSPACE_SETUP = "workspace/setup",
  DIAGNOSTIC = "diagnostic",
  IDLE = "idle",
}

// -------- Grails Module Types --------
export enum ModuleType {
  GRADLE = "Gradle",
  SERVER = "Server",
  CLIENT = "Client",
  TASK = "Task",
  PROJECT = "Project",
  EXTENSION = "GFS",
  UNKNOWN = "Unknown",
  GRAILS_FRAMEWORK_SUPPORT = "Grails Framework Support",
}

// -------- VS Code Icons (using codicons) --------
export const Icons = {
  GRAILS: "symbol-misc",
  CONTROLLER: "rocket",
  SERVICE: "gear",
  DOMAIN: "database",
  VIEW: "browser",
  TAGLIB: "tag",
  PLUGIN: "extensions",
  CONFIG: "settings-gear",
  URL_MAPPING: "link",
  SERVER_RUNNING: "play-circle",
  SERVER_STOPPED: "stop-circle",
  REFRESH: "refresh",
  DASHBOARD: "dashboard",

  // Grails-specific icons
  ASSETS: "symbol-color",
  I18N: "globe",
  INIT: "rocket",
  UNIT_TESTS: "beaker",
  INTEGRATION_TESTS: "verified",
  GSP_FILE: "browser",
  YML_FILE: "settings",
  GROOVY_FILE: "symbol-method",
} as const;

// -------- VS Code Theme Colors --------
export const Colors = {
  PRIMARY_GREEN: "charts.green",
  BLUE: "charts.blue",
  PURPLE: "charts.purple",
  ORANGE: "charts.orange",
  RED: "charts.red",
  YELLOW: "charts.yellow",
  GRAY: "charts.gray",
} as const;

// -------- Grails Artifact Types --------
export enum GrailsArtifactType {
  // Core Artifacts (Primary)
  CONTROLLER = "controller",
  SERVICE = "service",
  DOMAIN = "domain",
  VIEW = "view",
  TAGLIB = "taglib",

  // Configuration & Routing
  CONFIG = "config",
  URL_MAPPING = "urlmapping",
  I18N = "i18n",

  // Assets & Resources
  ASSETS = "assets",
  RESOURCES = "resources",

  // Application Structure
  INIT = "init",

  // Development & Testing
  TESTS = "tests",
  UNIT_TESTS = "unit-tests",
  INTEGRATION_TESTS = "integration-tests",
  GROOVY_SRC = "groovy-src",

  // Groovy Project Types
  SOURCE_SETS = "sourcesets",
  DEPENDENCIES = "dependencies",
  TASKS = "tasks",
  STRUCTURE = "structure",
}

// -------- Tree View Contexts --------
export const GRAILS_TREE_CONTEXTS = {
  CONTROLLERS: "grails-controllers",
  SERVICES: "grails-services",
  DOMAINS: "grails-domains",
  VIEWS: "grails-views",
  TAGLIBS: "grails-taglibs",
  CONFIG: "grails-config",
} as const;

export type GrailsTreeContext = (typeof GRAILS_TREE_CONTEXTS)[keyof typeof GRAILS_TREE_CONTEXTS];
