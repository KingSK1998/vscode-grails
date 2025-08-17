// Test configuration for Grails LSP
environments {
    // Test environment configuration
    test {
        // Mock project paths
        mockProjects {
            grailsApp = 'src/test/resources/mock-projects/grails-app'
            groovyProject = 'src/test/resources/mock-projects/groovy-project'
            gradleProject = 'src/test/resources/mock-projects/gradle-project'
        }
        
        // LSP test settings
        lsp {
            // Timeout settings for async operations (in milliseconds)
            timeouts {
                completion = 5000
                definition = 3000
                hover = 2000
                references = 5000
                diagnostics = 10000
            }
        }
    }
}