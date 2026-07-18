# Repository Guidelines

## Project Structure & Module Organization

This is a Spring Boot vocabulary-learning backend. Main source lives under `src/main/java/com/bachdauduc/vocab_app`.

- `configuration`: security, JWT decoding, CORS, Firebase, Jackson, timezone, trace IDs.
- `controller`: REST endpoints returning `ApiResponse<T>`.
- `service`: business logic for auth, dictionary lookup, review quizzes, user vocabulary, learning resources, and notifications.
- `repository`: Spring Data JPA repositories, native MySQL queries, and projection interfaces.
- `entity`: JPA mappings for MySQL tables.
- `dto/request` and `dto/response`: API request/response models.
- `exception`: `AppException`, `ErrorCode`, and global exception handling.
- `src/main/resources`: runtime configuration such as `application.properties` and `redis_keys.properties`.
- `src/test/java`: tests. Currently this contains a Spring context-load test.

Reference docs include `bussiness_rule.md`, `docs/frontend_context.md`, and `app_schema_sumary.csv`.

## Build, Test, and Development Commands

Use the Maven wrapper when possible.

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean package
.\mvnw.cmd spring-boot:run
```

`test` runs JUnit tests. `clean package` rebuilds and creates the Spring Boot jar under `target/`. `spring-boot:run` starts the API locally at `http://localhost:8080/vocab-learning`.

## Coding Style & Naming Conventions

Use Java 21 and existing Spring patterns. Keep controllers thin, put business rules in services, and keep database access in repositories. Use Lombok consistently with existing code (`@Data`, `@Builder`, `@RequiredArgsConstructor`, `@FieldDefaults`). Java fields use `camelCase`; database tables and columns use `snake_case`. IDs are typically UUID strings stored as `VARCHAR(36)`.

Responses should use `ApiResponse<T>`. Business errors should throw `AppException` with an `ErrorCode`.

## Testing Guidelines

Tests use JUnit 5 with Spring Boot test support. Add focused tests for new service logic, security behavior, repository queries, and validation. Prefer deterministic tests that do not require real SMTP, Firebase, Azure, or production data. Run `.\mvnw.cmd test` before submitting changes.

## Commit & Pull Request Guidelines

Current history uses short imperative messages, for example `add README.md` and `basic part`. Keep commits concise and scoped. Pull requests should include a summary, affected endpoints/modules, test results, migration/configuration notes, and any linked issue. Include API examples when request or response behavior changes.

## Security & Configuration Tips

Required environment variables include MySQL, Redis, JWT, mail, and Azure Translator settings. Do not commit secrets; `social-app.json` is ignored and must stay local. Do not log or expose passwords, password hashes, JWTs, push tokens, long private content, or listen challenge solutions. Add Redis key patterns to `redis_keys.properties` and access them through `RedisKeyProperties`.
