package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.constant.UserVocabularyInfoType;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyInfoResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyLevelQuantityResponse;
import com.bachdauduc.vocab_app.service.UserVocabularyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserVocabularyInfoControllerTest {
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
    void returnsUserVocabularyInfoUsingExistingApiEnvelope() throws Exception {
        UserVocabularyInfoResponse response = UserVocabularyInfoResponse.builder()
                .userId("user-1")
                .infoType(UserVocabularyInfoType.VOCAB_QUANTITY)
                .totalQuantity(3L)
                .quantityByLevels(List.of(
                        levelQuantity(1, 2L),
                        levelQuantity(2, 1L),
                        levelQuantity(3, 0L),
                        levelQuantity(4, 0L),
                        levelQuantity(5, 0L),
                        levelQuantity(6, 0L)
                ))
                .reviewQuantity(null)
                .build();
        when(userVocabularyService.getUserVocabularyInfo("user-1", "VOCAB_QUANTITY"))
                .thenReturn(response);

        mockMvc.perform(get("/user-vocabularies/info")
                        .param("userId", "user-1")
                        .param("infoType", "VOCAB_QUANTITY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2000))
                .andExpect(jsonPath("$.result.userId").value("user-1"))
                .andExpect(jsonPath("$.result.infoType").value("VOCAB_QUANTITY"))
                .andExpect(jsonPath("$.result.totalQuantity").value(3))
                .andExpect(jsonPath("$.result.quantityByLevels.length()").value(6))
                .andExpect(jsonPath("$.result.quantityByLevels[0].level").value(1))
                .andExpect(jsonPath("$.result.quantityByLevels[0].quantity").value(2))
                .andExpect(jsonPath("$.result.reviewQuantity").value(nullValue()));
    }

    private UserVocabularyLevelQuantityResponse levelQuantity(int level, long quantity) {
        return UserVocabularyLevelQuantityResponse.builder()
                .level(level)
                .quantity(quantity)
                .build();
    }
}
