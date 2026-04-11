package kingsk.grails.lsp.services

import groovy.util.logging.Slf4j
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

@Slf4j
class ProgressService implements LanguageClientAware {

    private static final String TOKEN = "GLS-SERVER-SETUP"
	private LanguageClient client

	@Override
	void connect(LanguageClient client) {
		this.client = client
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
        client.showMessage(new MessageParams(
            MessageType.Error,
            e ? "$message: ${e.message}" : message
        ))
    }

    /**
     * Show an informational popup to the user.
     */
	void info(String message) {
		client.showMessage(new MessageParams(
				MessageType.Info,
				message
		))
	}

    /**
     * Show a warning popup to the user.
     */
    void warn(String message) {
        client.showMessage(new MessageParams(
            MessageType.Warning,
            message
        ))
    }

    /**
     * Helper to wrap and send any WorkDoneProgress value.
     */
    private void send(WorkDoneProgressNotification workDoneProgressValue) {
        def params = new ProgressParams()
        params.setToken(TOKEN)
        params.setValue(Either.forLeft(workDoneProgressValue))
        log.debug "Progress [$TOKEN] → ${workDoneProgressValue.getClass().simpleName} $workDoneProgressValue"
        client.notifyProgress(params)
    }
}
