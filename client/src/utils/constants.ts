export const GRADLE_EXTENSION_ID = "vscjava.vscode-gradle";

// -------- Extension & Gradle IDs --------
export const EXTENSION_IDS = {
  GRADLE: "vscjava.vscode-gradle",
} as const;

export const EXTENSION_ID = "kingsk1998.vscode-grails";
export const EXTENSION_NAME = "Grails";

// -------- Status and Operation Messages --------
export const Messages = {
  EXTENSION_STARTING: "Starting Grails Extension...",
  EXTENSION_READY: "Grails Extension is ready.",
  EXTENSION_RUNNING: "Grails Extension is running.",
  EXTENSION_STOPPED: "Grails Extension is stopped.",
  EXTENSION_RESTARTING: "Restarting Grails Extension...",

  EXTENSION_INTIALIZING: "Initializing Grails extension...",
  EXTENSION_SYNCING: "Syncing Grails project...",
  EXTENSION_SYNC_FAILED: "Failed to sync Grails project.",

  /* ─── Server lifecycle ─────────────────────── */
  SERVER_CONNECTING: "Connecting to Grails Language Server...",
  SERVER_DISCONNECTED: "Language server disconnected - Click to restart",

  SERVER_STARTING: "Starting Grails Language Server...",
  SERVER_STARTED: "Grails Language Server started.",
  SERVER_START_FAILED: "Failed to start Grails Language Server.",
  SERVER_STOPPED: "Grails Language Server stopped.",
  SERVER_STOPPED_SUCCESS: "Grails Language Server stopped successfully.",
  SERVER_STOPPED_FAILED: "Failed to stop Grails Language Server.",
  SERVER_RESTARTED: "Grails Language Server restarted successfully.",
  SERVER_RESTART_FAILED: "Failed to restart Grails Language Server.",
  SERVER_NOT_RUNNING: "Grails Language Server: Not Running",

  /* ─── Error templates ──────────────────────── */
  SERVER_ERROR: "Grails Server Error: {0}",
  CLIENT_ERROR: "Grails Client Error: {0}",
  GRADLE_ERROR: "Gradle Error: {0}",
  LANGUAGE_SERVER_ERROR: "Language Server Error: {0}",

  /* ─── Project ──────────────────────────────── */
  INVALID_PROJECT: "Not a valid Groovy or Grails project",
  TESTING: "Running tests...",
  INFO: "Information available",
  SUCCESS: "Successful operation",
  WARNING: "Warning - Click for details",
  ERROR: "Error occurred - Check Problems panel",

  /* ─── Gradle-project sync ──────────────────── */
  GRADLE_SYNC_START: "Syncing with Gradle...",
  GRADLE_SYNC_PROGRESS: "Gradle sync in progress...",
  GRADLE_SYNC_COMPLETE: "Gradle sync completed",
  GRADLE_SYNC_FAILED: "Failed to sync with Gradle",
  GRADLE_SYNC_PROJECTS: "Synchronizing Gradle projects",

  /* ─── Gradle API / extension ───────────────── */
  GRADLE_ACTIVATING: "Activating Gradle extension...",
  GRADLE_INITIALIZING: "Initializing Gradle API...",
  GRADLE_API_INITIALIZED: "Gradle API initialized successfully",
  GRADLE_API_INIT_FAILED: "Failed to initialize Gradle API",
  GRADLE_NOT_AVAILABLE: "Gradle API is not available. Please check the extension.",
  GRADLE_API_ACTIVATION_FAILED: "Unable to activate the Gradle extension.",
  GRADLE_API_INIT_SUCCESS: "Gradle API initialized successfully.",
  GRADLE_EXTENSION_MISSING:
    "Gradle extension not found. Please install 'Gradle for Java' extension.",

  /* ─── Gradle tasks ─────────────────────────── */
  GRADLE_TASK_NOT_FOUND: "Gradle task not found: {0}",
  GRADLE_TASK_STARTED: "Running Gradle task: {0}",
  GRADLE_TASK_COMPLETED: "Gradle task completed: {0}",
  GRADLE_TASK_CANCELED: "Gradle task canceled: {0}",
  GRADLE_TASK_CANCEL_FAILED: "Failed to cancel Gradle task: {0}",
  GRADLE_TASK_PROVIDER_NOT_AVAILABLE: "Gradle Task Provider not available.",
  GRADLE_TASKS_LOADED: "Gradle tasks loaded.",
  GRADLE_TASK_RUNNING: "Running Gradle task: {0}",
  GRADLE_TASK_SUCCESS: 'Gradle task "{0}" completed successfully',
  GRADLE_TASK_FAILED: 'Failed to run Gradle task "{0}"',
} as const;

/** Union of all message keys, useful for type-safe utilities */
export type MessageKey = keyof typeof Messages;

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
  EXTENSION = "extension",
  GRADLE_SERVICE = "gradle.service",
  PROJECT_SERVICE = "project.service",
  LANGUAGE_SERVER = "language.server",
  ERROR_SERVICE = "error.service",
  STATUS_BAR = "status.bar",
  CONFIGURATION = "configuration",
  // TODO: remove below
  CLIENT = "Client",
  TASK = "Task",
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

// -------- Tree View Contexts --------
export const GRAILS_TREE_CONTEXTS = {
  CONTROLLERS: "grails-controllers",
  SERVICES: "grails-services",
  DOMAINS: "grails-domains",
  VIEWS: "grails-views",
  TAGLIBS: "grails-taglibs",
  CONFIG: "grails-config",
} as const;

export const GRAILS_ITEM_TO_ICONS_MAP = {
  /* ===============================
   * 📂 Folders
   * =============================== */
  CONTROLLER_FOLDER: "file-submodule",
  SERVICE_FOLDER: "wrench-screwdriver",
  DOMAIN_FOLDER: "database",
  VIEW_FOLDER: "layout-panel-left",
  TAGLIB_FOLDER: "code",
  CONF_FOLDER: "settings-gear",
  ASSETS_FOLDER: "package",
  ASSETS_IMAGES_FOLDER: "image",
  ASSETS_CSS_FOLDER: "file-code",
  ASSETS_JAVASCRIPT_FOLDER: "file-terminal",
  ASSETS_MISCELLANEOUS_FOLDER: "files",
  RESOURCES_FOLDER: "folder-kanban",
  I18N_FOLDER: "languages",
  INIT_FOLDER: "rocket",
  GROOVY_SRC_FOLDER: "braces",
  UNIT_TESTS_FOLDER: "beaker",
  INTEGRATION_TESTS_FOLDER: "flask-conical",

  /* ===============================
   * 📄 Files
   * =============================== */
  APPLICATION: "play-circle",
  BOOTSTRAP: "gear",
  CONFIG: "settings",
  CONTROLLER: "circuit-board",
  SERVICE: "tool",
  TAGLIB: "puzzle",
  DOMAIN: "database",
  GSP: "file-badge",
  TEST: "beaker",
  GROOVY: "file-code",

  /* ===============================
   * 🛠 Optional / Advanced Files
   * =============================== */
  CSS: "file-css",
  JAVASCRIPT: "file-js",
  IMAGES: "image",
  SPRING_RESOURCES: "leaf",
  APPLICATION_YML: "file-badge",
  LOG_XML: "file-bar-chart",
  PROPERTIES: "file-text",
};

export const GRAILS_SEMANTIC_ICONS = {
  /* ===============================
   * 🌱 Dependency Injection / Beans
   * =============================== */
  INJECTED_SERVICE: "leaf", // e.g. def bookService, @Autowired BookService
  SPRING_BEAN: "leaf", // Spring-managed beans
  GRAILS_BEAN: "leaf", // Grails artefact injections

  /* ===============================
   * ⚡ Controller & Actions
   * =============================== */
  CONTROLLER_ACTION: "zap", // Any closure/action in a Controller
  REST_ENDPOINT: "cloud", // Actions with @GetMapping, @PostMapping, etc.
  INTERCEPTOR: "shield", // before/after/interceptor blocks

  /* ===============================
   * 🎬 Lifecycle / Events
   * =============================== */
  BOOTSTRAP_INIT: "rocket", // BootStrap.init, Application.groovy main
  DOMAIN_CONSTRAINTS: "ruler", // static constraints
  DOMAIN_MAPPING: "map", // static mapping
  ASYNC_TASK: "timer", // runAsync, @Async methods
};
