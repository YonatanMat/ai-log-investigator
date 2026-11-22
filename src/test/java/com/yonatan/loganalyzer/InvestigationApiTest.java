package com.yonatan.loganalyzer;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.context.WebApplicationContext;
import com.yonatan.loganalyzer.service.ErrorExplainerService;
import com.yonatan.loganalyzer.service.ExplanationException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
class InvestigationApiTest {
    @MockitoBean
    private ErrorExplainerService explainer;
    @Autowired
    private WebApplicationContext context;
    private MockMvc api;

    @BeforeEach
    void setUp() {
        api = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void explainsOnlyTheMostFrequentErrorInTheSample() throws Exception {
        String message = "Database connection failed";
        when(explainer.explain(message)).thenReturn("Test explanation");
        upload(Files.readAllBytes(Path.of("samples/sample-repeated.log")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.totalEntries").value(11))
                .andExpect(jsonPath("$.analysis.levels.ERROR").value(4))
                .andExpect(jsonPath("$.topError.message").value(message))
                .andExpect(jsonPath("$.topError.count").value(2))
                .andExpect(jsonPath("$.topError.explanation").value("Test explanation"));
        verify(explainer).explain(message);
        verifyNoMoreInteractions(explainer);
    }

    @Test
    void picksTheFirstSeenMessageWhenCountsTie() throws Exception {
        upload("01 ERROR Zebra\n02 ERROR Alpha".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topError.message").value("Zebra"));
        verify(explainer).explain("Zebra");
        verifyNoMoreInteractions(explainer);
    }

    @Test
    void returnsCountsWithoutCallingClaudeWhenNoErrorsExist() throws Exception {
        upload("01 WARN Slow\n02 INFO Ready".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.totalEntries").value(2))
                .andExpect(jsonPath("$.topError").value(org.hamcrest.Matchers.nullValue()));
        verifyNoInteractions(explainer);
    }

    @Test
    void validatesEntireUploadBeforeCallingClaude() throws Exception {
        upload("01 ERROR Example\nincomplete".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Line 2: expected TIME LEVEL MESSAGE."));
        verifyNoInteractions(explainer);
    }

    @Test
    void returnsGenericProviderError() throws Exception {
        when(explainer.explain("Example")).thenThrow(new ExplanationException("Private details"));
        upload("01 ERROR Example".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Could not get an explanation from Claude."));
        verify(explainer).explain("Example");
        verifyNoMoreInteractions(explainer);
    }

    @Test
    void comparesTwoSavedSamplesWithoutAnotherExplanation() throws Exception {
        when(explainer.explain(anyString())).thenReturn("Fake explanation");
        String before = saveReport(Files.readAllBytes(Path.of("samples/sample-repeated.log")));
        String after = saveReport(Files.readAllBytes(Path.of("samples/sample-after.log")));
        verify(explainer).explain("Database connection failed");
        verify(explainer).explain("Invalid authentication token");

        compare(before, after).andExpect(status().isOk())
                .andExpect(jsonPath("$.before.id").value(before))
                .andExpect(jsonPath("$.after.id").value(after))
                .andExpect(jsonPath("$.before.totalEntries").value(11))
                .andExpect(jsonPath("$.after.totalEntries").value(5))
                .andExpect(jsonPath("$.changes").value(org.hamcrest.Matchers.hasSize(4)))
                .andExpect(jsonPath("$.changes[*].message").value(org.hamcrest.Matchers.contains(
                        "Audio file not found: sample song.mp3", "Database connection failed",
                        "Invalid authentication token", "Request timed out")))
                .andExpect(jsonPath("$.changes[*].beforeCount").value(org.hamcrest.Matchers.contains(1, 2, 1, 0)))
                .andExpect(jsonPath("$.changes[*].afterCount").value(org.hamcrest.Matchers.contains(0, 1, 2, 1)))
                .andExpect(jsonPath("$.changes[*].delta").value(org.hamcrest.Matchers.contains(-1, -1, 1, 1)));
        compare(after, before).andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].delta").value(org.hamcrest.Matchers.contains(1, 1, -1, -1)));
        verifyNoMoreInteractions(explainer);
    }

    @Test
    void identicalCountsProduceNoChangesEvenWithDifferentTotalEntries() throws Exception {
        String before = saveReport("01 ERROR Timeout".getBytes(StandardCharsets.UTF_8));
        String after = saveReport("02 INFO Restarted\n03 ERROR Timeout".getBytes(StandardCharsets.UTF_8));
        compare(before, before).andExpect(status().isOk()).andExpect(jsonPath("$.changes").isEmpty());
        compare(before, after).andExpect(status().isOk()).andExpect(jsonPath("$.changes").isEmpty())
                .andExpect(jsonPath("$.before.totalEntries").value(1))
                .andExpect(jsonPath("$.after.totalEntries").value(2));
        verify(explainer, times(2)).explain("Timeout");
        verifyNoMoreInteractions(explainer);
    }

    @Test
    void comparesMessageTextExactly() throws Exception {
        String before = saveReport("01 ERROR Timeout".getBytes(StandardCharsets.UTF_8));
        String after = saveReport("02 ERROR timeout".getBytes(StandardCharsets.UTF_8));
        compare(before, after).andExpect(status().isOk())
                .andExpect(jsonPath("$.changes[*].message").value(org.hamcrest.Matchers.contains("Timeout", "timeout")))
                .andExpect(jsonPath("$.changes[*].delta").value(org.hamcrest.Matchers.contains(-1, 1)));
    }

    @Test
    void unknownReportsReturn404AndInvalidParametersReturn400() throws Exception {
        String known = saveReport(new byte[0]);
        String unknown = UUID.randomUUID().toString();
        compare(unknown, known).andExpect(status().isNotFound());
        compare(known, unknown).andExpect(status().isNotFound());
        compare("invalid-id", known).andExpect(status().isBadRequest());
        api.perform(get("/investigations/compare").param("before", known)).andExpect(status().isBadRequest());
        verifyNoInteractions(explainer);
    }

    private String saveReport(byte[] contents) throws Exception {
        String body = upload(contents).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonMapper.builder().build().readTree(body).path("id").asString();
    }

    private ResultActions compare(String before, String after) throws Exception {
        return api.perform(get("/investigations/compare").param("before", before).param("after", after));
    }

    private ResultActions upload(byte[] data) throws Exception {
        return api.perform(multipart("/investigate")
                .file(new MockMultipartFile("file", "sample.log", "text/plain", data)));
    }
}
