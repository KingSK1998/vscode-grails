import type { ProjectInfo } from "../features/models/modelTypes";
import { ProjectType } from "../features/models/modelTypes";
import type { ProjectDTO } from "./protocol/project";

export class ProjectMapper {
  static toProjectInfo(dto: ProjectDTO): ProjectInfo {
    return {
      ...dto,
      type: this.mapProjectType(dto.type),
      isGradleProject: true,
      isPlugin: dto.type === "grails-plugin",
      lastUpdated: Date.now(),
    };
  }

  private static mapProjectType(type: string): ProjectType {
    switch (type) {
      case "grails":
        return ProjectType.Grails;
      case "grails-plugin":
        return ProjectType.GrailsPlugin;
      case "gradle":
        return ProjectType.Groovy;
      default:
        return ProjectType.Unknown;
    }
  }
}
