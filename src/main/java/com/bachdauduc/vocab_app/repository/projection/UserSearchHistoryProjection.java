package com.bachdauduc.vocab_app.repository.projection;

import java.time.LocalDateTime;

public interface UserSearchHistoryProjection {
    String getId();

    String getUserId();

    String getWordId();

    String getWord();

    LocalDateTime getSearchedAt();
}
