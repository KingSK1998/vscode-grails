import type { ProjectDTO } from "../../shared/protocol/project";

/** Supported project types */
export enum ProjectType {
  Groovy = "groovy",
  Grails = "grails",
  GrailsPlugin = "grails-plugin",
  Unknown = "unknown",
}

/** Complete Grails artifact types */
export enum ArtifactType {
  // Core MVC Artifacts
  Controller = "controller",
  Service = "service",
  Domain = "domain",
  View = "view",
  TagLib = "taglib",

  // Configuration
  Config = "config", // grails-app/conf/
  UrlMapping = "urlmapping", // grails-app/conf/UrlMappings.groovy
  Bootstrap = "bootstrap", // Bootstrap.groovy

  ApplicationConfig = "application", // Application.groovy
  SpringConfig = "spring", // grails-app/conf/spring/
  HibernateConfig = "hibernate", // grails-app/conf/hibernate/

  // ===== INTERCEPTORS & FILTERS =====
  Interceptor = "interceptor", // grails-app/controllers/*Interceptor.groovy
  Filter = "filter", // grails-app/conf/*Filters.groovy

  // ===== CONFIGURATION & ROUTING =====

  // ===== TESTING ARTIFACTS =====
  Tests = "tests",
  UNIT_TEST = "unit-test", // src/test/groovy/*Test.groovy
  INTEGRATION_TEST = "integration-test", // src/integration-test/groovy/*Spec.groovy
  SPOCK_SPEC = "spock-spec", // *Spec.groovy (Spock framework)
  FUNCTIONAL_TEST = "functional-test", // src/test/functional/

  // ===== ASSETS & RESOURCES =====
  Assets = "assets", // grails-app/assets/
  I18N = "i18n", // grails-app/i18n/
  Resources = "resources", // src/main/resources/
  STATIC_RESOURCES = "static", // src/main/webapp/ (older Grails)

  // ===== COMMANDS & SCRIPTS =====
  Command = "command", // grails-app/commands/
  Script = "script", // src/main/scripts/

  // ===== PLUGIN ARTIFACTS =====
  Job = "job", // grails-app/jobs/ (Quartz plugin)
  Utils = "utils", // grails-app/utils/
  CODEC = "codec", // grails-app/utils/*Codec.groovy

  // ===== GROOVY PROJECT TYPES =====
  GroovySrc = "groovy-src", // src/main/groovy/
  JavaSrc = "java-src", // src/main/java/
  SOURCE_SETS = "sourcesets",
  Dependencies = "dependencies",
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

export interface ProjectInfo extends ProjectDTO {
  type: ProjectType;

  artifactCounts?: ArtifactCounts;

  lastUpdated?: number; // Timestamp for when the project info was last updated

  isGradleProject?: boolean; // Whether this project is recognized as a Gradle project
  isPlugin?: boolean; // Whether this project is recognized as a Grails plugin
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
  [ArtifactType.Controller]: "controllers",
  [ArtifactType.Service]: "services",
  [ArtifactType.Domain]: "domain",
  [ArtifactType.View]: "views",
  [ArtifactType.TagLib]: "taglib",
  [ArtifactType.Interceptor]: "controllers", // Interceptors go in controllers dir
  [ArtifactType.Filter]: "conf", // Filters in conf dir
  [ArtifactType.Config]: "conf",
  [ArtifactType.Assets]: "assets",
  [ArtifactType.I18N]: "i18n",
  [ArtifactType.Utils]: "utils",
  [ArtifactType.Command]: "commands",
  [ArtifactType.Job]: "jobs",
} as const;

/**
 * Safely get the directory path for a given artifact type
 * @param artifactType The Grails artifact type
 * @returns Directory name or undefined if not found
 */
export function getArtifactDirectory(artifactType: ArtifactType): string | undefined {
  return ARTIFACT_DIRECTORIES[artifactType as string];
}

// ===== FILE NAMING CONVENTIONS =====
export const ARTIFACT_SUFFIXES = {
  [ArtifactType.Controller]: "Controller.groovy",
  [ArtifactType.Service]: "Service.groovy",
  [ArtifactType.Domain]: ".groovy", // No suffix required
  [ArtifactType.TagLib]: "TagLib.groovy",
  [ArtifactType.Interceptor]: "Interceptor.groovy",
  [ArtifactType.Filter]: "Filters.groovy",
  [ArtifactType.CODEC]: "Codec.groovy",
  [ArtifactType.Command]: "Command.groovy",
  [ArtifactType.UNIT_TEST]: "Test.groovy",
  [ArtifactType.SPOCK_SPEC]: "Spec.groovy",
  [ArtifactType.INTEGRATION_TEST]: "Spec.groovy",
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
    buildGradleContains: ["grails", "org.grails.grails-web", "org.grails.grails-core"],
    // Add profile detection
    profiles: ["web", "rest-api", "angular", "react", "vue-js", "webpack"],
  },
  GRAILS_PLUGIN: {
    directories: [FILE_PATHS.GRAILS_APP],
    buildGradleContains: ["org.grails.grails-plugin", "grails-plugin"],
    files: ["plugin.groovy", "grails-app/conf/application.yml"],
    // Plugin-specific markers
    hasPluginDescriptor: true,
  },
  GROOVY: {
    directories: [FILE_PATHS.SRC_MAIN_GROOVY],
    buildGradleContains: ["groovy", "org.codehaus.groovy"],
    files: [],
    // Exclude if it's actually a Grails plugin
    excludeIfContains: ["grails-app"],
  },
} as const;
