package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularySearchResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.service.UserVocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserVocabularySearchControllerTest {
    @Mock
    UserVocabularyService userVocabularyService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserVocabularyController(userVocabularyService))
                .build();
    }

    @Test
    void returnsSavedVocabularyPageUsingDefaultSearchParameters() throws Exception {
        UserVocabularySearchResponse item = UserVocabularySearchResponse.builder()
                .userVocabulary(UserVocabularyResponse.builder()
                        .id("saved-1")
                        .userId("user-1")
                        .wordId("word-1")
                        .word("Apple")
                        .senseId("sense-1")
                        .level(2)
                        .build())
                .word(WordResponse.builder()
                        .wordId("word-1")
                        .word("Apple")
                        .senses(List.of(WordSenseResponse.builder()
                                .senseId("sense-1")
                                .shortMeaning("a fruit")
                                .build()))
                        .build())
                .build();
        when(userVocabularyService.searchUserVocabulary(
                "user-1", "app", false, 0, 20
        )).thenReturn(new PageImpl<>(
                List.of(item),
                PageRequest.of(0, 20),
                1
        ));

        mockMvc.perform(get("/user-vocabularies/search")
                        .principal(() -> "user-1")
                        .param("text", "app"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.message").value("Search user vocabularies successfully"))
                .andExpect(jsonPath("$.result.totalElements").value(1))
                .andExpect(jsonPath("$.result.content[0].userVocabulary.id").value("saved-1"))
                .andExpect(jsonPath("$.result.content[0].userVocabulary.word").value("Apple"))
                .andExpect(jsonPath("$.result.content[0].word.wordId").value("word-1"))
                .andExpect(jsonPath("$.result.content[0].word.senses.length()").value(1))
                .andExpect(jsonPath("$.result.content[0].word.senses[0].senseId").value("sense-1"))
                .andExpect(jsonPath("$.result.content[0].word.senses[0].shortMeaning").value("a fruit"));

        verify(userVocabularyService)
                .searchUserVocabulary("user-1", "app", false, 0, 20);
    }

    @Test
    void delegatesExplicitAutocompleteAndPaginationParameters() throws Exception {
        when(userVocabularyService.searchUserVocabulary(
                "user-1", "app", true, 2, 5
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 5), 0));

        mockMvc.perform(get("/user-vocabularies/search")
                        .principal(() -> "user-1")
                        .param("text", "app")
                        .param("isAutocomplete", "true")
                        .param("page", "2")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.content.length()").value(0));

        verify(userVocabularyService)
                .searchUserVocabulary("user-1", "app", true, 2, 5);
    }

    @Test
    void derivesUserIdFromAuthenticatedPrincipal() throws Exception {
        when(userVocabularyService.searchUserVocabulary(
                "authenticated-user", "app", false, 0, 20
        )).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/user-vocabularies/search")
                        .principal(() -> "authenticated-user")
                        .param("userId", "another-user")
                        .param("text", "app"))
                .andExpect(status().isOk());

        verify(userVocabularyService)
                .searchUserVocabulary("authenticated-user", "app", false, 0, 20);
    }
}
