import type { ProjectInfo } from "../features/models/modelTypes";
import { ProjectMapper } from "../features/projects/mappers/projectMapper";
import type { ProjectDTO, ProjectPatchDTO } from "../shared/protocol/project";

export type ProjectStoreListener = (projects: readonly ProjectInfo[]) => void;

export class ProjectStore {
  private projects = new Map<string, ProjectInfo>();
  private listeners: ProjectStoreListener[] = [];

  getAll(): ProjectInfo[] {
    return [...this.projects.values()];
  }

  get(id: string): ProjectInfo | undefined {
    return this.projects.get(id);
  }

  set(project: ProjectDTO): void {
    const mapped = ProjectMapper.toProjectInfo(project);
    this.projects.set(mapped.id, mapped);
    this.emit();
  }

  setAll(projects: ProjectDTO[]): void {
    this.projects.clear();
    projects.forEach(p => {
      const mapped = ProjectMapper.toProjectInfo(p);
      this.projects.set(mapped.id, mapped);
    });
    this.emit();
  }

  patch(patch: ProjectPatchDTO): void {
    const existing = this.projects.get(patch.projectId);
    if (!existing) return;

    const currentDto: ProjectDTO = {
      id: existing.id,
      rootPath: existing.rootPath,
      name: existing.name,
      type: existing.type as unknown as string,
      dependencies: existing.dependencies as unknown as ProjectDTO["dependencies"],
    };

    if (existing.grailsVersion) currentDto.grailsVersion = existing.grailsVersion;
    if (existing.groovyVersion) currentDto.groovyVersion = existing.groovyVersion;
    if (existing.javaVersion) currentDto.javaVersion = existing.javaVersion;
    if (existing.pluginVersion) currentDto.pluginVersion = existing.pluginVersion;
    if (existing.artifact) currentDto.artifact = existing.artifact;

    const updatedDto: ProjectDTO = { ...currentDto, ...patch.changes };
    const updated = ProjectMapper.toProjectInfo(updatedDto);
    this.projects.set(updated.id, updated);
    this.emit();
  }

  subscribe(listener: ProjectStoreListener): void {
    this.listeners.push(listener);
  }

  private emit(): void {
    const data = this.getAll();
    this.listeners.forEach(l => l(data));
  }
}
