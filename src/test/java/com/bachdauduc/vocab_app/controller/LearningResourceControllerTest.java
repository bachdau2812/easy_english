package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.dto.request.learningresource.IeltsWritingReviewRequest;
import com.bachdauduc.vocab_app.service.LearningResourceService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LearningResourceControllerTest {

    @Test
    void writingProblemProgressUsesAuthenticatedUser() {
        LearningResourceService service = mock(LearningResourceService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("authenticated-user");
        LearningResourceController controller = new LearningResourceController(service);

        controller.getIeltsWritingProblemsByTopic("Environment", null, authentication);

        verify(service).getIeltsWritingProblemsByTopic("Environment", "authenticated-user");
    }

    @Test
    void reviewIeltsWritingUsesAuthenticatedUserInsteadOfRequestUserId() {
        LearningResourceService service = mock(LearningResourceService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("authenticated-user");
        when(service.reviewIeltsWriting("exercise-1", "authenticated-user", "My essay"))
                .thenReturn("{}");
        LearningResourceController controller = new LearningResourceController(service);
        IeltsWritingReviewRequest request = IeltsWritingReviewRequest.builder()
                .exerciseId("exercise-1")
                .userId("spoofed-user")
                .userAnswer("My essay")
                .build();

        controller.reviewIeltsWriting(request, authentication);

        verify(service).reviewIeltsWriting("exercise-1", "authenticated-user", "My essay");
    }

    @Test
    void writingHistoryUsesAuthenticatedUser() {
        LearningResourceService service = mock(LearningResourceService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("authenticated-user");
        LearningResourceController controller = new LearningResourceController(service);

        controller.getIeltsWritingAttemptHistory("exercise-1", authentication);

        verify(service).getIeltsWritingAttemptHistory("authenticated-user", "exercise-1");
    }
}
