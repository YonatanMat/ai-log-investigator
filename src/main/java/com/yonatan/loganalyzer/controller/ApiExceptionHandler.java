package com.yonatan.loganalyzer.controller;

import com.yonatan.loganalyzer.dto.ApiErrorDTO;
import com.yonatan.loganalyzer.service.ExplanationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import org.springframework.dao.DataAccessException;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO invalidLog(IllegalArgumentException exception) {
        return new ApiErrorDTO(exception.getMessage());
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO missingFile() {
        return new ApiErrorDTO("Upload a file using the form-data key 'file'.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public ApiErrorDTO tooLarge() {
        return new ApiErrorDTO("The upload is too large. Maximum file size: 1 MB.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
    public ApiErrorDTO wrongContentType() {
        return new ApiErrorDTO("Unsupported content type. POST /analyze and POST /investigate need multipart/form-data "
                + "with a file field named 'file'; POST /explain needs JSON.");
    }

    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO invalidUpload() {
        return new ApiErrorDTO("Invalid file upload. Send multipart/form-data with key 'file'.");
    }

    @ExceptionHandler(CharacterCodingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO invalidEncoding() {
        return new ApiErrorDTO("The log must be a UTF-8 text file.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorDTO unreadableJson() {
        return new ApiErrorDTO("Send JSON like {\"message\": \"Database connection failed\"}.");
    }

    // 502 Bad Gateway: our server is fine, but the AI service it called failed.
    @ExceptionHandler(ExplanationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public ApiErrorDTO explanationFailed() {
        return new ApiErrorDTO("Could not get an explanation from Claude.");
    }

    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiErrorDTO readFailure() {
        return new ApiErrorDTO("The server could not read the uploaded file.");
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiErrorDTO databaseFailure() {
        return new ApiErrorDTO("Investigation history is unavailable.");
    }
}
