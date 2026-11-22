package com.yonatan.loganalyzer.controller;

import com.yonatan.loganalyzer.dto.ExplainRequestDTO;
import com.yonatan.loganalyzer.dto.ExplainResponseDTO;
import com.yonatan.loganalyzer.service.ErrorExplainerService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExplainController {
    private final ErrorExplainerService explainer;

    // Spring supplies the service when it creates this controller.
    public ExplainController(ErrorExplainerService explainer) {
        this.explainer = explainer;
    }

    // @RequestBody: Spring reads the JSON in the request and builds an
    // ExplainRequestDTO from it. This is the reverse of what happens to the
    // return value, which Spring turns from an object into JSON.
    @PostMapping(value = "/explain",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ExplainResponseDTO explain(@RequestBody ExplainRequestDTO request) {
        return new ExplainResponseDTO(request.message(), explainer.explain(request.message()));
    }
}
