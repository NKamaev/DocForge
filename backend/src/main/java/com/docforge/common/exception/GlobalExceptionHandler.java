package com.docforge.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler({BadCredentialsException.class, AuthenticationException.class})
    public ProblemDetail handleAuth(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        // merge-функция обязательна: две нарушенных валидации на одном поле
        // дают два FieldError с одним ключом, иначе здесь падал бы
        // IllegalStateException -> 500 вместо 400 (BUG-067).
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (first, second) -> first));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Ошибка валидации");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Нечитаемое тело запроса (невалидный JSON, неизвестное значение enum,
     * неверный тип поля) — ошибка клиента: 400, а не 500 (BUG-067).
     *
     * <p>В detail попадает причина без внутренних имён Jackson/JVM
     * (полные имена классов вырезаются).
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        // Стектрейс — чтобы маскированный баг десериализатора был виден в логах.
        log.debug("Нечитаемое тело запроса", ex);
        Throwable cause = ex.getMostSpecificCause();
        String reason = cause != null ? cause.getMessage() : null;
        String detail = (reason == null || reason.isBlank())
                ? "Некорректное тело запроса"
                : "Некорректное тело запроса: " + reason.replaceAll(
                        "\\b[a-z][a-z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)+\\b", "…");
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
    }

    /**
     * Неверный тип/формат параметра запроса (например, кривой ISO-date в {@code createdAfter})
     * — это ошибка клиента, а не внутренняя ошибка сервера.
     */
    @ExceptionHandler(TypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(TypeMismatchException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Некорректное значение параметра запроса");
        problem.setProperty("parameter", ex.getPropertyName());
        return problem;
    }

    /**
     * Явно выброшенный статус (например, 422 «Нельзя назначить OWNER») должен
     * сохранять свой код, а не превращаться в 500 catch-all-обработчиком.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        String detail = ex.getReason() != null ? ex.getReason() : "Ошибка запроса";
        return ProblemDetail.forStatusAndDetail(ex.getStatusCode(), detail);
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidationException(ValidationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setProperty("fieldErrors", ex.getFieldErrors());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Неожиданная ошибка", ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Внутренняя ошибка сервера");
    }
}
