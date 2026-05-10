export interface DependencyDTO {
  name: string;
  group: string;
  version: string;
  scope?: string;
}

export interface ArtifactDTO {
  controllers: number;
  services: number;
  domains: number;
  views: number;
  taglibs: number;
}

export type ProjectTypeDTO = "groovy" | "grails" | "grails-plugin";

export interface ProjectDTO {
  id: string;
  rootPath: string;
  name: string;
  type: string;

  grailsVersion?: string;
  groovyVersion?: string;
  javaVersion?: string;
  pluginVersion?: string;

  dependencies: DependencyDTO[];
  artifact?: ArtifactDTO;
}

export interface ProjectPatchDTO {
  projectId: string;
  changes: Partial<ProjectDTO>;
}
