import type { LanguageClient } from "vscode-languageclient/node";
import type { ProjectDTO, ProjectPatchDTO } from "../../shared/protocol/project";
import type { ProjectStore } from "../../store/ProjectStore";

export function registerProjectHandlers(client: LanguageClient, store: ProjectStore): void {
  client.onNotification("grails/projectsDiscovered", (projects: ProjectDTO[]) => {
    store.setAll(projects);
  });

  client.onNotification("grails/projectUpdated", (project: ProjectDTO) => {
    store.set(project);
  });

  client.onNotification("grails/projectPatched", (patch: ProjectPatchDTO) => {
    store.patch(patch);
  });
}
