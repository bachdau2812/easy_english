# Split Rolling Log Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure Spring Boot Logback to write mutually exclusive routine and error logs, with daily and 100 MB rollover, 30-day retention, and a 10 GB cap per category.

**Architecture:** Add one Spring-aware Logback configuration resource using the logging backend already supplied by Spring Boot. A console appender keeps combined operational output, while two synchronous rolling-file appenders use exact level filters and independent size-and-time rolling policies.

**Tech Stack:** Java 21, Spring Boot 4.1.0, SLF4J, Logback, Maven Wrapper

## Global Constraints

- Keep the existing Logback backend; do not add or replace Maven dependencies.
- Normal file logs contain only `TRACE`, `DEBUG`, `INFO`, and `WARN`.
- Error file logs contain only `ERROR`.
- Roll each category when the date changes or the active file reaches `100 MB`.
- Retain archives for `30` days and cap each category's archives at `10 GB`.
- Keep console logging enabled.
- Preserve the MDC `traceId` and show `server.port`, defaulting to `8080`.
- Do not modify the user's existing uncommitted changes in `application.properties` or Java source files.

---

## File Structure

- Create `src/main/resources/logback-spring.xml`: owns console formatting, mutually exclusive level routing, and both rolling/retention policies.
- Use existing `src/test/java/com/bachdauduc/vocab_app/VocabAppApplicationTests.java` unchanged: its Spring context startup verifies that Spring Boot can discover and initialize the new logging configuration.

### Task 1: Add and Validate Split Rolling Log Configuration

**Files:**
- Create: `src/main/resources/logback-spring.xml`
- Verify unchanged: `src/test/java/com/bachdauduc/vocab_app/VocabAppApplicationTests.java`

**Interfaces:**
- Consumes: Spring property `server.port`; SLF4J events and MDC key `traceId`.
- Produces: combined standard output, `logs/normal.log`, `logs/error.log`, and gzip archives under `logs/archive/`.

- [ ] **Step 1: Run the existing context test to establish the baseline**

Run:

```powershell
.\mvnw.cmd -Dtest=VocabAppApplicationTests test
```

Expected: the context test passes before the resource exists. If it fails because required external environment variables are absent, record the failure as an environment baseline and do not weaken production configuration.

- [ ] **Step 2: Create the Spring-aware Logback configuration**

Create `src/main/resources/logback-spring.xml` with exactly this content:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration scan="false">
    <springProperty scope="context" name="APP_PORT"
                    source="server.port" defaultValue="8080"/>

    <property name="LOG_DIR" value="${LOG_DIR:-logs}"/>
    <property name="LOG_PATTERN"
              value="%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level %X{traceId} [%thread] [port:${APP_PORT}] %logger{36} - %msg%n%ex"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder><pattern>${LOG_PATTERN}</pattern></encoder>
    </appender>

    <appender name="NORMAL_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/normal.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelRangeFilter">
            <levelMin>TRACE</levelMin>
            <levelMax>WARN</levelMax>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <encoder><pattern>${LOG_PATTERN}</pattern></encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/archive/normal-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
            <cleanHistoryOnStart>true</cleanHistoryOnStart>
        </rollingPolicy>
    </appender>

    <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>${LOG_DIR}/error.log</file>
        <filter class="ch.qos.logback.classic.filter.LevelFilter">
            <level>ERROR</level>
            <onMatch>ACCEPT</onMatch>
            <onMismatch>DENY</onMismatch>
        </filter>
        <encoder><pattern>${LOG_PATTERN}</pattern></encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <fileNamePattern>${LOG_DIR}/archive/error-%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
            <maxFileSize>100MB</maxFileSize>
            <maxHistory>30</maxHistory>
            <totalSizeCap>10GB</totalSizeCap>
            <cleanHistoryOnStart>true</cleanHistoryOnStart>
        </rollingPolicy>
    </appender>

    <root level="TRACE">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="NORMAL_FILE"/>
        <appender-ref ref="ERROR_FILE"/>
    </root>
</configuration>
```

- [ ] **Step 3: Run the focused Spring context verification**

Run:

```powershell
.\mvnw.cmd -Dtest=VocabAppApplicationTests test
```

Expected: Maven reports `BUILD SUCCESS`; startup output contains no Logback parser, appender, filter, or rolling-policy errors. The application creates `logs/normal.log` during startup, demonstrating that the file appender initialized.

- [ ] **Step 4: Inspect the generated files and configured policy**

Run:

```powershell
Get-ChildItem logs -Recurse
Select-String -Path src\main\resources\logback-spring.xml -Pattern "100MB|30|10GB|LevelRangeFilter|LevelFilter"
```

Expected: `logs/normal.log` and `logs/error.log` exist; the configuration contains both filters and all three retention values. Routing is based on event level, not words appearing inside a message.

- [ ] **Step 5: Run the complete Maven test suite**

Run:

```powershell
.\mvnw.cmd test
```

Expected: Maven reports `BUILD SUCCESS`. If an unrelated pre-existing test fails, compare it with the Step 1 baseline and report it without editing unrelated code.

- [ ] **Step 6: Review the scoped diff**

Run:

```powershell
git diff --check -- src/main/resources/logback-spring.xml
git status --short
```

Expected: no whitespace errors; the only new implementation file is `src/main/resources/logback-spring.xml`. Existing unrelated modifications remain unstaged and unchanged.

- [ ] **Step 7: Commit the logging configuration**

Run:

```powershell
git add -- src/main/resources/logback-spring.xml
git commit -m "add split rolling log configuration" -- src/main/resources/logback-spring.xml
```

Expected: the commit contains only `src/main/resources/logback-spring.xml`.
