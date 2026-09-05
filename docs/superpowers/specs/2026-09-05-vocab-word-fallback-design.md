# Saved Word Vietnamese Localization Fallback Design

## Goal

Update `GET /user-vocabularies/{userVocabId}/word` so a saved vocabulary row that references a standard `senseId` prefers an existing Vietnamese localization and falls back to the English sense when no Vietnamese localization exists.

## Data Flow

`UserVocabularyController` keeps its current API contract and delegates to `UserVocabularyService.getUserVocabWord(userVocabId)`.

`UserVocabularyService.loadSavedWord(...)` resolves the saved sense as follows:

1. If `senseLocalizedId` is present, keep the existing behavior: load that localization, request word data in its language, and retain only the saved localized sense.
2. If only `senseId` is present, query `word_sense_localizations` with `findFirstBySenseIdAndLangCode(senseId, "vi")`.
3. If a Vietnamese localization exists, request translated word data with `getWord(wordId, true, "vi")` and retain only the saved `senseId`.
4. If no Vietnamese localization exists, request untranslated word data with `getWord(wordId, false, null)` and retain only the saved `senseId`.

The existence check happens before translated word loading. This prevents `GetWordDataService` from invoking Azure Translator and creating a new Vietnamese localization when the table has no existing translation.

## Response Semantics

The endpoint continues returning the existing `ApiResponse<WordResponse>` shape. Only one saved sense remains in `WordResponse.senses`.

- Existing Vietnamese localization: the sense contains the Vietnamese translation through the current translated `WordResponse` mapping.
- No Vietnamese localization: the sense contains the original English definition and no generated translation.

No controller parameters, DTOs, database migrations, or error codes change.

The shared `loadSavedWord(...)` helper is also used by saved-vocabulary search results, so those results receive the same fallback behavior and remain consistent with the single-word endpoint.

## Error Handling

Existing behavior remains unchanged:

- Missing saved vocabulary returns `USER_VOCABULARY_NOT_FOUND`.
- Missing referenced word, localized sense, or standard sense returns `WORD_NOT_FOUND`.

## Testing

Focused service tests will verify:

- A saved `senseId` with an existing `vi` localization requests translated Vietnamese word data and returns only that sense.
- A saved `senseId` without an existing `vi` localization requests untranslated word data and returns only the English sense.
- The fallback path never requests translated data, so it cannot trigger automatic Azure translation.

After the focused tests pass, the full Maven test suite will be run.
