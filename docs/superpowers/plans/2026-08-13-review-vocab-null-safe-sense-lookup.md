# Review Vocabulary Null-safe Sense Lookup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Load standard, localized-only MOCHI, and dual-ID review vocabularies without passing null sense identifiers to maps.

**Architecture:** Keep the existing batch loading and localized-first snapshot behavior. Add a generic lookup helper that returns null for a null or blank key before calling `Map.get`, then exercise the public `load` method with focused tests.

**Tech Stack:** Java 21, Spring Boot, JUnit 5, Mockito, AssertJ, Maven Wrapper

## Global Constraints

- A MOCHI vocabulary may legitimately have `senseId = null` and only `senseLocalizedId`.
- When both identifiers exist, `senseLocalizedId` determines review content and `senseId` is supplementary.
- Do not change persistence, request validation, cache identities, repositories, controllers, or API contracts.

---

### Task 1: Make review sense lookups null-safe

**Files:**
- Modify: `src/test/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoaderTest.java`
- Modify: `src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoader.java:116-126`

**Interfaces:**
- Consumes: `ReviewVocabDataLoader.load(List<UserVocabulary>, String)`.
- Produces: private `<T> T valueByTextKey(Map<String, T> values, String key)`.

- [ ] **Step 1: Add a localized-only regression test**

```java
@Test
void loadsLocalizedOnlySenseWithoutLookingUpANullStandardSenseId() {
    UserVocabulary vocabulary = new UserVocabulary();
    vocabulary.setId("uv-1");
    vocabulary.setWordId("word-1");
    vocabulary.setSenseLocalizedId("localization-1");

    WordSenseLocalization localizedSense = new WordSenseLocalization();
    localizedSense.setId("localization-1");
    localizedSense.setWordId("word-1");
    localizedSense.setSource("MOCHI");
    localizedSense.setLangCode("vi");
    localizedSense.setShortMeaning("MOCHI meaning");

    when(snapshotCache.lookup(any())).thenReturn(
            new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 0L)));
    when(wordRepository.findAllById(any())).thenReturn(List.of(word()));
    when(wordSenseRepository.findAllById(any())).thenReturn(List.of());
    when(wordSenseLocalizationRepository.findAllById(any())).thenReturn(List.of(localizedSense));
    when(wordSoundRepository.findByWordIdIn(any())).thenReturn(List.of());
    when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of());

    Map<String, ReviewVocabSnapshot> result = loader.load(List.of(vocabulary), "vi");

    assertThat(result).containsKey("uv-1");
    assertThat(result.get("uv-1").meaning()).isEqualTo("MOCHI meaning");
}
```

- [ ] **Step 2: Run the localized-only test and verify RED**

Run:

```powershell
.\mvnw.cmd -Dtest=ReviewVocabDataLoaderTest#loadsLocalizedOnlySenseWithoutLookingUpANullStandardSenseId test
```

Expected: `NullPointerException` from `ImmutableCollections$MapN.get` at the translation lookup.

- [ ] **Step 3: Add the minimal null-safe lookup**

```java
ReviewVocabSnapshot snapshot = buildSnapshot(
        vocabulary,
        word,
        valueByTextKey(senses, vocabulary.getSenseId()),
        valueByTextKey(localizedSenses, vocabulary.getSenseLocalizedId()),
        valueByTextKey(translationsBySense, vocabulary.getSenseId()),
        soundsByWord.getOrDefault(vocabulary.getWordId(), List.of()),
        examplesBySense.getOrDefault(exampleSenseId(vocabulary), List.of()),
        exampleTranslations,
        langCode
);
```

```java
private <T> T valueByTextKey(Map<String, T> values, String key) {
    return StringUtils.hasText(key) ? values.get(key) : null;
}
```

- [ ] **Step 4: Re-run the localized-only test and verify GREEN**

Run the command from Step 2. Expected: `BUILD SUCCESS`, zero failures and errors.

- [ ] **Step 5: Add dual-ID characterization coverage**

```java
@Test
void prefersLocalizedSenseWhenBothSenseIdentifiersExist() {
    UserVocabulary vocabulary = new UserVocabulary();
    vocabulary.setId("uv-1");
    vocabulary.setWordId("word-1");
    vocabulary.setSenseId("sense-1");
    vocabulary.setSenseLocalizedId("localization-1");

    WordSenseLocalization localizedSense = new WordSenseLocalization();
    localizedSense.setId("localization-1");
    localizedSense.setWordId("word-1");
    localizedSense.setSenseId("sense-1");
    localizedSense.setSource("CUSTOM");
    localizedSense.setLangCode("vi");
    localizedSense.setShortMeaning("selected localized meaning");

    WordSenseLocalization standardTranslation = new WordSenseLocalization();
    standardTranslation.setId("translation-1");
    standardTranslation.setWordId("word-1");
    standardTranslation.setSenseId("sense-1");
    standardTranslation.setSource("CUSTOM");
    standardTranslation.setLangCode("vi");
    standardTranslation.setShortMeaning("standard translation");

    when(snapshotCache.lookup(any())).thenReturn(
            new ReviewSnapshotLookup(Map.of(), Map.of("word-1", 0L)));
    when(wordRepository.findAllById(any())).thenReturn(List.of(word()));
    when(wordSenseRepository.findAllById(any())).thenReturn(List.of(sense()));
    when(wordSenseLocalizationRepository.findAllById(any())).thenReturn(List.of(localizedSense));
    when(wordSenseLocalizationRepository.findBySenseIdInAndLangCode(any(), eq("vi")))
            .thenReturn(List.of(standardTranslation));
    when(wordSoundRepository.findByWordIdIn(any())).thenReturn(List.of());
    when(wordExampleRepository.findBySenseIdIn(any())).thenReturn(List.of());

    Map<String, ReviewVocabSnapshot> result = loader.load(List.of(vocabulary), "vi");

    assertThat(result.get("uv-1").meaning()).isEqualTo("selected localized meaning");
}
```

- [ ] **Step 6: Run the loader tests**

```powershell
.\mvnw.cmd -Dtest=ReviewVocabDataLoaderTest test
```

Expected: standard, localized-only, and dual-ID tests pass.

- [ ] **Step 7: Run project verification**

```powershell
.\mvnw.cmd test
git diff --check
```

Expected: Maven reports `BUILD SUCCESS`; Git reports no whitespace errors.

- [ ] **Step 8: Commit the focused fix**

```powershell
git add docs/superpowers/plans/2026-08-13-review-vocab-null-safe-sense-lookup.md src/main/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoader.java src/test/java/com/bachdauduc/vocab_app/service/review/ReviewVocabDataLoaderTest.java
git commit -m "fix localized review sense lookup"
```
