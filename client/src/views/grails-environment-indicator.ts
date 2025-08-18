import { StatusBarAlignment, StatusBarItem, window } from "vscode";

type GrailsEnvironment = "development" | "test" | "production";

export class GrailsEnvironmentIndicator {
  createEnvironmentBadge(environment: GrailsEnvironment): StatusBarItem {
    const item = window.createStatusBarItem(StatusBarAlignment.Left, 95);

    const envColors = {
      development: "#48b946",
      test: "#ffa500",
      production: "#dc3545",
    };

    item.text = `$(symbol-misc) ${environment.toUpperCase()}`;
    item.color = envColors[environment] || "#6c757d";
    item.tooltip = `Grails Environment: ${environment}`;
    item.command = "grails.switchEnvironment";

    return item;
  }
}
