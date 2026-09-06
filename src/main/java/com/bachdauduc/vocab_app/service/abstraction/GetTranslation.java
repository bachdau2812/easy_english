package com.bachdauduc.vocab_app.service.abstraction;

import java.util.List;
import java.util.Map;

public interface GetTranslation {
    Map<String, String> translate(List<String> texts, String transLangCode);

    String translateHtml(String html, String transLangCode);
}
