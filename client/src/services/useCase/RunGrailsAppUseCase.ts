import type { ErrorService } from "../errors/ErrorService";
import { ErrorSource } from "../errors/errorTypes";
import type { GradleService } from "../gradle/GradleService";
import type { ProjectService } from "../workspace/ProjectService";

export class RunGrailsAppUseCase {
  private readonly projectService: ProjectService;
  private readonly gradleService: GradleService;
  private readonly errorService: ErrorService;

  constructor(
    projectService: ProjectService,
    gradleService: GradleService,
    errorService: ErrorService
  ) {
    this.projectService = projectService;
    this.gradleService = gradleService;
    this.errorService = errorService;
  }

  public async execute(): Promise<void> {
    const activeProject = this.projectService.getActiveProject();

    if (!activeProject) {
      this.errorService.handleError(
        "No active project selected.",
        new Error("Active project is undefined"),
        ErrorSource.ProjectService
      );
      return;
    }

    await this.gradleService.runGrailsApp(activeProject);
  }
}
