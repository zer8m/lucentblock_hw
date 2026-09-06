package com.cex.server.error;

import com.cex.server.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
        log.warn("[{}] {}", e.code(), e.getMessage());
        return ResponseEntity.status(e.code().status())
                .body(new ErrorResponse(e.code().name(), e.getMessage()));
    }

    /** 바디 JSON 파싱 실패, 타입 불일치 등 -> INVALID_PARAM */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception e) {
        log.warn("INVALID_PARAM: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_PARAM.status())
                .body(new ErrorResponse(ErrorCode.INVALID_PARAM.name(), "요청 형식이 올바르지 않습니다."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleEtc(Exception e) {
        log.error("예상치 못한 오류", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(new ErrorResponse(ErrorCode.INTERNAL_ERROR.name(), "서버 내부 오류"));
    }
}
