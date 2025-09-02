import { env, ExtensionContext, Uri, window } from "vscode";
import path = require("path");
import { ServiceContainer } from "../../core/container/ServiceContainer";
import { GrailsTreeExplorer } from "./GrailsTreeExplorer";
import { GroovyTreeExplorer } from "./GroovyTreeExplorer";
import { TreeExplorerBase } from "./TreeExplorerBase";
import { ProjectType } from "../../features/models/modelTypes";

/** Factory function to create appropriate tree explorer based on workspace content */
export function createProjectTreeProvider(context: ExtensionContext): TreeExplorerBase | null {
  const container = ServiceContainer.getInstance();
  const projects = container.projectService.getProjects();

  console.log(`🔍 Analyzing ${projects.length} projects in workspace`);

  if (projects.length === 0) {
    console.log("⚠️ No projects found in workspace - may still be loading");
    return null;
  }

  // Categorize projects by type
  const grailsProjects = projects.filter(
    p => p.type === ProjectType.Grails || p.type === ProjectType.GrailsPlugin
  );
  const groovyProjects = projects.filter(p => p.type === ProjectType.Groovy);
  const otherProjects = projects.filter(
    p =>
      p.type !== ProjectType.Grails &&
      p.type !== ProjectType.GrailsPlugin &&
      p.type !== ProjectType.Groovy
  );


  // Log project composition for debugging
    if (grailsProjects.length > 0) {
        console.log(`🚀 Found ${grailsProjects.length} Grails project(s):`, grailsProjects.map(p => p.name));
    }
    if (groovyProjects.length > 0) {
        console.log(`☕ Found ${groovyProjects.length} Groovy project(s):`, groovyProjects.map(p => p.name));
    }
    if (otherProjects.length > 0) {
        console.log(`📁 Found ${otherProjects.length} other project(s):`, otherProjects.map(p => `${p.name} (${p.type})`));
    }

    // Select appropriate provider
    if (grailsProjects.length > 0) {
        console.log("🌳 Using GrailsTreeExplorer");
        return new GrailsTreeExplorer(context);
    } else if (groovyProjects.length > 0) {
        console.log("🌳 Using GroovyTreeExplorer");
        return new GroovyTreeExplorer(context);
    } else {
        // ✅ No supported projects - inform user and return null
        console.log("❌ No supported projects found");
        
        const projectTypes = [...new Set(projects.map(p => p.type))].join(", ");
        console.log(`📋 Detected project types: ${projectTypes}`);
        
        return null; // ✅ Gracefully handle unsupported workspaces
    }
  }

// Export tree explorers for direct use if needed
export { TreeExplorerBase } from "./TreeExplorerBase";
export { GrailsTreeExplorer } from "./GrailsTreeExplorer";
export { GroovyTreeExplorer } from "./GroovyTreeExplorer";
