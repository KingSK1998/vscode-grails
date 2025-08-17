package kingsk.grails.lsp.services

import groovy.util.logging.Slf4j
import org.eclipse.lsp4j.*
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

@Slf4j
class ProgressReportService implements LanguageClientAware {
	
	private LanguageClient client
	
	@Override
	void connect(LanguageClient client) {
		this.client = client
	}
	
	void sendProgressBegin() {
		def begin = new WorkDoneProgressBegin(
				title: "Indexing",
				message: "Started indexing...",
				percentage: 0,
				cancellable: false
		)
		
		def params = new ProgressParams()
		params.token = "GLS-SERVER-SETUP"
		params.value = Either.forLeft(begin)
		
		log.info "Sending progress begin: [Percentage: 0%, Message: ${begin.message}]"
		client.notifyProgress(params)
	}
	
	void sendProgressReport(String message, int percentage) {
		def report = new WorkDoneProgressReport(message: message, percentage: percentage)
		def params = new ProgressParams()
		params.token = "GLS-SERVER-SETUP"
		params.value = Either.forLeft(report)
		
		log.info "Sending progress report: [Percentage: ${percentage}%, Message: ${message}]"
		client.notifyProgress(params)
	}
	
	void sendProgressEnd() {
		def end = new WorkDoneProgressEnd(message: "Indexing completed")
		def params = new ProgressParams()
		params.token = "GLS-SERVER-SETUP"
		params.value = Either.forLeft(end)
		
		log.info "Sending progress end: [Percentage: 100%, Message: ${end.message}]"
		client.notifyProgress(params)
	}
	
	void notifyError(String message, Exception e) {
		client.showMessage(new MessageParams(
				MessageType.Error,
				"$message: ${e.message}"
		))
	}
	
	void notifyMessage(String message) {
		client.showMessage(new MessageParams(
				MessageType.Info,
				message
		))
	}
	
	void notifyWarning(String message) {
		client.showMessage(new MessageParams(
				MessageType.Warning,
				message
		))
	}
}
