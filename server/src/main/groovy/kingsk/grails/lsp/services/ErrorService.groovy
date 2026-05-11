package kingsk.grails.lsp.services

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import kingsk.grails.lsp.GrailsService
import kingsk.grails.lsp.model.enums.ErrorSeverity
import kingsk.grails.lsp.model.enums.ErrorSource
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType

/**
 * Centralized error handler for the Grails Language Server.
 * Logs to Slf4j and notifies the client via LSP.
 */
@Slf4j
@CompileStatic
class ErrorService {
    private final GrailsService service

    ErrorService(GrailsService service) {
        this.service = service
    }

    /**
     * Handle an error with logging and client notification.
     * @param message Description of what happened
     * @param error The exception if any
     * @param source The component that triggered the error
     * @param severity Importance level
     */
    void handleError(String message, Throwable error = null, ErrorSource source = ErrorSource.GENERAL, ErrorSeverity severity = ErrorSeverity.ERROR) {
        String logMessage = "[${source}] ${severity}: ${message}"
        if (error != null) {
            logMessage += " | Error: ${error.message}"
        }
        
        // 1. Log via Slf4j (Server-side)
        switch (severity) {
            case ErrorSeverity.CRITICAL:
            case ErrorSeverity.ERROR:
                log.error(logMessage, error)
                break
            case ErrorSeverity.WARNING:
                log.warn(logMessage, error)
                break
            case ErrorSeverity.INFO:
                log.info(logMessage)
                break
        }

        // 2. Notify Client via LSP (if connected)
        if (service.client != null) {
            MessageType type = mapSeverityToMessageType(severity)
            service.client.showMessage(new MessageParams(type, logMessage))
        }
    }

    /**
     * Helper for try-catch blocks
     */
    void handle(Throwable error, ErrorSource source = ErrorSource.GENERAL) {
        handleError(error.message ?: "An unexpected error occurred", error, source, ErrorSeverity.ERROR)
    }

    private MessageType mapSeverityToMessageType(ErrorSeverity severity) {
        switch (severity) {
            case ErrorSeverity.CRITICAL:
            case ErrorSeverity.ERROR:
                return MessageType.Error
            case ErrorSeverity.WARNING:
                return MessageType.Warning
            case ErrorSeverity.INFO:
                return MessageType.Info
            default:
                return MessageType.Log
        }
    }
}
