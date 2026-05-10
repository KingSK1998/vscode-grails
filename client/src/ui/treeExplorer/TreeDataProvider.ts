import type { ExtensionContext } from "vscode";
import { ServiceContainer } from "../../core/container/ServiceContainer";
import { ProjectType } from "../../features/models/modelTypes";
import { ErrorSeverity, ErrorSource } from "../../services/errors/errorTypes";
import { GrailsTreeExplorer } from "./GrailsTreeExplorer";
import { TreeItemKind } from "./TreeItemKind";

/** Factory function to create appropriate tree explorer based on workspace content */
export function createProjectTreeProvider(context: ExtensionContext): GrailsTreeExplorer | null {
  console.log("🏭 createProjectTreeProvider: Started");

  try {
    console.log("🔍 TreeItemType check:", TreeItemKind.ProjectRoot);

    const container = ServiceContainer.getInstance();
    const projects = container.projectService.getProjects();

    console.log(`🔍 Analyzing ${projects.length} projects in workspace`);
    projects.forEach((p, i) => {
      console.log(`🏭   ${i + 1}. ${p.name} (${p.type}) - ${p.rootPath}`);
    });

    if (projects.length === 0) {
      console.log("⚠️ No projects found in workspace - may still be loading");
      return null;
    }

    // Categorize projects by type
    const grailsProjects = projects.filter(
      p => p.type === ProjectType.Grails || p.type === ProjectType.GrailsPlugin
    );
    const groovyProjects = projects.filter(p => p.type === ProjectType.Groovy);

    console.log(`🏭 Grails projects: ${grailsProjects.length}`);
    console.log(`🏭 Groovy projects: ${groovyProjects.length}`);

    // Select appropriate provider
    if (grailsProjects.length > 0) {
      console.log("🚀 Creating GrailsTreeExplorer");
      return new GrailsTreeExplorer(context);
    } else {
      console.log("❌ No supported projects found");

      const projectTypes = [...new Set(projects.map(p => p.type))].join(", ");
      console.log(`📋 Detected project types: ${projectTypes}`);
    }
  } catch (error) {
    ServiceContainer.getInstance().errorService.handleError(
      "Tree provider creation failed",
      error,
      ErrorSource.UI,
      ErrorSeverity.Error
    );
  }
  return null; // ✅ Gracefully handle unsupported workspaces
}

// Export tree explorers for direct use if needed
export { GrailsTreeExplorer } from "./GrailsTreeExplorer";
