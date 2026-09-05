package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.exception.GlobalExceptionHandler;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.service.UserVocabularyExportService;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class UserVocabularyExportControllerTest {
    @Mock UserVocabularyRepository repository;
    @Mock UserInfoRepository userInfoRepository;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new UserVocabularyExportService(repository, userInfoRepository);
        mvc = MockMvcBuilders.standaloneSetup(new UserVocabularyExportController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void downloadsActualExcelForJwtOwnerWithVietnameseDefault() throws Exception {
        when(userInfoRepository.existsById("owner")).thenReturn(true);
        when(repository.findUserVocabularyForExport("owner", "vi", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of()));

        var response = mvc.perform(get("/user-vocabularies/export")
                        .principal(new TestingAuthenticationToken("owner", null, "USER"))
                        .param("userId", "someone-else"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-vocabulary.xlsx\""))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn().getResponse();

        byte[] file = response.getContentAsByteArray();
        assertThat(response.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo(String.valueOf(file.length));
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(file))) {
            assertThat(workbook.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("meaning_practice");
        }
        verify(repository).findUserVocabularyForExport("owner", "vi", PageRequest.of(0, 500));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void acceptsExplicitTranslationLanguage() throws Exception {
        when(userInfoRepository.existsById("owner")).thenReturn(true);
        when(repository.findUserVocabularyForExport("owner", "fr", PageRequest.of(0, 500)))
                .thenReturn(new SliceImpl<>(List.of()));
        mvc.perform(get("/user-vocabularies/export").param("langCode", "fr")
                        .principal(new TestingAuthenticationToken("owner", null, "USER")))
                .andExpect(status().isOk());
        verify(repository).findUserVocabularyForExport("owner", "fr", PageRequest.of(0, 500));
    }

    @Test
    void rejectsUnauthenticatedDownload() throws Exception {
        mvc.perform(get("/user-vocabularies/export"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
        verifyNoInteractions(repository, userInfoRepository);
    }

    @Test
    void missingUserReturnsJsonErrorInsteadOfExcelAttachment() throws Exception {
        mvc.perform(get("/user-vocabularies/export")
                        .principal(new TestingAuthenticationToken("missing", null, "USER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(2003))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_DISPOSITION));
        verifyNoInteractions(repository);
    }
}
