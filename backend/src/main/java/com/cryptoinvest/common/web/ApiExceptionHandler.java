package com.cryptoinvest.common.web;

import java.util.Map;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 입력 오류만 고정된 일반 메시지로 반환해 내부 예외·민감한 세부 정보를 노출하지 않는다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidValidationRequest() { return Map.of("code", "INVALID_REQUEST"); }

    @ExceptionHandler(com.cryptoinvest.security.AccountProfileController.NicknameTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> nicknameTaken() { return Map.of("code", "NICKNAME_TAKEN"); }
    @ExceptionHandler(com.cryptoinvest.security.AccountProfileController.EmailChangeCooldownException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> emailChangeCooldown(com.cryptoinvest.security.AccountProfileController.EmailChangeCooldownException exception) { return Map.of("code", "EMAIL_CHANGE_COOLDOWN", "availableAt", exception.availableAt().toString()); }

    @ExceptionHandler(com.cryptoinvest.security.AccountProfileController.EmailTakenException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> emailTaken() { return Map.of("code", "EMAIL_TAKEN"); }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidRequest() { return Map.of("code", "INVALID_REQUEST"); }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> invalidState() { return Map.of("code", "INVALID_STATE"); }


    @ExceptionHandler(com.cryptoinvest.security.AuthRateLimitService.RateLimitExceededException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> rateLimited() {
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(Map.of("code", "RATE_LIMITED"));
    }
}
