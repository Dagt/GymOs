package com.gymos.common.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class GlobalExceptionHandler {
	private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ApiError> handleBusiness(BusinessException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ApiError.of(exception.code(), exception.getMessage(), exception.details(), correlationId(request)));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
		Map<String, Object> details = new LinkedHashMap<>();
		exception.getBindingResult().getFieldErrors()
				.forEach(error -> details.put(error.getField(), error.getDefaultMessage()));
		return ResponseEntity.badRequest()
				.body(ApiError.of("VALIDATION_ERROR", "La solicitud contiene datos invalidos.", details, correlationId(request)));
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
		return ResponseEntity.badRequest()
				.body(ApiError.of("VALIDATION_ERROR", "La solicitud contiene datos invalidos.", Map.of("violations", exception.getMessage()), correlationId(request)));
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.FORBIDDEN)
				.body(ApiError.of("PERMISSION_DENIED", "No tienes permiso para realizar esta accion.", Map.of(), correlationId(request)));
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ApiError> handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
		if (exception instanceof ApiAuthenticationException apiException) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(ApiError.of(
							apiException.code(), apiException.publicMessage(), Map.of(), correlationId(request)));
		}
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.body(ApiError.of("BAD_CREDENTIALS", "Usuario o contrasena incorrectos.", Map.of(), correlationId(request)));
	}

	@ExceptionHandler(NoResourceFoundException.class)
	ResponseEntity<ApiError> handleNotFound(NoResourceFoundException exception, HttpServletRequest request) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiError.of("RESOURCE_NOT_FOUND", "El recurso solicitado no existe.", Map.of(), correlationId(request)));
	}

	@ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
	ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
		// Never echo rejected values: request bodies may contain passwords or tokens.
		return ResponseEntity.badRequest().body(ApiError.of(
				"VALIDATION_ERROR", "La solicitud contiene un identificador, fecha o dato inválido.",
				Map.of(), correlationId(request)));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
		LOGGER.error("Unexpected API error. correlationId={}", correlationId(request), exception);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(ApiError.of("INTERNAL_ERROR", "Ocurrio un error inesperado.", Map.of(), correlationId(request)));
	}

	private String correlationId(HttpServletRequest request) {
		Object value = request.getAttribute(Correlation.ATTRIBUTE);
		return value == null ? "unknown" : value.toString();
	}
}
