import { GrailsArtifactType } from "../../utils/constants";

/** Supported sproject types */
export enum ProjectType {
  Groovy = "groovy",
  Grails = "grails",
  GrailsPlugin = "grails-plugin",
}

/** Shared metadata the client needs for UI and Gradle-aware tasks. */
export interface ProjectInfo {
  // Unique id for multi-root support
  id: string;
  rootPath: string;
  name: string;
  type: ProjectType;
  dependencies?: string[];

  // Grails-specific metadata
  grailsVersion?: string;
  groovyVersion?: string;
  javaVersion?: string;
  pluginVersion?: string;
  // TODO: Update this to use GrailsArtifact
  actifactCounts?: Record<string, number>;
}

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

export interface ArtifactCounts {
  controllers: number;
  services: number;
  domains: number;
  views: number;
  taglibs: number;
  assets: number;
  i18n: number;
  tests: number;
  groovySrc: number;
}

export interface ConfigFile {
  path: string;
  name: string;
  icon: string;
}
