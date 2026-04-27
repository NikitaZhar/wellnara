package life.wellnara.exception;

import java.util.Map;

/**
 * Exception for provider calendar validation errors.
 */
public class CalendarValidationException extends RuntimeException {

    private final Map<String, String> fieldErrors;

    public CalendarValidationException(Map<String, String> fieldErrors) {
        super("Provider calendar form contains validation errors");
        this.fieldErrors = fieldErrors;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}