# Vocabulary Level Thresholds Design

## Goal

Advance vocabulary at levels 1, 2, and 3 after one correct review while
preserving all higher-level behavior.

## Rules

| Current level | Correct turns required |
|---|---:|
| 1 | 1 |
| 2 | 1 |
| 3 | 1 |
| 4 | 2 |
| 5 | 4 |
| 6 | Maximum level; no advancement |

No API, schema, Redis, wrong-answer, or review-interval behavior changes.

## Implementation

Keep the rule in `UserVocabularyService.requiredCorrectTurns` and express the
thresholds explicitly with a switch. The existing correct-review flow still
advances one level, resets `currentLevelCorrectTurns` to zero, and uses the
current next-review schedule.

## Verification

Add focused service tests for one-turn advancement at levels 1 through 3, the
unchanged two-turn threshold at level 4, the unchanged four-turn threshold at
level 5, correct-turn reset after advancement, and unchanged level 6 behavior.
Run the focused tests followed by the complete Maven test suite.
