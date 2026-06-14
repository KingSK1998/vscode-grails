const fs = require('fs');
const path = require('path');

const testDir = "d:/Grails_Framework_Support_Extension/vscode-gng-support/server/src/test/groovy/kingsk/grails/lsp";

function walkDir(dir, callback) {
    if (!fs.existsSync(dir)) return;
    fs.readdirSync(dir).forEach(f => {
        let dirPath = path.join(dir, f);
        let isDirectory = fs.statSync(dirPath).isDirectory();
        isDirectory ? walkDir(dirPath, callback) : callback(dirPath);
    });
}

walkDir(testDir, function(filePath) {
    if (filePath.endsWith(".groovy")) {
        let text = fs.readFileSync(filePath, 'utf8');
        const originalText = text;
        
        // Replace new XProvider(service) with new XProvider(service, service, service)
        // Also GrailsDiagnosticService(service) -> no change needed, as it only takes 1 service now.
        // Wait, what providers did we change?
        // All that extend BaseProvider, plus GrailsDependencyProvider, GrailsTestDiscoveryProvider, GrailsGormSqlProvider.
        // Let's just do it for all classes ending in Provider.
        text = text.replace(/new ([A-Za-z]+Provider)\(service\)/g, "new $1(service, service, service)");
        
        if (originalText !== text) {
            fs.writeFileSync(filePath, text);
            console.log(`Updated ${path.basename(filePath)}`);
        }
    }
});
