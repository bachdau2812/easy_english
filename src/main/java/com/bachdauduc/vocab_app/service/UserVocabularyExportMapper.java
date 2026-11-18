package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyExportResponse;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import org.apache.poi.ss.SpreadsheetVersion;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class UserVocabularyExportMapper {
    private UserVocabularyExportMapper() {
    }

    public static String normalizeLanguage(String langCode) {
        return StringUtils.hasText(langCode) ? langCode.trim().toLowerCase(Locale.ROOT) : "vi";
    }

    public static UserVocabularyExportResponse toResponse(
            UserVocabularyExportProjection vocabulary,
            String langCode
    ) {
        String examples = Stream.of(vocabulary.getExample1(), vocabulary.getExample2())
                .filter(StringUtils::hasText)
                .map(example -> "- " + example.strip().replaceAll("\\s*\\R\\s*", " "))
                .collect(Collectors.joining("\n"));

        return UserVocabularyExportResponse.builder()
                .meaningPractice("")
                .word(cellText(vocabulary.getWord()))
                .pos(cellText(vocabulary.getPos()))
                .wordSense(cellText(vocabulary.getWordSense()))
                .wordPractice("")
                .level(vocabulary.getLevel())
                .langCode(cellText(normalizeLanguage(langCode)))
                .examples(cellText(examples))
                .build();
    }

    private static String cellText(String value) {
        if (value == null) {
            return "";
        }
        int maxLength = SpreadsheetVersion.EXCEL2007.getMaxTextLength();
        if (value.length() <= maxLength) {
            return value;
        }
        int end = Character.isHighSurrogate(value.charAt(maxLength - 1)) ? maxLength - 1 : maxLength;
        return value.substring(0, end);
    }
}
