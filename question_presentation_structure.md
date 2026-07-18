# IELTS Reading Question Presentation Guide

This file describes how to render each question_group.question_type in the generated JSONL.

## Common Data Model

- Top level: `id`, `quiz`.
- `quiz.question_groups[]` is the render unit for a question block.
- Render `question_group.instruction` once per group.
- Render `question_group.context` when non-empty; it may contain lists, notes, summaries, tables, or placeholders like `{{Q12}}`.
- Render `question_group.shared_options` as the shared choice list for matching tasks or word-bank completion tasks.
- Render `question_group.questions[]` as the numbered user-answer controls.
- Do not show `answer`, `evidence_quote`, `explanation`, `difficulty`, or `skill` during the attempt; use them after submission/review.

## matching_features

- Count in dataset: 20
- UI control: Dropdown/select feature/entity label per statement.
- Answer shape: question.answer contains one shared option label, e.g. ["D"].
- Presentation:
  - Render group.instruction once.
  - Render group.context or group.shared_options as the list of people/entities/features.
  - For each question: show statement in stem and choose a label from shared_options.
  - Respect allow_option_reuse.

## matching_headings

- Count in dataset: 118
- UI control: Dropdown/select per paragraph, or drag heading to paragraph.
- Answer shape: question.answer contains one heading label, e.g. ["iv"].
- Presentation:
  - Render group.instruction once.
  - Render group.context as the List of Headings when present.
  - Render group.shared_options as reusable display list if context is empty or if you prefer structured rendering.
  - For each question: stem usually names a paragraph; answer is a Roman heading label.
  - Do not allow reuse unless allow_option_reuse is true; normally it is false.

## matching_information

- Count in dataset: 33
- UI control: Dropdown/select paragraph label per statement.
- Answer shape: question.answer contains one paragraph label, e.g. ["B"].
- Presentation:
  - Render group.instruction once, including reuse rule if any.
  - For each question: show number + statement from stem.
  - Choices are paragraph labels from group.source_paragraph_ids or passage paragraph labels.

## multiple_choice_multiple

- Count in dataset: 1
- UI control: Checkbox group; multiple selectable options per question.
- Answer shape: question.answer contains two or three option labels, e.g. ["A", "E"].
- Presentation:
  - Render instruction exactly because it states TWO or THREE answers.
  - Show question.options as checkboxes; enforce max selections from instruction/answer length.

## multiple_choice_single

- Count in dataset: 106
- UI control: Radio group; one selectable option per question.
- Answer shape: question.answer contains one option label, e.g. ["C"].
- Presentation:
  - Render group.instruction once at the top.
  - For each question: show number + stem, then question.options A-D as radio choices.
  - Do not show context when it is empty.

## sentence_completion

- Count in dataset: 217
- UI control: Text input per gap.
- Answer shape: question.answer contains exact passage words/numbers, usually one string.
- Presentation:
  - Render instruction and word_limit prominently.
  - Render each stem with placeholder like {{Q12}} replaced by a text input.
  - question.options and shared_options are empty.

## short_answer

- Count in dataset: 1
- UI control: Short text input.
- Answer shape: question.answer contains exact passage words/numbers.
- Presentation:
  - Render group.instruction and word_limit.
  - For each question: show number + stem, then a short text input.

## summary_completion

- Count in dataset: 3
- UI control: Text input per gap, or dropdown when shared_options is non-empty.
- Answer shape: question.answer contains exact words or a shared option label depending on the group.
- Presentation:
  - Render group.instruction and group.word_limit.
  - Render group.context as the summary text with {{Qn}} placeholders replaced by inputs/dropdowns.
  - Questions provide answer metadata for each placeholder; stems may be short labels.

## true_false_not_given

- Count in dataset: 224
- UI control: Three-option segmented/radio control: TRUE, FALSE, NOT GIVEN.
- Answer shape: question.answer contains one of TRUE, FALSE, NOT GIVEN.
- Presentation:
  - Render group.instruction once.
  - For each question: show number + stem/statement, then TRUE/FALSE/NOT GIVEN options.
  - question.options is intentionally empty.

## yes_no_not_given

- Count in dataset: 5
- UI control: Three-option segmented/radio control: YES, NO, NOT GIVEN.
- Answer shape: question.answer contains one of YES, NO, NOT GIVEN.
- Presentation:
  - Render like true_false_not_given, but labels are YES/NO/NOT GIVEN.
  - Use for writer views/claims rather than factual statements.
