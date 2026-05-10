import type { ProjectDTO } from "../../shared/protocol/project";

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
  TREE_REFRESH = "tree.refresh",
}

export interface ProjectsDiscoveredEvent extends BaseGrailsEvents {
  type: EventType.PROJECTS_DISCOVERED;
  projects: ProjectDTO[];
}

export interface ProjectChangedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_CHANGED;
  project: ProjectDTO;
}

export interface ProjectDiscoveredEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_DISCOVERED;
  project: ProjectDTO;
}

export interface ProjectLoadedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_LOADED;
  project: ProjectDTO;
}

export interface ProjectSyncStartedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_STARTED;
  project: ProjectDTO;
}

export interface ProjectSyncCompletedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_COMPLETED;
  project: ProjectDTO;
}

export interface ProjectSyncFailedEvent extends BaseGrailsEvents {
  type: EventType.PROJECT_SYNC_FAILED;
  project: ProjectDTO;
  error: string;
}

export interface ArtifactCreatedEvent extends BaseGrailsEvents {
  type: EventType.ARTIFACT_CREATED;
  project: ProjectDTO;
  artifact: string;
}

export interface ArtifactDeletedEvent extends BaseGrailsEvents {
  type: EventType.ARTIFACT_DELETED;
  project: ProjectDTO;
  artifact: string;
}

export interface TreeRefreshEvent extends BaseGrailsEvents {
  type: EventType.TREE_REFRESH;
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
  | ArtifactDeletedEvent
  | TreeRefreshEvent;

// Helper type for event handlers
export type EventHandler<T extends GrailsEvent> = (event: T) => void;

export interface GrailsEventMap {
  [EventType.PROJECTS_DISCOVERED]: ProjectsDiscoveredEvent;
  [EventType.PROJECT_CHANGED]: ProjectChangedEvent;
  [EventType.PROJECT_DISCOVERED]: ProjectDiscoveredEvent;
  [EventType.PROJECT_LOADED]: ProjectLoadedEvent;
  [EventType.PROJECT_SYNC_STARTED]: ProjectSyncStartedEvent;
  [EventType.PROJECT_SYNC_COMPLETED]: ProjectSyncCompletedEvent;
  [EventType.PROJECT_SYNC_FAILED]: ProjectSyncFailedEvent;
  [EventType.ARTIFACT_CREATED]: ArtifactCreatedEvent;
  [EventType.ARTIFACT_DELETED]: ArtifactDeletedEvent;
  [EventType.TREE_REFRESH]: TreeRefreshEvent;
}
