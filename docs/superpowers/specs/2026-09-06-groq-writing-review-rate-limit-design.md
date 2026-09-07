# Groq Writing Review Rate-Limit Design

## Goal

Reduce token usage and make Groq `429 rate_limit_exceeded` predictable without changing the stored IELTS review JSON contract or splitting a review into multiple model calls.

## Request design

- Keep one static system prompt for each writing task. Put the task statement, Task 1 image description, word count, and learner essay in the user message so Groq can reuse the stable prompt prefix.
- Keep the existing compact JSON template and local full-schema validation.
- Use `reasoning_effort=low` and `max_completion_tokens=5000`.
- Keep the IELTS scoring rules in the two task-specific prompts while removing repeated wording and limiting list sizes: two items per criterion, three grammar issues, three lexical issues, two successful grammar examples, and three priorities.

## Retry and concurrency

- Permit one in-flight Groq writing-review call per application instance. A concurrent request fails quickly with the writing-review rate-limit error.
- Make at most two provider calls per review. Retry once after either `json_validate_failed` or HTTP 429.
- For HTTP 429, honor `Retry-After`, accepting integer or decimal seconds. Wait only if the requested delay is at most 30 seconds; otherwise return HTTP 429 immediately without an early retry. If the retry also fails, return a dedicated application error with HTTP 429.
- Preserve the thread interrupt flag and release the permit on every exit path.

## Observability and privacy

- Log provider status plus safe rate-limit and usage numbers only.
- Never log the prompt, task, essay, generated review, provider message, key, or organization identifier.

## Verification

- Unit tests inspect the request, retry count, Retry-After parsing, rate-limit mapping, and permit release.
- Existing validation, scoring, evidence filtering, controller, and live opt-in tests remain compatible.
- Run the focused test class and complete deterministic Maven suite.

## Implementation clarification (2026-09-07)

The 30-second budget bounds retry waiting, not total endpoint time. Each provider HTTP call retains its existing 120-second timeout. The maximum is two provider calls total, including a JSON-validation retry. Missing or malformed Retry-After falls back to one second. Concurrency uses a nonblocking semaphore per service instance; it is not a distributed token limiter.
