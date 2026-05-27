package com.example.accounts.error;

import com.example.shared.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handleAccountNotFound(AccountNotFoundException ex) {

        return Map.of(
                "status", 404,
                "error", "Not Found",
                "message", ex.getMessage()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.BadRequest.class)
    public ResponseEntity<ErrorResponse> handleBadRequestFromOtherService(
            HttpClientErrorException.BadRequest ex
    ) {
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse(extractMessage(ex)));
    }

    private String extractMessage(HttpClientErrorException ex) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            ErrorResponse error = mapper.readValue(
                    ex.getResponseBodyAsString(),
                    ErrorResponse.class
            );

            return error.message();
        } catch (Exception ignored) {
            return ex.getResponseBodyAsString();
        }
    }
}