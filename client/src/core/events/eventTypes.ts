import { ProjectInfo } from "../../features/models/modelTypes";

export interface BaseGrailsEvents {
  timestamp: number;
  source: string;
}

export enum EventType {
  PROJECTS_DISCOVERED = "projects.discovered",
  PROJECT_DISCOVERED = "project.discovered",
  PROJECT_LOADED = "project.loaded",
  PROJECT_SYNC_STARTED = "project.sync.started",
  PROJECT_SYNC_COMPLETED = "project.sync.completed",
  PROJECT_SYNC_FAILED = "project.sync.failed",
  ARTIFACT_CREATED = "artifact.created",
  ARTIFACT_DELETED = "artifact.deleted",
  LANGUAGE_SERVER_STARTED = "languageServer.started",
  LANGUAGE_SERVER_STOPPED = "languageServer.stopped",
  CONFIGURATION_CHANGED = "configuration.changed",
  PROJECT_CHANGED = "project.changed",
}

export interface ProjectsDiscoveredEvent extends BaseGrailsEvents {
  type: EventType.PROJECTS_DISCOVERED;
  projects: ProjectInfo[];
}

export interface ProjectChangedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_CHANGED;
  project: ProjectInfo;
}

export interface ProjectDiscoveredEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_DISCOVERED;
  project: ProjectInfo;
}

export interface ProjectLoadedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_LOADED;
  project: ProjectInfo;
}

export interface ProjectSyncStartedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_STARTED;
  project: ProjectInfo;
}

export interface ProjectSyncCompletedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_COMPLETED;
  project: ProjectInfo;
}

export interface ProjectSyncFailedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_FAILED;
  project: ProjectInfo;
  error: string;
}

export interface ArtifactCreatedEvent extends BaseGrailsEvents {
  type: EventType.ARTIFACT_CREATED;
  project: ProjectInfo;
  artifact: string;
}

export interface ArtifactDeletedEvent extends BaseGrailsEvents {
  type: EventType.ARTIFACT_DELETED;
  project: ProjectInfo;
  artifact: string;
}

// Create a union type for all events (this is the key!)
export type GrailsEvent =
  | ProjectsDiscoveredEvent
  | ProjectChangedEvent
  | ProjectDiscoveredEvent
  | ProjectLoadedEvent
  | ProjectSyncStartedEvent
  | ProjectSyncCompletedEvent
  | ProjectSyncFailedEvent
  | ArtifactCreatedEvent
  | ArtifactDeletedEvent;

// Helper type for event handlers
export type EventHandler<T extends GrailsEvent> = (event: T) => void;
