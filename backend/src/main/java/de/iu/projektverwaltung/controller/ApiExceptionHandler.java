package de.iu.projektverwaltung.controller;

import de.iu.projektverwaltung.controller.ApiModels.ApiMessage;
import de.iu.projektverwaltung.service.ApiException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/** Übersetzt Fehler zentral in kurze, einheitliche Antworten für die Oberfläche. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiMessage> application(ApiException error) {
        return ResponseEntity.status(error.getStatus()).body(new ApiMessage(error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiMessage> validation(MethodArgumentNotValidException error) {
        String message = error.getBindingResult().getAllErrors().stream()
                .map(item -> item.getDefaultMessage()).distinct().sorted().collect(Collectors.joining(" "));
        return ResponseEntity.badRequest().body(new ApiMessage(message));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiMessage> malformed(Exception ignored) {
        return ResponseEntity.badRequest().body(new ApiMessage("Ungültige Eingabe. Bitte Felder und Auswahlwerte prüfen."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiMessage> conflict(DataIntegrityViolationException ignored) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiMessage("Datensatz steht im Konflikt mit vorhandenen Daten."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiMessage> notFound(NoResourceFoundException ignored) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiMessage("Adresse nicht gefunden."));
    }
}
