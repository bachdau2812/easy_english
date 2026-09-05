package com.bachdauduc.vocab_app.controller;

import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.service.UserVocabularyExportService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user-vocabularies")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserVocabularyExportController {
    UserVocabularyExportService userVocabularyExportService;

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportVocabulary(
            Authentication authentication,
            @RequestParam(defaultValue = "vi") String langCode
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        byte[] file = userVocabularyExportService.exportVocabulary(authentication.getName(), langCode);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("my-vocabulary.xlsx").build().toString())
                .cacheControl(CacheControl.noStore())
                .contentLength(file.length)
                .body(file);
    }
}
