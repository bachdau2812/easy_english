package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.dto.request.learningresource.InsertIeltsReadingSourceRequest;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingSourceResponse;
import com.bachdauduc.vocab_app.entity.Category;
import com.bachdauduc.vocab_app.entity.IeltsReadingSource;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.CategoryRepository;
import com.bachdauduc.vocab_app.repository.IeltsReadingSourceRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningResourceService {
    IeltsReadingSourceRepository ieltsReadingSourceRepository;
    CategoryRepository categoryRepository;

    public Page<IeltsReadingSourceResponse> getIeltsReadingSources(int page, int limit) {
        log.debug("Start service: method=getIeltsReadingSources, page={}, limit={}", page, limit);
        Page<IeltsReadingSourceResponse> sources = ieltsReadingSourceRepository
                .findAll(pageRequest(page, limit))
                .map(this::toIeltsReadingSourceResponse);
        log.info("IELTS reading sources loaded: page={}, limit={}, resultCount={}, totalElements={}",
                page, limit, sources.getNumberOfElements(), sources.getTotalElements());
        return sources;
    }

    public IeltsReadingSourceResponse insertIeltsReadingSource(InsertIeltsReadingSourceRequest request) {
        log.debug("Start service: method=insertIeltsReadingSource, categorySlug={}, title={}, contentLength={}",
                request.getCategorySlug(), request.getTitle(), request.getContent() == null ? 0 : request.getContent().length());
        Category category = categoryRepository.findFirstBySlug(request.getCategorySlug())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        log.debug("Reading category resolved: categorySlug={}, categoryId={}", request.getCategorySlug(), category.getId());

        IeltsReadingSource source = new IeltsReadingSource();
        source.setId(UUID.randomUUID().toString());
        source.setName(request.getName());
        source.setTitle(request.getTitle());
        source.setCategoryId(category.getId());
        source.setContent(request.getContent());

        IeltsReadingSource saved = ieltsReadingSourceRepository.save(source);
        log.info("IELTS reading source inserted: id={}, categoryId={}, title={}",
                saved.getId(), saved.getCategoryId(), saved.getTitle());
        return toIeltsReadingSourceResponse(saved);
    }

    public String insertListenExercisePlaceholder() {
        log.info("Placeholder called: method=insertListenExercisePlaceholder");
        return "insert listen_exercise will be implemented later";
    }

    public String insertListenAndTypeQuizPlaceholder() {
        log.info("Placeholder called: method=insertListenAndTypeQuizPlaceholder");
        return "insert listen-and-type quiz will be implemented later";
    }

    public String insertListenAndAnswerQuizPlaceholder() {
        log.info("Placeholder called: method=insertListenAndAnswerQuizPlaceholder");
        return "insert listen-and-answer quiz will be implemented later";
    }

    public String insertReadingQuizPlaceholder() {
        log.info("Placeholder called: method=insertReadingQuizPlaceholder");
        return "insert reading quiz will be implemented later";
    }

    public String generateReadingAndListeningQuizPlaceholder() {
        log.info("Placeholder called: method=generateReadingAndListeningQuizPlaceholder");
        return "generate reading and listening quiz will be implemented later";
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(Math.max(page, 0), Math.max(limit, 1));
    }

    private IeltsReadingSourceResponse toIeltsReadingSourceResponse(IeltsReadingSource source) {
        return IeltsReadingSourceResponse.builder()
                .id(source.getId())
                .name(source.getName())
                .title(source.getTitle())
                .categoryId(source.getCategoryId())
                .content(source.getContent())
                .createdAt(source.getCreatedAt())
                .updatedAt(source.getUpdatedAt())
                .build();
    }
}
