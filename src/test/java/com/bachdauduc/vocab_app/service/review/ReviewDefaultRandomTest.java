package com.bachdauduc.vocab_app.service.review;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewDefaultRandomTest {
    @Test
    void productionConstructorsUseRandomImplementationFromJavaBase() throws Exception {
        assertUsesJavaBaseRandom(
                new BalancedReviewQuizScheduler(),
                BalancedReviewQuizScheduler.class
        );
        assertUsesJavaBaseRandom(
                new ReviewQuizFactory(),
                ReviewQuizFactory.class
        );
        assertUsesJavaBaseRandom(
                new ReviewSnapshotCache(null, null, null, true),
                ReviewSnapshotCache.class
        );
    }

    private void assertUsesJavaBaseRandom(Object target, Class<?> type) throws Exception {
        Field field = type.getDeclaredField("random");
        field.setAccessible(true);
        assertThat(field.get(target)).isInstanceOf(Random.class);
    }
}
