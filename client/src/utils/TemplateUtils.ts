/**
 * Utility functions for string templating and formatting
 */

/**
 * Replaces numbered placeholders {0}, {1}, {2}, etc. with provided arguments
 * @param template - Template string with numbered placeholders
 * @param args - Arguments to replace placeholders with
 * @returns Formatted string with placeholders replaced
 *
 * @example
 * formatTemplate("Error in {0}: {1}", "Server", "Connection failed")
 * // Returns: "Error in Server: Connection failed"
 */
export function formatTemplate(template: string, ...args: string[]): string {
  return template.replace(/{(\d+)}/g, (match, index) => {
    const argIndex = parseInt(index, 10);
    return args[argIndex] !== undefined ? args[argIndex] : match;
  });
}

/**
 * Replaces named placeholders {key} with values from params object
 * @param template - Template string with named placeholders
 * @param params - Object with key-value pairs for replacement
 * @returns Formatted string with placeholders replaced
 *
 * @example
 * formatTemplateNamed("Error in {source}: {message}", { source: "Server", message: "Connection failed" })
 * // Returns: "Error in Server: Connection failed"
 */
export function formatTemplateNamed(template: string, params: Record<string, string>): string {
  return template.replace(/{(\w+)}/g, (match, key) => {
    return params[key] !== undefined ? params[key] : match;
  });
}
