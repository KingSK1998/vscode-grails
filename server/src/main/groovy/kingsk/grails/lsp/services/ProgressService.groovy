package kingsk.grails.lsp.services

import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.ErrorSeverity
import kingsk.grails.lsp.model.ErrorSource
import groovy.util.logging.Slf4j
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either

@Slf4j
class ProgressService {

    private static final String TOKEN = "GLS-SERVER-SETUP"
	private final GrailsService service

	ProgressService(GrailsService service) {
		this.service = service
	}

    /**
     * Signal the start of a long-running operation.
     */
    void begin(String title = "Indexing", String message = "Starting…") {
        send(new WorkDoneProgressBegin(
            title: title,
            message: message,
            percentage: 0,
            cancellable: false
        ))
    }

    /**
     * Update progress with a message and percentage.
     */
    void update(String message, int percentage) {
        send(new WorkDoneProgressReport(
            message: message,
            percentage: percentage
        ))
    }

    /**
     * Signal completion of the operation.
     */
    void end(String message = "Completed") {
        send(new WorkDoneProgressEnd(message: message))
    }


    /**
     * Show an error popup to the user.
     */
    void error(String message, Exception e = null) {
        service.errorService.handleError(message, e, ErrorSource.GENERAL, ErrorSeverity.ERROR)
    }

    /**
     * Show an informational popup to the user.
     */
	void info(String message) {
        service.errorService.handleError(message, null, ErrorSource.GENERAL, ErrorSeverity.INFO)
	}

    /**
     * Show a warning popup to the user.
     */
    void warn(String message) {
        service.errorService.handleError(message, null, ErrorSource.GENERAL, ErrorSeverity.WARNING)
    }

    /**
     * Helper to wrap and send any WorkDoneProgress value.
     */
    private void send(WorkDoneProgressNotification workDoneProgressValue) {
        def params = new ProgressParams()
        params.setToken(TOKEN)
        params.setValue(Either.forLeft(workDoneProgressValue))
        if (service.client != null) {
            log.debug "Progress [$TOKEN] → ${workDoneProgressValue.getClass().simpleName} $workDoneProgressValue"
            service.client.notifyProgress(params)
        }
    }
}
