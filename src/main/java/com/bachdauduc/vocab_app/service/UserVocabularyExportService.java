package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserVocabularyExportService {
    private static final int BATCH_SIZE = 500;
    // Conservative estimate for the two 80-character-wide wrapped columns.
    private static final int WRAPPED_LINE_LENGTH = 60;
    private static final float MAX_ROW_HEIGHT_POINTS = 409.5f;
    private static final String[] HEADERS = {
            "meaning_practice", "word", "pos", "word_sense", "word_practice", "level", "langcode", "examples"
    };

    UserVocabularyRepository userVocabularyRepository;
    UserInfoRepository userInfoRepository;

    @Transactional(readOnly = true)
    public byte[] exportVocabulary(String userId, String langCode) {
        if (!userInfoRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        String language = StringUtils.hasText(langCode) ? langCode.trim().toLowerCase(Locale.ROOT) : "vi";

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.setCompressTempFiles(true);
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            CellStyle wrappedStyle = workbook.createCellStyle();
            wrappedStyle.setWrapText(true);

            Sheet sheet = createSheet(workbook, headerStyle);
            int rowIndex = 1;
            PageRequest pageable = PageRequest.of(0, BATCH_SIZE);
            Slice<UserVocabularyExportProjection> batch;
            do {
                batch = userVocabularyRepository.findUserVocabularyForExport(userId, language, pageable);
                for (UserVocabularyExportProjection vocabulary : batch) {
                    if (rowIndex == SpreadsheetVersion.EXCEL2007.getMaxRows()) {
                        sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADERS.length - 1));
                        sheet = createSheet(workbook, headerStyle);
                        rowIndex = 1;
                    }
                    Row row = sheet.createRow(rowIndex++);
                    // Explicit string cells keep values beginning with '=' as text, not formulas.
                    row.createCell(0);
                    row.createCell(1).setCellValue(cellText(vocabulary.getWord()));
                    row.createCell(2).setCellValue(cellText(vocabulary.getPos()));
                    String meaning = cellText(vocabulary.getWordSense());
                    row.createCell(3).setCellValue(meaning);
                    row.getCell(3).setCellStyle(wrappedStyle);
                    row.createCell(4);
                    if (vocabulary.getLevel() != null) {
                        row.createCell(5).setCellValue(vocabulary.getLevel());
                    } else {
                        row.createCell(5);
                    }
                    row.createCell(6).setCellValue(language);
                    String examples = cellText(Stream.of(vocabulary.getExample1(), vocabulary.getExample2())
                            .filter(StringUtils::hasText)
                            .map(example -> "- " + example.strip().replaceAll("\\s*\\R\\s*", " "))
                            .collect(Collectors.joining("\n")));
                    row.createCell(7).setCellValue(examples);
                    row.getCell(7).setCellStyle(wrappedStyle);
                    int lines = Math.max(wrappedLines(meaning), wrappedLines(examples));
                    row.setHeightInPoints(Math.min(MAX_ROW_HEIGHT_POINTS,
                            lines * sheet.getDefaultRowHeightInPoints() + 2));
                }
                pageable = pageable.next();
            } while (batch.hasNext());
            sheet.setAutoFilter(new CellRangeAddress(0, rowIndex - 1, 0, HEADERS.length - 1));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            log.error("Vocabulary export failed: userId={}", userId);
            throw new AppException(ErrorCode.USER_VOCABULARY_EXPORT_FAILED);
        }
    }

    private Sheet createSheet(SXSSFWorkbook workbook, CellStyle headerStyle) {
        String name = workbook.getNumberOfSheets() == 0
                ? "My Vocabulary" : "My Vocabulary " + (workbook.getNumberOfSheets() + 1);
        Sheet sheet = workbook.createSheet(name);
        sheet.createFreezePane(0, 1);
        int[] widths = {32, 28, 16, 80, 28, 10, 12, 80};
        Row header = sheet.createRow(0);
        for (int column = 0; column < HEADERS.length; column++) {
            header.createCell(column).setCellValue(HEADERS[column]);
            header.getCell(column).setCellStyle(headerStyle);
            sheet.setColumnWidth(column, widths[column] * 256);
        }
        return sheet;
    }

    private int wrappedLines(String value) {
        return Math.max(1, value.lines()
                .mapToInt(line -> Math.max(1, (line.length() + WRAPPED_LINE_LENGTH - 1) / WRAPPED_LINE_LENGTH))
                .sum());
    }

    private String cellText(String value) {
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
