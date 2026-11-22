# Log Analyzer - Step 7

A Java 21 / Spring Boot 4.1.2 application that accepts a log upload and returns a JSON report. You can also send one error message to Claude for an explanation. Both explanation features use ANTHROPIC_API_KEY when contacting Claude.

## Start in IntelliJ

1. Locate the new project folder containing this README and pom.xml.
2. In IntelliJ, open this folder's `pom.xml` as a project. This is a complete Maven project; do not copy these classes into the old console project.
3. Select JDK 21 as the project SDK and let Maven load the dependencies. The first import needs internet access.
4. Run `com.yonatan.loganalyzer.LogAnalyzerApplication`.
5. Open http://127.0.0.1:8080/health. It should return `{"status":"ok"}`.

If port 8080 is busy, change `server.port` in `src/main/resources/application.properties` to 8081 and use that port in Postman too.

## Compare two investigations

Open **Compare investigations** in the Step 7 Postman collection. For reports
you already saved, use **List recent investigations** to get their IDs. In the
comparison request's **Params** tab paste the earlier ID into `before` and the
later ID into `after`, then send. This reads the database and makes no AI call.

To reproduce the example from scratch, send **Investigate a log** twice using
`samples/sample-repeated.log` first and `samples/sample-after.log` second.
Each of these uploads includes errors and makes one paid call. The collection
remembers the IDs of the two successful uploads. Open **Compare investigations**
with its original variables and send.

The response identifies both reports, including their total entry counts (11 and
5 for these samples). The changes list is ordered by exact message text. A delta
is the after count minus the before count.

| Error message | Before | After | Delta |
| --- | ---: | ---: | ---: |
| Audio file not found: sample song.mp3 | 1 | 0 | -1 |
| Database connection failed | 2 | 1 | -1 |
| Invalid authentication token | 1 | 2 | +1 |
| Request timed out | 0 | 1 | +1 |

An empty changes list means all ERROR message counts matched. Both report
totals still appear. Swapping IDs reverses the direction of the comparison.
A missing or invalid UUID parameter returns HTTP 400. An unknown report returns 404.

## Save a report and reopen it

1. Reload Maven in IntelliJ, then run LogAnalyzerApplication with the project directory as its working directory.
2. Import the Step 7 Postman collection. Open **Investigate a log**, choose **Body > form-data** and select `samples/sample-no-errors.log` in the `file` row. Send it. This free example returns two entries, a null `topError`, an `id` and a UTC `createdAt`. Postman remembers the ID.
3. Send **List recent investigations** for up to 50 summaries, newest first. Each includes the ID, timestamp, total entries and selected error message.
4. Stop and restart the Java application, then send **Open saved investigation**. Its ID comes from the collection variable `investigationId`. The detail response contains the same data as the original response. A well-formed UUID that is absent returns HTTP 404.
5. To save an explanation too, choose `samples/sample-repeated.log` for the upload. This calls Claude once. Reading history never calls Claude.

History begins with successful uploads to POST /investigate in Step 6. Each successful upload creates one report. The database stores counts, grouped ERROR messages, the selected error and its explanation. Raw upload bytes and individual INFO/WARN lines stay out of storage.

### Database options

By default the application uses H2 and keeps history under `data/` in the project working directory. This runs directly from IntelliJ. For MySQL 8, use the same API with the following setup:

1. Connect to your MySQL server through Workbench and run:
   ```sql
   CREATE DATABASE IF NOT EXISTS log_analyzer CHARACTER SET utf8mb4;
   ```
2. Use a database account with CREATE, SELECT and INSERT permissions on that database. The application creates the table on startup.
3. In Windows **Edit environment variables for your account**, add `LOG_DB_USER` and `LOG_DB_PASSWORD` with your database credentials. For a server other than localhost:3306, also set `LOG_DB_URL` to its JDBC URL, such as `jdbc:mysql://localhost:3307/log_analyzer`. Restart IntelliJ so it inherits the variables.
4. Open **Run > Edit Configurations**. Select LogAnalyzerApplication. Under **Modify options > Program arguments**, enter `--spring.profiles.active=mysql`. Run the application and try the three Postman requests above.

The selected database owns its history. Switching between H2 and MySQL opens a different set of reports. Tests use their own databases.

## Investigate a log in one request

Restart LogAnalyzerApplication. Import the updated Postman collection and open
**Log Analyzer - Step 7 > Investigate a log**. Under **Body > form-data**,
choose `samples/sample-repeated.log` for the `file` field and click **Send**.
This sample makes one paid Claude call.

The server parses the entire file and counts exact ERROR messages locally. It
sends the most frequent ERROR message to Claude. Equal counts use the first
appearance in the file. The response includes the existing report under
`analysis` and the selected message, count and explanation under `topError`.
For the sample, expect 11 entries, 4 ERROR lines, and a top error of
`Database connection failed` with count 2.

Files with no ERROR lines return HTTP 200 with a null topError and use no
Claude call. Invalid uploads fail before explanation. The existing 2,000-character
limit applies to the selected message. A Claude failure returns HTTP 502 with the
generic error. Use **Analyze a log** anytime you want the counts alone.

## Explain one error with Postman

1. Run the existing checks in IntelliJ under **Maven > Lifecycle > test**.
2. With the server running, import `postman/Log-Analyzer.postman_collection.json`.
3. Open **Explain one error**. Its JSON body contains a sample message.
   Click **Send** to make one paid call using the server's environment key.

```json
{"message": "Database connection failed"}
```

On success, look for HTTP 200, the original `message`, and an `explanation`
containing Meaning, Possible causes, and Checks to try. Claude writes the text,
so the wording can vary.

For a free input check, send `{"message":" "}`. The server returns HTTP 400
before contacting Claude. Missing messages, malformed JSON, and messages longer
than 2,000 Java characters also return 400. A provider failure or missing API key
returns 502 with a short error. Restart IntelliJ if you changed the environment key.

**How it works.** Spring reads the JSON into an ExplainRequestDTO record.
ExplainController reads its message, calls ErrorExplainerService, and returns an
ExplainResponseDTO record holding both strings. Spring serializes it to JSON.

## Send a log with Postman

Import `postman/Log-Analyzer.postman_collection.json`. Open **Analyze a log**, then under **Body > form-data**, select a file for the `file` row. Use `samples/sample-repeated.log` and click **Send**. Keep the Java server running.

Or create the request yourself:

- Method: POST
- URL: http://127.0.0.1:8080/analyze
- Body: form-data
- Key: `file`
- Key type: File (change the row from Text to File)
- Value: choose `samples/sample-repeated.log`

Postman generates the multipart Content-Type header, including its boundary; leave that header automatic.

The response should be HTTP 200 with these values (JSON field order can vary):

```json
{
  "totalEntries": 11,
  "levels": {"INFO": 6, "WARN": 1, "ERROR": 4},
  "errors": {
    "Database connection failed": 2,
    "Invalid authentication token": 1,
    "Audio file not found: sample song.mp3": 1
  }
}
```

The original `samples/sample.log` has 10 entries: INFO 6, WARN 1, ERROR 3; each error message appears once. `samples/invalid.log` demonstrates an HTTP 400 response naming the incomplete line.

## What changed from step 2

| Class | Responsibility |
| --- | --- |
| LogAnalyzerApplication | Starts Spring Boot and the local HTTP server. |
| LogController | Receives the uploaded file and calls the service. |
| LogAnalysisService | Reads entries, counts levels, and groups error messages. |
| LogEntry | Holds one input entry's time, level, and message. |
| AnalysisResultDTO | Carries the counts and error groups to the controller. |
| ApiErrorDTO | Carries an error message. |
| ApiExceptionHandler | Converts expected input failures into HTTP error responses. |

Previously our methods printed their results. Now `analyze()` returns an `AnalysisResultDTO`:

```java
return new AnalysisResultDTO(entries.size(), levels, errors);
```

The controller returns that object. Spring reads the DTO's getters and serializes it as JSON. You do not manually build a JSON string.

Spring creates the controller and service for us: `@RestController` marks the controller, `@Service` marks the analysis class, and constructor injection supplies the service to the controller. `@PostMapping` connects the method to `/analyze`; `@RequestParam("file")` receives the upload field.

The analysis uses the same exact-message grouping as step 2. `LinkedHashMap` keeps insertion order; its counting operations work like the HashMap operations already used. Each request has its own counts.

## Supported input

Use UTF-8 text with one entry per line:

```text
21:00:00 INFO Server started
21:00:01 ERROR Database connection failed
```

Each line is split into three parts: TIME, LEVEL, and the complete MESSAGE. Time is kept as text. INFO/WARN/ERROR comparisons are case-sensitive; other level names are grouped under OTHER. Blank lines are ignored. An empty file returns zero counts and an empty error map.

Messages must match exactly to share a group. Changing capitalization, IDs, or internal spacing creates a different group. This learning parser does not yet support full Spring Boot layouts or multiline stack traces. It expects a single token for the time, such as `21:00:00`.

## Upload endpoint HTTP responses

| Status | Meaning |
| --- | --- |
| 200 | Analysis succeeded, including an empty input file. |
| 400 | Missing file field, incomplete log line, malformed upload, or invalid UTF-8. |
| 413 | File exceeds the configured 1 MB limit (or total request exceeds 2 MB). |
| 415 | Request was not sent as multipart/form-data. |
| 500 | The server encountered an I/O failure. |

Expected input errors have a JSON body with an `error` field. Log content is not stored by this application; it is analyzed for the current request. The server binds to 127.0.0.1 for local development.

## Terminal commands

With JDK 21 and Maven 3.6.3 or newer installed, run these from the folder containing `pom.xml`:

```sh
mvn test
mvn spring-boot:run
```

From a second terminal while the server is running:

```sh
curl -F "file=@samples/sample-repeated.log" http://127.0.0.1:8080/analyze
```

In Windows PowerShell use `curl.exe` for that command. IntelliJ also provides Maven goals through its Maven tool window, so a separate Maven installation is optional when working in the IDE.

To build an executable JAR:

```sh
mvn package
java -jar target/log-analyzer-0.0.1-SNAPSHOT.jar
```

The tests include service checks and real HTTP upload tests against a server on a temporary port. See `STATUS.md` for the verification status of this delivered copy.

## Reading the endpoint tests

Run **Maven > Lifecycle > test** in IntelliJ. Expect 35 passing tests.
ExplainApiTest uses Spring MockMvc and a fake explainer to check message validation,
successful responses and generic failures. InvestigationApiTest uses the sample
upload and verifies that the top error is the only message sent to the explainer.
