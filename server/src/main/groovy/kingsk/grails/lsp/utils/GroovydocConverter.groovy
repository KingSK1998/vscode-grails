package kingsk.grails.lsp.utils

import groovy.lang.groovydoc.Groovydoc

/**
 * Groovydoc to Markdown converter for LSP integration.
 */
class GroovydocConverter {
	
	/**
	 * Converts Groovydoc comments to Markdown format suitable for hover information.
	 * Optimized for LSP usage - prioritizes speed over feature completeness.
	 *
	 * @param groovydoc The Groovydoc object to convert
	 * @return A Markdown-formatted string representation of the Groovydoc
	 */
	static String groovydocToDescription(Groovydoc groovydoc) {
		if (!groovydoc?.present || !groovydoc.content) {
			return ""
		}
		
		String content = groovydoc.content.trim()
		if (content.isEmpty()) {
			return ""
		}
		
		return convertToMarkdown(content)
	}
	
	/**
	 * Core conversion with single-pass processing for optimal performance
	 */
	private static String convertToMarkdown(String content) {
		// Remove comment markers efficiently
		if (content.startsWith('/**')) {
			content = content.substring(3)
		}
		if (content.endsWith('*/')) {
			content = content.substring(0, content.length() - 2)
		}
		
		String[] lines = content.split('\n')
		StringBuilder result = new StringBuilder(content.length() + (lines.length * 10))
		
		boolean inMainDescription = true
		boolean inCodeBlock = false
		int emptyLineCount = 0
		
		for (String line : lines) {
			// Clean line in single operation
			String cleaned = cleanLine(line)
			
			// Handle empty lines
			if (cleaned.empty) {
				emptyLineCount++
				if (emptyLineCount == 1 && result.length() > 0) {
					result.append('\n')
				}
				continue
			}
			emptyLineCount = 0
			
			// Process code blocks first (highest priority)
			if (cleaned.contains('<pre>')) {
				inCodeBlock = true
				result.append('\n\n```groovy\n')
				continue
			}
			if (cleaned.contains('</pre>')) {
				inCodeBlock = false
				result.append('\n```\n\n')
				continue
			}
			
			// Skip HTML processing in code blocks
			if (inCodeBlock) {
				result.append(cleaned).append('\n')
				continue
			}
			
			// Handle Javadoc tags
			if (cleaned.startsWith('@')) {
				if (inMainDescription) {
					result.append('\n\n')
					inMainDescription = false
				}
				
				if (processTag(cleaned, result)) {
					continue
				}
			}
			
			// Process regular content
			String processed = processContent(cleaned)
			if (!processed.empty) {
				result.append(processed).append('\n')
			}
		}
		
		return normalizeOutput(result.toString())
	}
	
	/**
	 * Clean line with minimal operations
	 */
	private static String cleanLine(String line) {
		// Remove leading whitespace and asterisk in one pass
		int start = 0
		int length = line.length()
		
		// Skip leading whitespace
		while (start < length && Character.isWhitespace(line.charAt(start))) {
			start++
		}
		
		// Skip asterisk and following space if present
		if (start < length && line.charAt(start) == (char) '*') {
			start++
			if (start < length && line.charAt(start) == (char) ' ') {
				start++
			}
		}
		
		return start < length ? line.substring(start) : ""
	}
	
	/**
	 * Process Javadoc tags with simple string operations
	 */
	private static boolean processTag(String line, StringBuilder result) {
		if (line.startsWith('@param ')) {
			int spaceIndex = line.indexOf(' ', 7)
			if (spaceIndex > 7) {
				String paramName = line.substring(7, spaceIndex)
				String description = line.substring(spaceIndex + 1)
				result.append("**`").append(paramName).append("`** — ")
						.append(processSimpleHtml(description)).append('\n\n')
			} else {
				result.append("**Parameter:** ").append(line.substring(7)).append('\n\n')
			}
			return true
		}
		
		if (line.startsWith('@return ')) {
			result.append("**Returns:** ")
					.append(processSimpleHtml(line.substring(8)))
					.append('\n\n')
			return true
		}
		
		if (line.startsWith('@throws ') || line.startsWith('@exception ')) {
			int tagLength = line.startsWith('@throws ') ? 8 : 11
			String remainder = line.substring(tagLength)
			int spaceIndex = remainder.indexOf(' ')
			
			if (spaceIndex > 0) {
				String exceptionName = remainder.substring(0, spaceIndex)
				String description = remainder.substring(spaceIndex + 1)
				result.append("**Throws `").append(exceptionName).append("`** — ")
						.append(processSimpleHtml(description)).append('\n\n')
			} else {
				result.append("**Throws `").append(remainder).append("`**\n\n")
			}
			return true
		}
		
		if (line.startsWith('@deprecated')) {
			String reason = line.length() > 11 ? line.substring(12) : ""
			result.append("**⚠️ Deprecated**")
			if (!reason.isEmpty()) {
				result.append(" — ").append(processSimpleHtml(reason))
			}
			result.append('\n\n')
			return true
		}
		
		// Handle other common tags simply
		String[] tags = ["@see ", "@since ", "@author ", "@version "]
		String[] labels = ["**See:** ", "**Since:** ", "**Author:** ", "**Version:** "]
		
		for (int i = 0; i < tags.length; i++) {
			if (line.startsWith(tags[i])) {
				result.append(labels[i])
						.append(processSimpleHtml(line.substring(tags[i].length())))
						.append('\n\n')
				return true
			}
		}
		
		return false
	}
	
	/**
	 * Process regular content with essential HTML conversion
	 */
	private static String processContent(String line) {
		return processSimpleHtml(line)
	}
	
	/**
	 * Minimal HTML processing for performance - handles most common cases
	 */
	private static String processSimpleHtml(String text) {
		if (text.indexOf('<') == -1 && text.indexOf('{') == -1 && text.indexOf('&') == -1) {
			return text // Fast path for plain text
		}
		
		// Process inline code first
		text = text.replace("{@code ", "`").replace("}", "`")
		text = text.replace("{@literal ", "").replace("}", "")
		text = text.replace("<code>", "`").replace("</code>", "`")
		
		// Basic formatting
		text = text.replace("<em>", "_").replace("</em>", "_")
		text = text.replace("<i>", "_").replace("</i>", "_")
		text = text.replace("<strong>", "**").replace("</strong>", "**")
		text = text.replace("<b>", "**").replace("</b>", "**")
		
		// Line breaks
		text = text.replace("<br>", "  \n").replace("<br/>", "  \n").replace("<br />", "  \n")
		
		// Lists
		text = text.replace("<li>", "- ")
		
		// Common entities
		text = text.replace("<", "<").replace("&gt;", ">")
		text = text.replace("&", "&").replace("&quot;", "\"")
		text = text.replace("&nbsp;", " ")
		
		// Remove remaining simple HTML tags
		return removeSimpleHtmlTags(text)
	}
	
	/**
	 * Remove HTML tags with simple character scanning
	 */
	private static String removeSimpleHtmlTags(String text) {
		if (text.indexOf('<') == -1) {
			return text
		}
		
		StringBuilder result = new StringBuilder(text.length())
		boolean inTag = false
		
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i)
			if (c == (char) '<') {
				inTag = true
			} else if (c == (char) '>') {
				inTag = false
			} else if (!inTag) {
				result.append(c)
			}
		}
		
		return result.toString()
	}
	
	/**
	 * Minimal output normalization
	 */
	private static String normalizeOutput(String text) {
		// Remove excessive blank lines and trim
		return text.replaceAll("\n{3,}", "\n\n").trim()
	}
}
