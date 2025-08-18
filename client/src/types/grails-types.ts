import { GrailsArtifactType } from "../utils/constants";

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
