package com.yonatan.loganalyzer;

import com.yonatan.loganalyzer.service.ErrorExplainerService;
import com.yonatan.loganalyzer.service.ExplanationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InvestigationHistoryTest {
    @TempDir
    Path directory;

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeClaude {
        @Bean
        @Primary
        ErrorExplainerService localExplainer() {
            return mock(ErrorExplainerService.class);
        }
    }

    @Test
    void savesThenReopensTheSameReportAfterRestart() throws Exception {
        String report;
        String id;
        var json = JsonMapper.builder().build();

        try (var app = start()) {
            var api = requests(app);
            var fake = app.getBean(ErrorExplainerService.class);
            when(fake.explain("Database unavailable")).thenReturn("Check the database connection.");
            byte[] lines = "01 INFO Private detail\n02 ERROR Database unavailable".getBytes(StandardCharsets.UTF_8);
            report = api.perform(multipart("/investigate").file(new MockMultipartFile("file", lines)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.createdAt").isString())
                    .andExpect(jsonPath("$.topError.explanation").value("Check the database connection."))
                    .andReturn().getResponse().getContentAsString();
            id = json.readTree(report).path("id").asString();
            UUID.fromString(id);
            verify(fake).explain("Database unavailable");
            verifyNoMoreInteractions(fake);
        }

        try (var app = start()) {
            var api = requests(app);
            api.perform(get("/investigations"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(id))
                    .andExpect(jsonPath("$[0].topMessage").value("Database unavailable"))
                    .andExpect(jsonPath("$[0].totalEntries").value(2));
            String reopened = api.perform(get("/investigations/{id}", id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(json.readTree(report), json.readTree(reopened));
            api.perform(get("/investigations/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
            verifyNoInteractions(app.getBean(ErrorExplainerService.class));
        }
    }

    @Test
    void savesNoErrorReportsAndExcludesFailedInvestigations() throws Exception {
        try (var app = start()) {
            var api = requests(app);
            var fake = app.getBean(ErrorExplainerService.class);
            when(fake.explain("Broken")).thenThrow(new ExplanationException("private"));
            api.perform(multipart("/investigate").file("file", "00 ERROR Broken".getBytes(StandardCharsets.UTF_8)))
                    .andExpect(status().isBadGateway());
            api.perform(get("/investigations")).andExpect(content().json("[]"));
            String report = api.perform(multipart("/investigate").file("file", new byte[0]))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var json = JsonMapper.builder().build();
            String id = json.readTree(report).path("id").asString();
            String reopened = api.perform(get("/investigations/{id}", id))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            assertEquals(json.readTree(report), json.readTree(reopened));
            verify(fake).explain("Broken");
            verifyNoMoreInteractions(fake);
        }
    }

    private ConfigurableApplicationContext start() {
        String url = "jdbc:h2:file:" + directory.resolve("history").toAbsolutePath() + ";MODE=MySQL";
        return new SpringApplicationBuilder(LogAnalyzerApplication.class, FakeClaude.class)
                .run("--server.port=0", "--spring.profiles.active=test", "--spring.datasource.url=" + url);
    }

    private MockMvc requests(ConfigurableApplicationContext app) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build();
    }
}
