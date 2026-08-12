# Null-safe review vocabulary sense lookup

## Goal

Prevent `ReviewVocabDataLoader` from throwing `NullPointerException` when a saved vocabulary has no `senseId`, while preserving support for standard senses, localized-only MOCHI senses, and records containing both identifiers.

## Data rules

- A standard saved vocabulary may have only `senseId`.
- A MOCHI saved vocabulary may have only `senseLocalizedId`; its `senseId` may legitimately be absent.
- A non-MOCHI localized vocabulary may contain both `senseId` and `senseLocalizedId`.
- When both identifiers exist, review content uses `senseLocalizedId` as the selected sense. `senseId` remains supplementary and may be used to load linked standard-sense data.

## Implementation

Add one null-safe map lookup helper in `ReviewVocabDataLoader`. The helper returns `null` when its key is null, empty, or blank, and otherwise delegates to the supplied map.

Use the helper when resolving the standard sense, localized sense, and translation for each `UserVocabulary`. This prevents any map implementation, including immutable maps returned by `Map.of()`, from receiving a null key. Existing localized-first selection in `buildSnapshot` remains unchanged.

Do not derive or persist a missing `senseId`, change vocabulary request validation, or change cache identities as part of this fix.

## Tests

Add focused regression coverage to `ReviewVocabDataLoaderTest`:

1. A MOCHI/localized-only vocabulary with `senseId = null` loads successfully and uses the localized meaning.
2. A non-MOCHI vocabulary containing both IDs loads successfully and prefers the localized meaning over the standard sense or its translation.
3. The existing standard-sense test continues to pass.

The localized-only test must fail against the current implementation with the production `NullPointerException`, proving that it captures the reported regression before production code is changed.

## Scope

Only the review data loader and its focused unit test are changed. Persistence rules, entities, repositories, controllers, and API contracts remain untouched.
