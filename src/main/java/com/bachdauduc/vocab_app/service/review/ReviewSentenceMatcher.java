package com.bachdauduc.vocab_app.service.review;

import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewTargetSpan;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Matches surface forms, never arbitrary stems. All offsets refer to the unmodified example. */
public final class ReviewSentenceMatcher {
    private static final String LEFT = "(?<![\\p{L}\\p{M}\\p{N}_-])(?<![\\p{L}\\p{M}]['’])";
    private static final String RIGHT = "(?![\\p{L}\\p{M}\\p{N}_-]|['’][\\p{L}\\p{M}])";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Set<String> PARTICLES = Set.of(
            "up", "down", "off", "on", "out", "in", "away", "back", "over", "around", "apart", "together", "along");
    private static final Pattern OBJECT_START = Pattern.compile(
            "(?iu)^(?:me|you|him|her|it|us|them|this|that|these|those|the|a|an|my|your|his|its|our|their|some|any|each|every|both|all)\\b");
    private static final Pattern CLAUSE_CONNECTOR = Pattern.compile(
            "(?iu)\\b(?:and|or|but|because|while|when|who|which|to)\\b");

    private ReviewSentenceMatcher() {
    }

    public static Optional<Match> find(String sentence, String word, String pos, Collection<String> dictionaryForms) {
        if (!StringUtils.hasText(sentence) || !StringUtils.hasText(word)) {
            return Optional.empty();
        }
        Set<String> forms = new LinkedHashSet<>();
        forms.add(word.trim());
        dictionaryForms.stream().filter(StringUtils::hasText).map(String::trim).forEach(forms::add);
        String[] tokens = word.trim().toLowerCase(Locale.ROOT).split("(?U)\\s+");
        boolean verb = isVerb(pos);
        if (verb) {
            String tail = tokens.length == 1 ? "" : " " + String.join(" ", Arrays.copyOfRange(tokens, 1, tokens.length));
            verbForms(tokens[0]).forEach(form -> forms.add(form + tail));
        } else if ("noun".equalsIgnoreCase(pos) && tokens.length == 1) {
            forms.add(plural(tokens[0]));
        }

        // Prefer a contiguous occurrence. Longer alternatives prevent a shorter form swallowing a phrase.
        String alternatives = forms.stream().sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .map(ReviewSentenceMatcher::phrasePattern).collect(Collectors.joining("|"));
        Matcher contiguous = Pattern.compile(LEFT + "(?:" + alternatives + ")" + RIGHT, FLAGS).matcher(sentence);
        if (contiguous.find()) {
            return Optional.of(new Match(List.of(span(sentence, contiguous.start(), contiguous.end()))));
        }

        // A bounded object between a verb and a particle is supported; never cross punctuation/clauses.
        if (!verb || tokens.length != 2 || !PARTICLES.contains(tokens[1])) {
            return Optional.empty();
        }
        Set<String> heads = forms.stream().map(form -> form.split("(?U)\\s+"))
                .filter(parts -> parts.length == 2 && parts[1].equalsIgnoreCase(tokens[1]))
                .map(parts -> parts[0]).collect(Collectors.toCollection(LinkedHashSet::new));
        String headPattern = heads.stream().map(Pattern::quote).collect(Collectors.joining("|"));
        String gap = "\\s+(?:[\\p{L}\\p{M}]+(?:['’][\\p{L}\\p{M}]+)?\\s+){1,4}?";
        Matcher separated = Pattern.compile(LEFT + "(" + headPattern + ")" + RIGHT
                + gap + "(" + Pattern.quote(tokens[1]) + ")" + RIGHT, FLAGS).matcher(sentence);
        while (separated.find()) {
            String object = sentence.substring(separated.end(1), separated.start(2)).trim();
            if (!OBJECT_START.matcher(object).find() || CLAUSE_CONNECTOR.matcher(object).find()) {
                continue;
            }
            return Optional.of(new Match(List.of(span(sentence, separated.start(1), separated.end(1)),
                    span(sentence, separated.start(2), separated.end(2)))));
        }
        return Optional.empty();
    }

    private static boolean isVerb(String pos) {
        if (pos == null) {
            return false;
        }
        return Set.of("verb", "phrasal verb", "v", "v.", "phrase").contains(pos.toLowerCase(Locale.ROOT).trim());
    }

    private static String phrasePattern(String form) {
        return Arrays.stream(form.split("(?U)\\s+")).map(Pattern::quote).collect(Collectors.joining("\\s+"));
    }

    private static VocabReviewTargetSpan span(String sentence, int start, int end) {
        return new VocabReviewTargetSpan(start, end, sentence.substring(start, end));
    }

    private static Set<String> verbForms(String word) {
        Set<String> forms = new LinkedHashSet<>();
        forms.add(plural(word));
        if (word.matches(".*[^aeiou]y$")) {
            forms.add(word.substring(0, word.length() - 1) + "ied");
        } else {
            forms.add(word + (word.endsWith("e") ? "d" : "ed"));
        }
        if (word.endsWith("ie")) {
            forms.add(word.substring(0, word.length() - 2) + "ying");
        } else if (word.endsWith("e") && !word.endsWith("ee") && !word.endsWith("ye") && !word.endsWith("oe")) {
            forms.add(word.substring(0, word.length() - 1) + "ing");
        } else {
            forms.add(word + "ing");
        }
        // Only short CVC stems: longer stressed forms are supplied by word_forms.
        if (word.matches("[^aeiou]*[aeiou][^aeiouwxy]") && word.length() >= 3) {
            String doubled = word + word.charAt(word.length() - 1);
            forms.add(doubled + "ed");
            forms.add(doubled + "ing");
        }
        return forms;
    }

    private static String plural(String word) {
        if (word.matches(".*[^aeiou]y$")) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        return word + (word.matches(".*(?:s|x|z|ch|sh|o)$") ? "es" : "s");
    }

    public record Match(List<VocabReviewTargetSpan> spans) {
        public Match {
            spans = List.copyOf(spans);
        }

        public String surface() {
            return spans.stream().map(VocabReviewTargetSpan::text).collect(Collectors.joining(" "));
        }

        /** Replacements correspond one-to-one to spans, retaining every intervening character. */
        public String replace(String sentence, List<String> replacements) {
            StringBuilder result = new StringBuilder();
            int previous = 0;
            for (int index = 0; index < spans.size(); index++) {
                var span = spans.get(index);
                result.append(sentence, previous, span.start()).append(replacements.get(index));
                previous = span.end();
            }
            return result.append(sentence, previous, sentence.length()).toString();
        }

        public List<String> splitReplacement(String replacement) {
            List<String> parts = new ArrayList<>();
            int offset = 0;
            for (var span : spans) {
                parts.add(replacement.substring(offset, offset + span.text().length()));
                offset += span.text().length() + 1;
            }
            return parts;
        }
    }
}
