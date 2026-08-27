package com.example.ledgercore.exception;

import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Provides centralized exception handling for the LedgerCore REST API.
 *
 * <p>The handler converts application and validation exceptions into
 * consistent HTTP responses instead of allowing controllers to handle
 * exceptions individually.</p>
 *
 * <p>Centralized exception handling keeps controllers focused on HTTP request
 * processing while ensuring that clients receive predictable error responses.</p>
 *
 * @author Suleman Agasimani
 * @since 1.0
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles requests for customers that do not exist.
     *
     * @param exception exception raised when the requested customer is absent
     * @return HTTP 404 response containing the error details
     */
    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCustomerNotFound(
            @NonNull CustomerNotFoundException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * Handles requests for accounts that do not exist.
     *
     * <p>Converts an {@link AccountNotFoundException} into a standardized
     * HTTP 404 response so that missing accounts are reported as a
     * client-side resource-not-found condition rather than an internal
     * server error.</p>
     *
     * @param exception exception raised when the requested account is absent
     * @return HTTP 404 response containing the account error details
     */
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(errorResponse);
    }

    /**
     * Handles validation failures occurring in request DTOs.
     *
     * <p>When a request contains invalid data and the controller uses
     * {@code @Valid}, Spring raises a {@link MethodArgumentNotValidException}.
     * This handler converts the individual field errors into a single,
     * client-readable error message.</p>
     *
     * @param exception validation exception containing the invalid fields
     * @return HTTP 400 response containing the validation error details
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            @NonNull MethodArgumentNotValidException exception) {

        String message = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error ->
                        error.getField() + ": " + error.getDefaultMessage()
                )
                .collect(Collectors.joining(", "));

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                message,
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errorResponse);
    }


    /**
     * Handles attempts to create a user with a username that is already registered.
     *
     * <p>Returns HTTP 409 Conflict to indicate that the requested resource
     * cannot be created because the username already exists.</p>
     *
     * @param exception exception containing the duplicate username error message
     * @return HTTP 409 response containing the error details
     */
    @ExceptionHandler(DuplicateUsernameException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUsername(
            DuplicateUsernameException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    /**
     * Handles invalid state transitions within LedgerCore resources.
     *
     * <p>This exception is used when an operation is valid in general but
     * cannot be performed because the resource is currently in an incompatible
     * state, such as attempting to freeze an already closed account.</p>
     *
     * @param exception exception containing the invalid-state message
     * @return HTTP 409 response containing the error details
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException exception) {

        ErrorResponse errorResponse = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                exception.getMessage(),
                LocalDateTime.now()
        );

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(errorResponse);
    }

    /**
     * Represents the standard error response returned by LedgerCore.
     *
     * @param status HTTP status code associated with the error
     * @param message description of the error
     * @param timestamp time at which the error occurred
     */
    public record ErrorResponse(
            int status,
            String message,
            LocalDateTime timestamp
    ) {
    }
}