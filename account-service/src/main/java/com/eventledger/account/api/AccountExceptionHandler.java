package com.eventledger.account.api;

import com.eventledger.account.service.AccountNotFoundException;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class AccountExceptionHandler extends ResponseEntityExceptionHandler {

    private final ObjectProvider<Tracer> tracer;

    private ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setType(URI.create("urn:event-ledger:" + type));
        p.setTitle(title);
        Tracer t = tracer.getIfAvailable();
        if (t != null && t.currentSpan() != null) {
            p.setProperty("traceId", t.currentSpan().context().traceId());
        }
        return p;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail handleNotFound(AccountNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "account-not-found", "Account Not Found", ex.getMessage());
    }

    // Message stays generic: an unexpected exception's message may leak
    // internals; the traceId property links the response to the full log line.
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal-error",
            "Internal Server Error", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest()
            .body(problem(HttpStatus.BAD_REQUEST, "validation", "Validation Failed", detail));
    }
}
