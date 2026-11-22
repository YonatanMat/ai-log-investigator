package com.yonatan.loganalyzer.dto;

// The JSON we receive: {"message": "Database connection failed"}.
// A record is a short way to write a class that only holds data. Jackson fills it
// from the JSON body, matching the JSON field "message" to the record field.
public record ExplainRequestDTO(String message) {
}
