# Saved Vocabulary Search API Design

## Goal

Add a paginated API that searches only a user's saved vocabulary and returns each matching saved record together with full word data filtered to the exact standard or localized sense that the user saved.

## API Contract

The controller exposes:

```http
GET /user-vocabularies/search?userId={userId}&text={text}&isAutocomplete={boolean}&page={page}&limit={limit}
```

- `userId` and `text` are required request parameters.
- `isAutocomplete` defaults to `false`.
- `page` defaults to `0` and negative values are normalized to `0` through the existing page helper.
- `limit` defaults to `20` and values below `1` are normalized to `1` through the existing page helper.
- When `isAutocomplete=false`, `text` is trimmed, lowercased, and matched exactly against `words.normalized_word`.
- When `isAutocomplete=true`, the normalized text is matched as a prefix against `words.normalized_word`.
- Blank search text produces an empty page after validating that the user exists.
- Results are restricted to saved vocabulary rows owned by `userId`.

The response uses the existing `ApiResponse<T>` envelope with a `Page<UserVocabularySearchResponse>` result. Each item has this shape:

```json
{
  "userVocabulary": {
    "id": "user-vocab-id",
    "userId": "user-id",
    "wordId": "word-id",
    "word": "example",
    "senseId": "sense-id",
    "senseLocalizedId": null,
    "level": 2,
    "currentLevelCorrectTurns": 1,
    "nextReviewAt": "2026-09-05T12:00:00",
    "createdAt": "2026-09-01T12:00:00",
    "updatedAt": "2026-09-05T12:00:00"
  },
  "word": {
    "wordId": "word-id",
    "word": "example",
    "senses": [
      {
        "senseId": "sense-id",
        "shortMeaning": "a representative instance",
        "definition": "something that represents a general rule"
      }
    ]
  }
}
```

`UserVocabularySearchResponse` contains two fields:

- `UserVocabularyResponse userVocabulary`
- `WordResponse word`

## Components and Data Flow

`UserVocabularyController` accepts the request parameters and delegates to `UserVocabularyService`.

`UserVocabularyRepository` adds two paginated native queries. Both join `user_vocabularies` to `words`, filter by `user_id`, and return `UserVocabularyProjection` so the saved record includes the display word. One query performs exact matching; the other performs prefix matching. Results are ordered deterministically by normalized word, display word, newest saved record, and saved vocabulary ID.

`UserVocabularyService` validates that the user exists, normalizes the search text, chooses the exact or prefix repository query, and maps each projection to `UserVocabularySearchResponse`. Word detail loading reuses `GetWordDataService.getWord(...)` and the same saved-sense filtering rules already used by `getUserVocabWord(...)`:

- For `senseId`, load untranslated word data and retain only that sense.
- For `senseLocalizedId`, load its localization to determine the language, load translated word data, and retain the localized sense. The standard sense ID is accepted as the existing fallback for localized data.

The filtering implementation is shared between single-word lookup and search mapping so the two endpoints cannot diverge. The dictionary response is loaded through the existing Redis-backed path. The service does not insert search-history records for this saved-vocabulary search.

## Result Semantics

Every saved vocabulary row remains a separate result. If a user saved two senses of the same word, the page contains two items with distinct `userVocabulary.id` values and each item's `word.senses` contains only its corresponding saved sense.

The endpoint returns complete `WordResponse` data, including categories, sounds, idioms, forms, and relation, while restricting only the `senses` collection. Pagination metadata represents saved vocabulary rows, not distinct spellings.

## Error Handling

- A missing user produces the existing `USER_NOT_FOUND` business error.
- A saved row that references missing word or sense data produces the existing `WORD_NOT_FOUND` business error, matching the current single saved-word endpoint.
- A valid search with no matches returns an empty page.
- No new error codes or database migrations are required.

## Testing

Repository tests cover exact and prefix matching, user isolation, multiple saved senses for one word, pagination totals, and deterministic ordering.

Service tests cover user validation, blank text, normalization, exact/prefix query selection, standard-sense filtering, localized-sense filtering, preservation of separate saved records, and propagation of missing referenced data errors.

Controller tests verify parameter defaults, delegation, the `ApiResponse` envelope, page metadata, saved vocabulary metadata, and that each returned `WordResponse` contains only the saved sense.

The full Maven test suite must pass before completion.