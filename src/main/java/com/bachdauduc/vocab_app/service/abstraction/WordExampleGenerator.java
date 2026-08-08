package com.bachdauduc.vocab_app.service.abstraction;

import com.bachdauduc.vocab_app.service.model.GeneratedWordExample;
import com.bachdauduc.vocab_app.service.model.WordExampleGenerationInput;

import java.util.List;

public interface WordExampleGenerator {
    List<GeneratedWordExample> generate(List<WordExampleGenerationInput> inputs);
}
