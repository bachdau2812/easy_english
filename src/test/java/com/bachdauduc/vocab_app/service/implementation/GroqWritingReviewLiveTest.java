package com.bachdauduc.vocab_app.service.implementation;

import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.repository.IeltsWritingExerciseRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Explicit opt-in only: makes a paid Groq call with synthetic essays and no database writes.
@EnabledIfSystemProperty(named = "groq.live", matches = "true")
class GroqWritingReviewLiveTest {
    @Test
    void task1() throws Exception {
        checkReview(1,
                "The chart shows car sales for companies A and B in 2020 and 2021. "
                        + "Summarise the information and make comparisons where relevant.",
                "Company A: 100 cars in 2020, 150 in 2021. Company B: 200 cars in 2020, 180 in 2021.",
                """
                The bar chart compares the number of cars sold by two companies, A and B, in 2020 and 2021.
                Overall, company B sold more cars in both years, although the gap between the two companies
                narrowed considerably. While sales at company A increased, company B experienced a modest decline.
                In 2020, company A sold 100 cars, whereas company B sold 200. This means that B sold twice
                as many vehicles as A, with a difference of 100 cars between them. By the following year,
                A had increased its sales to 150 cars. This represented an increase of 50 vehicles, or 50 percent,
                compared with its initial figure. In contrast, sales at company B fell from 200 to 180 cars
                over the same period, a reduction of 20 vehicles. Despite this decrease, B remained the larger
                seller in 2021. However, its lead over company A was reduced to just 30 cars.
                """);
    }

    @Test
    void task2() throws Exception {
        checkReview(2,
                "Some people think public transport should be free. Others believe passengers should pay. "
                        + "Discuss both views and give your opinion.", null,
                """
                Free public transport can make cities more accessible. People with low incomes can travel
                to work without worrying about fares. It may also encourage drivers to leave their cars at home,
                reducing congestion and pollution. However, removing fares means that taxpayers must fund the service.
                Without enough public funding, services could become less frequent and less reliable.
                Charging passengers provides money for maintenance and new vehicles. People who use the service
                contribute directly to its costs. On the other hand, high ticket prices can discourage those who
                need transport most. In my opinion, a balanced policy is better than making every trip free.
                Students, older people and people on low incomes should receive discounted fares, while others
                should pay affordable prices. Governments should also invest in reliable routes because low prices
                alone will not attract passengers if buses rarely arrive on time.
                """);
    }

    private void checkReview(int task, String problem, String visual, String essay) throws Exception {
        String key = System.getenv("GROQ_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getenv("GROK_API_KEY");
        }
        assertThat(key != null && !key.isBlank()).as("Groq API key available").isTrue();
        IeltsWritingExercise exercise = new IeltsWritingExercise();
        exercise.setId("synthetic-writing-live");
        exercise.setTaskType(task);
        exercise.setProblem(problem);
        exercise.setImageDescription(visual);
        IeltsWritingExerciseRepository repository = mock(IeltsWritingExerciseRepository.class);
        when(repository.findById(exercise.getId())).thenReturn(Optional.of(exercise));
        ObjectMapper mapper = new ObjectMapper();
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build()) {
            GroqWritingReview service = new GroqWritingReview(repository, mapper, client);
            ReflectionTestUtils.setField(service, "apiKey", key);
            service.loadReviewResources();
            JsonNode review = mapper.readTree(service.generateReview(exercise.getId(), "synthetic-user", essay));
            assertThat(review.path("taskType").asInt()).isEqualTo(task);
            assertThat(review.path("overallBand").asDouble()).isBetween(0.0, 9.0);
            assertThat(review.path("scores").size()).isEqualTo(4);
            assertThat(review.has("task" + task + "Analysis")).isTrue();
            System.out.println("Groq live review passed: task=" + task + ", contractValidated=true");
        }
    }
}
