package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.exception.GlobalExceptionHandler;
import com.bachdauduc.vocab_app.service.UserVocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.mockito.Answers;
import org.mockito.Mockito;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserVocabularyListControllerTest {
    private final AtomicReference<String> requestedUserId = new AtomicReference<>();
    private final AtomicReference<String> requestedLanguage = new AtomicReference<>();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Map<String, Object> vocabulary = new LinkedHashMap<>();
        vocabulary.put("meaning_practice", "");
        vocabulary.put("word", "Apple");
        vocabulary.put("pos", "noun");
        vocabulary.put("word_sense", "a fruit");
        vocabulary.put("word_practice", "");
        vocabulary.put("level", 2);
        vocabulary.put("langcode", "en");
        vocabulary.put("examples", "- I eat an apple.");

        UserVocabularyService userVocabularyService = Mockito.mock(UserVocabularyService.class, invocation -> {
            if (invocation.getMethod().getName().equals("getAllUserVocabularies")) {
                requestedUserId.set((String) invocation.getArgument(0));
                requestedLanguage.set((String) invocation.getArgument(1));
                return List.of(vocabulary);
            }
            return Answers.RETURNS_DEFAULTS.answer(invocation);
        });
        mockMvc = MockMvcBuilders.standaloneSetup(new UserVocabularyController(userVocabularyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsExcelCompatibleListForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/user-vocabularies/all")
                        .principal(new TestingAuthenticationToken("owner", null, "USER"))
                        .param("userId", "someone-else")
                        .param("langCode", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("Get user vocabularies successfully"))
                .andExpect(jsonPath("$.result.length()").value(1))
                .andExpect(jsonPath("$.result[0].meaning_practice").value(""))
                .andExpect(jsonPath("$.result[0].word").value("Apple"))
                .andExpect(jsonPath("$.result[0].pos").value("noun"))
                .andExpect(jsonPath("$.result[0].word_sense").value("a fruit"))
                .andExpect(jsonPath("$.result[0].word_practice").value(""))
                .andExpect(jsonPath("$.result[0].level").value(2))
                .andExpect(jsonPath("$.result[0].langcode").value("en"))
                .andExpect(jsonPath("$.result[0].examples").value("- I eat an apple."));

        assertThat(requestedUserId.get()).isEqualTo("owner");
        assertThat(requestedLanguage.get()).isEqualTo("EN");
    }

    @Test
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(get("/user-vocabularies/all"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(1001));
    }
}
