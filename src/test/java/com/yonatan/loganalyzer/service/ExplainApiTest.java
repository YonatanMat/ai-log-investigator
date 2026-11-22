package com.yonatan.loganalyzer.service;

import com.yonatan.loganalyzer.controller.ApiExceptionHandler;
import com.yonatan.loganalyzer.controller.ExplainController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ExplainApiTest {
    // Keep real message validation. Replace the method that reads the key and calls Claude.
    static class FakeExplainer extends ErrorExplainerService {
        boolean simulateFailure;

        @Override
        String requestExplanation(String message) {
            if (simulateFailure) {
                throw new ExplanationException("Private provider failure details");
            }
            return "Test explanation for " + message;
        }
    }

    private final FakeExplainer explainer = new FakeExplainer();
    private final MockMvc api = MockMvcBuilders.standaloneSetup(new ExplainController(explainer))
            .setControllerAdvice(new ApiExceptionHandler()).build();

    @Test
    void returnsOriginalMessageAndExplanation() throws Exception {
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"message":"Database connection failed"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Database connection failed"))
                .andExpect(jsonPath("$.explanation").value("Test explanation for Database connection failed"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"message\":null}", "{\"message\":\"   \"}"})
    void rejectsMissingOrBlankMessage(String body) throws Exception {
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The error message must not be empty."));
    }

    @Test
    void acceptsMessageAtLengthLimit() throws Exception {
        String message = "x".repeat(2000);
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(message));
    }

    @Test
    void rejectsMessageOverLengthLimit() throws Exception {
        String message = "x".repeat(2001);
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + message + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("The error message is too long. Maximum: 2000 characters."));
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void rejectsPlainTextBody() throws Exception {
        api.perform(post("/explain").contentType(MediaType.TEXT_PLAIN).content("Database connection failed"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void hidesProviderFailureDetails() throws Exception {
        explainer.simulateFailure = true;
        api.perform(post("/explain").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Database connection failed\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(content().json("""
                        {"error":"Could not get an explanation from Claude."}
                        """, org.springframework.test.json.JsonCompareMode.STRICT));
    }
}
