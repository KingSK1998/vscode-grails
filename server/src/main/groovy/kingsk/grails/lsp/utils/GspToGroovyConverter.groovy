package kingsk.grails.lsp.utils

import kingsk.grails.lsp.model.TextFile

/**
 * Converts Grails GSP files to valid Groovy scripts by preserving line and column numbers.
 * Non-Groovy parts (HTML) are replaced with whitespaces.
 * Groovy Scriptlets (<% %>, <%= %>) and GStrings (${}) are preserved as Groovy expressions.
 */
class GspToGroovyConverter {

    static String convertToVirtualGroovy(String gspText) {
        StringBuilder groovyScript = new StringBuilder()
        int i = 0
        int n = gspText.length()

        boolean inScriptlet = false
        boolean inGString = false

        while (i < n) {
            char c = gspText.charAt(i)
            
            // Handle comments %{-- --}%
            if (!inScriptlet && !inGString && i + 3 < n && c == '%' && gspText.substring(i, i + 4) == '%{--') {
                int endIdx = gspText.indexOf('--}%', i + 4)
                if (endIdx != -1) {
                    groovyScript.append(getWhitespaces(gspText.substring(i, endIdx + 4)))
                    i = endIdx + 4
                    continue
                }
            }

            // Detect scriptlets <% %>
            if (!inScriptlet && !inGString && i + 1 < n && c == '<' && gspText.charAt(i + 1) == '%') {
                inScriptlet = true
                groovyScript.append('  ') // Replace <% with spaces
                i += 2
                // Special case for <%= 
                if (i < n && gspText.charAt(i) == '=') {
                    groovyScript.append(' ')
                    i++
                }
                continue
            }
            if (inScriptlet && i + 1 < n && c == '%' && gspText.charAt(i + 1) == '>') {
                inScriptlet = false
                groovyScript.append('  ') // Replace %> with spaces
                i += 2
                continue
            }

            // Detect GString ${ }
            if (!inScriptlet && !inGString && i + 1 < n && c == '$' && gspText.charAt(i + 1) == '{') {
                inGString = true
                groovyScript.append('  ') // Replace ${ with spaces
                i += 2
                continue
            }
            if (inGString && c == '}') {
                inGString = false
                groovyScript.append(' ') // Replace } with space
                i += 1
                continue
            }

            if (inScriptlet || inGString) {
                groovyScript.append(c)
                i++
            } else {
                if (c == '\n' || c == '\r') {
                    groovyScript.append(c)
                } else {
                    groovyScript.append(' ')
                }
                i++
            }
        }

        return groovyScript.toString()
    }

    private static String getWhitespaces(String text) {
        StringBuilder sb = new StringBuilder()
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i)
            if (c == '\n' || c == '\r') {
                sb.append(c)
            } else {
                sb.append(' ')
            }
        }
        return sb.toString()
    }
}
