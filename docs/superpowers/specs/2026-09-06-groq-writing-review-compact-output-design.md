# Groq Writing Review Compact Output Design

## Problem

`GroqWritingReview` currently appends the complete JSON Schema to the system prompt. The schemas repeat object metadata, required-field lists, and array item definitions. With `openai/gpt-oss-120b`, the same valid request can intermittently fail with `json_validate_failed`; captured failed generations show malformed adjacent arrays in deeply structured Task 1 output.

## Goal

Make Groq output more reliable without changing the two task-specific prompt files, the stored review JSON contract, the endpoint response, or the database schema.

## Design

The existing Task 1 and Task 2 JSON Schemas remain the authoritative validation contracts inside the application. Before sending a request, `GroqWritingReview` recursively converts the selected schema into a compact JSON output template:

- objects include every declared property;
- strings use an empty string placeholder;
- integers and numbers use `0`;
- booleans use `false`;
- arrays contain one representative item so the model can see the item shape;
- the prompt tells the model to replace placeholders with review content and use an empty array when there are no supported items.

Only this compact template is appended to the system prompt. The full JSON Schema is no longer sent to Groq. After receiving the response, the existing recursive schema validation, evidence filtering, score calculation, response-size guard, and persistence flow remain unchanged.

## Provider Error Handling

The first request uses JSON object mode, temperature `0.2`, medium reasoning effort, and the current token limit. If Groq returns HTTP 400 with provider code `json_validate_failed`, the service retries the same request once. Other 4xx responses are not retried. A second validation failure is returned through the existing `WRITING_REVIEW_FAILED` application error.

Logs include only the exercise identifier and sanitized provider metadata. Generated review text and learner essays are never logged.

## Testing

Focused unit tests will verify:

- the provider prompt contains the compact template and no JSON Schema keywords such as `additionalProperties` or `required`;
- nested object and array item shapes are represented in the template;
- `json_validate_failed` is retried exactly once;
- other 4xx responses are not retried;
- the existing response contract validation and calculated scores remain unchanged.

The full Maven test suite will run after the focused tests. Opt-in live tests will call Groq with synthetic Task 1 and Task 2 essays to verify the actual provider response without exposing real learner content.
