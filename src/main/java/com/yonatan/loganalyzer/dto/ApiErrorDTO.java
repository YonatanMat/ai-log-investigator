package com.yonatan.loganalyzer.dto;

public class ApiErrorDTO {
    private final String error;

    public ApiErrorDTO(String error) {
        this.error = error;
    }

    public String getError() {
        return error;
    }
}
