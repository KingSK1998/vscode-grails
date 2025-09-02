/** Supported project types */
export enum ProjectType {
  Groovy = "groovy",
  Grails = "grails",
  GrailsPlugin = "grails-plugin",
}

/** Complete Grails artifact types */
export enum GrailsArtifactType {
  // ===== CORE MVC ARTIFACTS =====
  CONTROLLER = "controller",
  SERVICE = "service",
  DOMAIN = "domain",
  VIEW = "view",
  TAGLIB = "taglib",
  URL_MAPPING = "urlmapping", // grails-app/conf/UrlMappings.groovy

  // ===== INTERCEPTORS & FILTERS =====
  INTERCEPTOR = "interceptor", // grails-app/controllers/*Interceptor.groovy
  FILTER = "filter", // grails-app/conf/*Filters.groovy

  // ===== CONFIGURATION & ROUTING =====
  CONFIG = "config", // grails-app/conf/
  APPLICATION_CONFIG = "application", // Application.groovy
  BOOTSTRAP = "bootstrap", // Bootstrap.groovy
  SPRING_CONFIG = "spring", // grails-app/conf/spring/
  HIBERNATE_CONFIG = "hibernate", // grails-app/conf/hibernate/

  // ===== TESTING ARTIFACTS =====
  TESTS = "tests",
  UNIT_TEST = "unit-test", // src/test/groovy/*Test.groovy
  INTEGRATION_TEST = "integration-test", // src/integration-test/groovy/*Spec.groovy
  SPOCK_SPEC = "spock-spec", // *Spec.groovy (Spock framework)
  FUNCTIONAL_TEST = "functional-test", // src/test/functional/

  // ===== ASSETS & RESOURCES =====
  ASSETS = "assets", // grails-app/assets/
  I18N = "i18n", // grails-app/i18n/
  RESOURCES = "resources", // src/main/resources/
  STATIC_RESOURCES = "static", // src/main/webapp/ (older Grails)

  // ===== COMMANDS & SCRIPTS =====
  COMMAND = "command", // grails-app/commands/
  SCRIPT = "script", // src/main/scripts/

  // ===== PLUGIN ARTIFACTS =====
  JOBS = "jobs", // grails-app/jobs/ (Quartz plugin)
  UTILS = "utils", // grails-app/utils/
  CODEC = "codec", // grails-app/utils/*Codec.groovy

  // ===== GROOVY PROJECT TYPES =====
  GROOVY_SRC = "groovy-src", // src/main/groovy/
  JAVA_SRC = "java-src", // src/main/java/
  SOURCE_SETS = "sourcesets",
  DEPENDENCIES = "dependencies",
  TASKS = "tasks",
}

/** Grails artifact counts for project overview */
export interface ArtifactCounts {
  // ===== CORE MVC =====
  controllers: number;
  services: number;
  domains: number;
  views: number;
  taglibs: number;

  // ===== INTERCEPTORS & FILTERS =====
  interceptors: number;
  filters: number;

  // ===== CONFIGURATION =====
  config: number;
  urlMappings: number;
  bootstrap: number;
  applicationConfig: number;
  springConfigs: number;
  hibernateConfigs: number;

  // ===== TESTING =====
  unitTests: number;
  integrationTests: number;
  spockSpecs: number;
  functionalTests: number;

  // ===== ASSETS & RESOURCES =====
  assets: number;
  i18n: number;
  resources: number;
  gspFiles: number;
  staticFiles: number;

  // ===== COMMANDS & SCRIPTS =====
  commands: number;
  scripts: number;

  // ===== PLUGIN ARTIFACTS =====
  jobs: number; // Quartz jobs
  utils: number;
  codecs: number;

  // ===== SOURCE CODE =====
  groovySrc: number;
  javaSrc: number;
  dependencies: number;
  tasks: number;
}

/** Core project metadata for UI and workspace management. */
export interface ProjectInfo {
  // Multi-root support
  id: string;
  rootPath: string;
  name: string;
  type: ProjectType;

  // Gradle dependencies
  dependencies?: string[];

  // Version info from LSP cache or build.gradle parsing
  grailsVersion?: string;
  groovyVersion?: string;
  javaVersion?: string;
  pluginVersion?: string;

  // UI display data
  artifactCounts?: ArtifactCounts;
}

/** Grails artifact definition for tree display */
export interface GrailsArtifact {
  name: string;
  type: GrailsArtifactType;
  path: string;
  packageName?: string;
}

export interface GrailsProjectInfo {
  name: string;
  version: string;
  grailsVersion: string;
  groovyVersion: string;
  javaVersion: string;
  artifacts: GrailsArtifact[];
}

export interface ConfigFile {
  path: string;
  name: string;
  icon: string;
}

// ===== COMPLETE FILE PATHS =====
export const FILE_PATHS = {
  // Build files
  BUILD_GRADLE: "build.gradle",
  BUILD_GRADLE_KTS: "build.gradle.kts",
  SETTINGS_GRADLE: "settings.gradle",
  GRADLE_PROPERTIES: "gradle.properties",

  // Main Grails structure
  GRAILS_APP: "grails-app",

  // Core MVC directories
  CONTROLLERS_DIR: "grails-app/controllers",
  SERVICES_DIR: "grails-app/services",
  DOMAIN_DIR: "grails-app/domain",
  VIEWS_DIR: "grails-app/views",
  TAGLIB_DIR: "grails-app/taglib",

  // Configuration
  CONF_DIR: "grails-app/conf",
  APPLICATION_YML: "grails-app/conf/application.yml",
  APPLICATION_GROOVY: "grails-app/conf/application.groovy",
  URL_MAPPINGS: "grails-app/conf/UrlMappings.groovy",
  BOOTSTRAP_GROOVY: "grails-app/conf/BootStrap.groovy",
  SPRING_DIR: "grails-app/conf/spring",
  HIBERNATE_DIR: "grails-app/conf/hibernate",

  // Assets & Resources
  ASSETS_DIR: "grails-app/assets",
  I18N_DIR: "grails-app/i18n",
  INIT_DIR: "grails-app/init",
  UTILS_DIR: "grails-app/utils",

  // Commands (Grails 3+)
  COMMANDS_DIR: "grails-app/commands",

  // Plugin directories
  JOBS_DIR: "grails-app/jobs", // Quartz plugin

  // Standard Groovy/Java structure
  SRC_MAIN_GROOVY: "src/main/groovy",
  SRC_MAIN_JAVA: "src/main/java",
  SRC_MAIN_RESOURCES: "src/main/resources",
  SRC_MAIN_SCRIPTS: "src/main/scripts",

  // Testing directories
  SRC_TEST_GROOVY: "src/test/groovy",
  SRC_TEST_JAVA: "src/test/java",
  SRC_TEST_RESOURCES: "src/test/resources",
  SRC_INTEGRATION_TEST: "src/integration-test/groovy",
  SRC_FUNCTIONAL_TEST: "src/test/functional",
} as const;

// ===== ARTIFACT DIRECTORY MAPPING =====
export const ARTIFACT_DIRECTORIES: Record<string, string> = {
  [GrailsArtifactType.CONTROLLER]: "controllers",
  [GrailsArtifactType.SERVICE]: "services",
  [GrailsArtifactType.DOMAIN]: "domain",
  [GrailsArtifactType.VIEW]: "views",
  [GrailsArtifactType.TAGLIB]: "taglib",
  [GrailsArtifactType.INTERCEPTOR]: "controllers", // Interceptors go in controllers dir
  [GrailsArtifactType.FILTER]: "conf", // Filters in conf dir
  [GrailsArtifactType.CONFIG]: "conf",
  [GrailsArtifactType.ASSETS]: "assets",
  [GrailsArtifactType.I18N]: "i18n",
  [GrailsArtifactType.UTILS]: "utils",
  [GrailsArtifactType.COMMAND]: "commands",
  [GrailsArtifactType.JOBS]: "jobs",
} as const;

/**
 * Safely get the directory path for a given artifact type
 * @param artifactType The Grails artifact type
 * @returns Directory name or undefined if not found
 */
export function getArtifactDirectory(artifactType: GrailsArtifactType): string | undefined {
  return ARTIFACT_DIRECTORIES[artifactType as string];
}

// ===== FILE NAMING CONVENTIONS =====
export const ARTIFACT_SUFFIXES = {
  [GrailsArtifactType.CONTROLLER]: "Controller.groovy",
  [GrailsArtifactType.SERVICE]: "Service.groovy",
  [GrailsArtifactType.DOMAIN]: ".groovy", // No suffix required
  [GrailsArtifactType.TAGLIB]: "TagLib.groovy",
  [GrailsArtifactType.INTERCEPTOR]: "Interceptor.groovy",
  [GrailsArtifactType.FILTER]: "Filters.groovy",
  [GrailsArtifactType.CODEC]: "Codec.groovy",
  [GrailsArtifactType.COMMAND]: "Command.groovy",
  [GrailsArtifactType.UNIT_TEST]: "Test.groovy",
  [GrailsArtifactType.SPOCK_SPEC]: "Spec.groovy",
  [GrailsArtifactType.INTEGRATION_TEST]: "Spec.groovy",
} as const;

// ===== SPECIAL FILES =====
export const SPECIAL_FILES = {
  APPLICATION_GROOVY: "Application.groovy", // Main application class
  BOOTSTRAP_GROOVY: "BootStrap.groovy", // Bootstrap initialization
  URL_MAPPINGS: "UrlMappings.groovy", // URL routing
  APPLICATION_YML: "application.yml", // Spring configuration
  APPLICATION_PROPERTIES: "application.properties",
} as const;

// -------- File Type Patterns --------
export const FILE_PATTERNS = {
  // Groovy and Java
  GROOVY: "**/*.groovy",
  JAVA: "**/*.java",

  // Grails specific
  GSP: "**/*.gsp",

  // Configuration
  YML: "**/*.yml",
  YAML: "**/*.yaml",
  PROPERTIES: "**/*.properties",

  // Web assets
  JS: "**/*.js",
  CSS: "**/*.css",
  SCSS: "**/*.scss",
  LESS: "**/*.less",

  // Build files
  GRADLE: "**/build.gradle",
  GRADLE_KTS: "**/build.gradle.kts",

  // Documentation
  MD: "**/*.md",
  TXT: "**/*.txt",
} as const;

// -------- Build File Detection Patterns --------
export const BUILD_FILES = [FILE_PATHS.BUILD_GRADLE, FILE_PATHS.BUILD_GRADLE_KTS] as const;

// -------- Project Detection Markers --------
export const PROJECT_MARKERS = {
  GRAILS: {
    directories: [FILE_PATHS.GRAILS_APP],
    files: [FILE_PATHS.APPLICATION_YML, FILE_PATHS.APPLICATION_GROOVY],
    buildGradleContains: ["grails", "org.grails.grails-web"],
  },
  GRAILS_PLUGIN: {
    directories: [FILE_PATHS.GRAILS_APP],
    buildGradleContains: ["org.grails.grails-plugin", "grails-plugin"],
    files: ["plugin.groovy", "grails-app/conf/application.yml"],
  },
  GROOVY: {
    directories: [FILE_PATHS.SRC_MAIN_GROOVY],
    buildGradleContains: ["groovy", "org.codehaus.groovy"],
    files: [],
  },
} as const;
