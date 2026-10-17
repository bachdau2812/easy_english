package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyExportProjection;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserVocabularyExportServiceTest {
    @Mock UserVocabularyRepository userVocabularyRepository;
    @Mock UserInfoRepository userInfoRepository;
    @InjectMocks UserVocabularyExportService service;

    @Test
    void exportsAllBatchesWithPracticeColumnsVietnameseAndNumericLevel() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        var first = item("Apple", "noun", "quả táo", 2);
        var second = item("Apple", "noun", "một nghĩa khác", 5);
        when(first.getExample1()).thenReturn("I eat an apple.");
        when(first.getExample2()).thenReturn("This apple is red.");
        when(second.getExample1()).thenReturn("Apple makes computers.");
        var page0 = PageRequest.of(0, 500);
        var page1 = PageRequest.of(1, 500);
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "vi", page0))
                .thenReturn(new SliceImpl<>(List.of(first), page0, true));
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "vi", page1))
                .thenReturn(new SliceImpl<>(List.of(second), page1, false));

        byte[] file = service.exportVocabulary("user-1", " VI ");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            var sheet = workbook.getSheet("My Vocabulary");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 8);
            assertThat(IntStream.range(0, 8).mapToObj(i -> sheet.getRow(0).getCell(i).getStringCellValue()))
                    .containsExactly("meaning_practice", "word", "pos", "word_sense",
                            "word_practice", "level", "langcode", "examples");
            var row = sheet.getRow(1);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Apple");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("noun");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("quả táo");
            assertThat(row.getCell(4).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(row.getCell(5).getNumericCellValue()).isEqualTo(2);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("vi");
            assertThat(row.getCell(7).getStringCellValue()).isEqualTo("- I eat an apple.\n- This apple is red.");
            assertThat(row.getCell(7).getCellStyle().getWrapText()).isTrue();
            assertThat(row.getHeightInPoints()).isGreaterThan(sheet.getDefaultRowHeightInPoints());
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue()).isEqualTo("- Apple makes computers.");
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("một nghĩa khác");
            assertThat(sheet.getPaneInformation().isFreezePane()).isTrue();
        }
        verify(userVocabularyRepository).findUserVocabularyForExport("user-1", "vi", page1);
    }

    @Test
    void emptyVocabularyStillDownloadsWorkbookWithHeadersAndDefaultsToVietnamese() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of()));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.exportVocabulary("user-1", null)))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1);
            assertThat(sheet.getRow(0).getLastCellNum()).isEqualTo((short) 8);
        }
    }

    @Test
    void preservesFormulaLikeTextAndHandlesMissingAndOverlongValues() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        var item = item("=1+1", null, "a".repeat(32766) + "😀", null);
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "fr", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of(item)));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.exportVocabulary("user-1", "fr")))) {
            var row = workbook.getSheetAt(0).getRow(1);
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(1).getStringCellValue()).isEqualTo("=1+1");
            assertThat(row.getCell(2).getStringCellValue()).isEmpty();
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("a".repeat(32766));
            assertThat(row.getCell(5).getCellType()).isEqualTo(CellType.BLANK);
            assertThat(row.getCell(6).getStringCellValue()).isEqualTo("fr");
            assertThat(row.getCell(7).getStringCellValue()).isEmpty();
            assertThat(row.getHeightInPoints()).isLessThanOrEqualTo(409.5f);
        }
    }

    @Test
    void rejectsMissingUserBeforeReadingVocabulary() {
        assertThatThrownBy(() -> service.exportVocabulary("missing", "vi"))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND));
        verifyNoInteractions(userVocabularyRepository);
    }

    @Test
    void displaysAllLinesOfFullMeaningWithWrappedTextAndExpandedRow() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        String fullMeaning = "Nghĩa đầy đủ thứ nhất.\nNghĩa đầy đủ thứ hai.\nNghĩa đầy đủ thứ ba.";
        var vocabulary = item("Apple", "noun", fullMeaning, 1);
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of(vocabulary)));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.exportVocabulary("user-1", "")))) {
            var sheet = workbook.getSheetAt(0);
            var row = sheet.getRow(1);
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo(fullMeaning);
            assertThat(row.getCell(3).getCellStyle().getWrapText()).isTrue();
            assertThat(row.getHeightInPoints()).isGreaterThanOrEqualTo(3 * sheet.getDefaultRowHeightInPoints());
        }
    }

    @Test
    void keepsRowsReadableWithoutMeaningOrExamplesAndFormatsOnlyNonblankSentences() throws Exception {
        when(userInfoRepository.existsById("user-1")).thenReturn(true);
        var empty = item("Apple", "noun", null, 1);
        when(empty.getExample2()).thenReturn("\r\n\t ");
        var sentence = item("Apple", "noun", null, 2);
        when(sentence.getExample1()).thenReturn("  =1+1\r\n  remains literal text.  ");
        when(sentence.getExample2()).thenReturn("  ");
        when(userVocabularyRepository.findUserVocabularyForExport("user-1", "vi", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of(empty, sentence)));

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(service.exportVocabulary("user-1", "vi")))) {
            var sheet = workbook.getSheetAt(0);
            var emptyRow = sheet.getRow(1);
            assertThat(emptyRow.getCell(3).getStringCellValue()).isEmpty();
            assertThat(emptyRow.getCell(7).getStringCellValue()).isEmpty();
            assertThat(emptyRow.getHeightInPoints()).isGreaterThanOrEqualTo(sheet.getDefaultRowHeightInPoints());
            assertThat(sheet.getRow(2).getCell(7).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(2).getCell(7).getStringCellValue())
                    .isEqualTo("- =1+1 remains literal text.");
        }
    }

    private UserVocabularyExportProjection item(String word, String pos, String meaning, Integer level) {
        var item = mock(UserVocabularyExportProjection.class);
        when(item.getWord()).thenReturn(word);
        when(item.getPos()).thenReturn(pos);
        when(item.getWordSense()).thenReturn(meaning);
        when(item.getLevel()).thenReturn(level);
        return item;
    }
}
