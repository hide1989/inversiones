package co.com.david.inversiones.api.error;

import co.com.david.inversiones.model.exception.DomainException;
import co.com.david.inversiones.model.exception.InvalidCredentialsException;
import co.com.david.inversiones.model.exception.SignalRefreshInProgressException;
import co.com.david.inversiones.model.exception.TransactionNotFoundException;
import co.com.david.inversiones.model.exception.UnresolvedTickerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.List;
import java.util.UUID;

/**
 * Single point translating every exception this API can raise into an RFC 7807
 * {@link ProblemDetail}.
 *
 * <p>The mapping is deliberately narrow: a few specific domain exceptions get their own status
 * ({@link TransactionNotFoundException} and {@link UnresolvedTickerException} to 404,
 * {@link InvalidCredentialsException} to 401, {@link SignalRefreshInProgressException} to 409)
 * because those are cases a client must be able to branch on. Every other {@link DomainException}
 * is a business-rule rejection of the request's content, which is a 400 regardless of which rule
 * fired — adding a bespoke status per exception subtype would just make
 * the client's error handling brittle to a domain refactor. Anything not a {@link DomainException}
 * at all is a bug, not a rejected request, so it is a 500 with a correlation id the operator can
 * grep for in the log; the stack trace itself is logged, never returned, so an unmapped failure
 * cannot leak internals to a caller.
 *
 * <p>This replaces the hand-built 401 JSON string {@code SecurityConfig} wrote directly in Task 8
 * ({@code AuthenticationEntryPoint}) is unrelated — it fires before a request reaches a
 * controller, so it cannot go through this advice, and keeps its own minimal {@code ProblemDetail}
 * body. Every exception a controller or its validation can raise, this class is now the only
 * place that turns it into a response: Task 8 had no controller in production code to raise
 * anything else yet.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(UnresolvedTickerException.class)
    public ProblemDetail handleUnresolvedTicker(UnresolvedTickerException exception) {
        return problemDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException exception) {
        return problemDetail(HttpStatus.UNAUTHORIZED, exception.getMessage());
    }

    @ExceptionHandler(SignalRefreshInProgressException.class)
    public ProblemDetail handleSignalRefreshInProgress(SignalRefreshInProgressException exception) {
        return problemDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomainException(DomainException exception) {
        return problemDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * Catches anything not already handled above: a bug, not a rejected request. The correlation
     * id ties the 500 response to the full stack trace in the log, without ever putting the trace
     * itself in the body.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        String correlationId = UUID.randomUUID().toString();
        log.error("Unhandled exception [correlationId={}]", correlationId, exception);
        ProblemDetail problem = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setProperty("correlationId", correlationId);
        return problem;
    }

    /**
     * Overrides Spring's default handling of a bean-validation failure to list which fields failed,
     * rather than the framework's default body shape, so every 400 in this API — domain rejection
     * or input validation — carries the same {@link ProblemDetail} envelope.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers, HttpStatusCode status,
            WebRequest request) {
        List<String> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .toList();
        ProblemDetail problem = problemDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    private ProblemDetail problemDetail(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
