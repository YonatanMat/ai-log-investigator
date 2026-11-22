package com.yonatan.loganalyzer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

// These tests send real HTTP requests to a local server on a temporary port.
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LogApiTest {
    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final JsonMapper json = JsonMapper.builder().build();

    @AfterEach
    void closeClient() {
        client.close();
    }

    @Test
    void healthEndpointConfirmsTheServerIsRunning() throws Exception {
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri("/health"))
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("ok", json.readTree(response.body()).path("status").asText());
    }

    @Test
    void uploadReturnsTheDtoAsJson() throws Exception {
        HttpResponse<String> response = upload("file",
                Files.readAllBytes(Path.of("samples/sample-repeated.log")));
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("").contains("application/json"));
        JsonNode body = json.readTree(response.body());
        assertEquals(11, body.path("totalEntries").asInt());
        assertEquals(6, body.path("levels").path("INFO").asInt());
        assertEquals(1, body.path("levels").path("WARN").asInt());
        assertEquals(4, body.path("levels").path("ERROR").asInt());
        assertEquals(2, body.path("errors").path("Database connection failed").asInt());
        assertEquals(3, body.path("errors").size());
    }

    @Test
    void emptyFileReturnsZeroCounts() throws Exception {
        HttpResponse<String> response = upload("file", new byte[0]);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(0, body.path("totalEntries").asInt());
        assertEquals(0, body.path("levels").path("ERROR").asInt());
        assertEquals(0, body.path("errors").size());
    }

    @Test
    void wrongFileFieldReturnsHelpfulBadRequest() throws Exception {
        HttpResponse<String> response = upload("wrongField", "00:00:00 INFO Started".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode());
        assertTrue(json.readTree(response.body()).path("error").asText().contains("'file'"));
    }

    @Test
    void malformedLineReturnsLineNumber() throws Exception {
        HttpResponse<String> response = upload("file", "\n00:00:00 ERROR".getBytes(StandardCharsets.UTF_8));
        assertEquals(400, response.statusCode());
        assertEquals("Line 2: expected TIME LEVEL MESSAGE.", json.readTree(response.body()).path("error").asText());
    }

    @Test
    void invalidUtf8ReturnsBadRequest() throws Exception {
        HttpResponse<String> response = upload("file", new byte[]{(byte) 0xC3, (byte) 0x28});
        assertEquals(400, response.statusCode());
        assertTrue(json.readTree(response.body()).path("error").asText().contains("UTF-8"));
    }

    @Test
    void oversizedFileIsRejectedBeforeAnalysis() throws Exception {
        HttpResponse<String> response = upload("file", new byte[1024 * 1024 + 1]);
        assertEquals(413, response.statusCode(), response.body());
        assertTrue(json.readTree(response.body()).path("error").asText().contains("1 MB"));
    }

    @Test
    void rawTextInsteadOfFileUploadReturnsUnsupportedMediaType() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/analyze"))
                .timeout(Duration.ofSeconds(10)).header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("00:00:00 INFO Started")).build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(415, response.statusCode());
        assertTrue(json.readTree(response.body()).path("error").asText().contains("multipart/form-data"));
    }

    private HttpResponse<String> upload(String field, byte[] contents) throws Exception {
        String boundary = "LogAnalyzerTestBoundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + field + "\"; filename=\"sample.log\"\r\n"
                + "Content-Type: text/plain\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.writeBytes(contents);
        body.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(uri("/analyze"))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }
}
