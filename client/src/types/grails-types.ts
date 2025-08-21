import { GrailsArtifactType } from "../utils/Constants";

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
