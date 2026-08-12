# Split Rolling Log Configuration Design

## Goal

Add a Spring Boot Logback configuration that keeps routine logs and error logs in separate rolling files while preserving console output for local and container operation.

## Logging Backend

Use Spring Boot's existing Logback backend through `logback-spring.xml`. Do not migrate to Log4j2 or change Maven dependencies.

## Appenders and Filtering

Configure three appenders:

- `CONSOLE` writes all events accepted by the root logger to standard output.
- `NORMAL_FILE` writes only `TRACE`, `DEBUG`, `INFO`, and `WARN` events to `logs/normal.log` using a level-range filter.
- `ERROR_FILE` writes only `ERROR` events to `logs/error.log` using an exact-level filter.

The two file categories are mutually exclusive: an `ERROR` event must not appear in `normal.log`.

## Output Pattern

Use one shared pattern for every appender. It includes the timestamp with milliseconds, log level, MDC `traceId`, thread name, application port, short logger name, message, and stack trace. Read the port from the Spring `server.port` property and fall back to `8080`.

## Rotation and Retention

Both file appenders use a size-and-time rolling policy:

- Start a new archive when the calendar day changes.
- Start another archive when the active file reaches `100 MB`.
- Compress archived files with gzip.
- Retain archives for at most `30` days.
- Limit archived data to `10 GB` for each log category.
- Delete the oldest eligible archives first when retention limits are exceeded.

Archive names contain the date and a per-day sequence number so size-based rollover cannot overwrite an existing archive.

## Root Level

Set the root logger to `TRACE` so the normal file can receive the complete requested `TRACE` through `WARN` range. More restrictive package-specific levels can still be configured through Spring properties later.

## Files and Change Boundaries

Add only `src/main/resources/logback-spring.xml` during implementation. Avoid modifying `application.properties`, which already contains unrelated uncommitted user changes.

## Verification

Verification will parse and initialize the Logback configuration through the Spring Boot test context, run the Maven test suite, and inspect the final diff. No external MySQL, Redis, SMTP, Firebase, or Azure services should be required specifically to validate the logging configuration.
