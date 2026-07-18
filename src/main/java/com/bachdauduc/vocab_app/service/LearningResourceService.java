package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;

import com.bachdauduc.vocab_app.dto.request.learningresource.InsertIeltsReadingSourceRequest;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingQuizResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsReadingSourceResponse;
import com.bachdauduc.vocab_app.dto.response.learningresource.IeltsWritingProblemSummaryResponse;
import com.bachdauduc.vocab_app.entity.Category;
import com.bachdauduc.vocab_app.entity.IeltsReadingSource;
import com.bachdauduc.vocab_app.entity.IeltsWritingExercise;
import com.bachdauduc.vocab_app.entity.IeltsWritingReference;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.CategoryRepository;
import com.bachdauduc.vocab_app.repository.IeltsReadingQuizGroupRepository;
import com.bachdauduc.vocab_app.repository.IeltsReadingSourceRepository;
import com.bachdauduc.vocab_app.repository.IeltsWritingExerciseRepository;
import com.bachdauduc.vocab_app.repository.IeltsWritingReferenceRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.projection.IeltsReadingQuizRowProjection;
import com.bachdauduc.vocab_app.repository.projection.IeltsWritingProblemSummaryProjection;
import com.bachdauduc.vocab_app.service.abstraction.WritingReview;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LearningResourceService {
    static final String ACADEMIC_READING_MODULE = "academic_reading";
    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    IeltsReadingSourceRepository ieltsReadingSourceRepository;
    CategoryRepository categoryRepository;
    IeltsReadingQuizGroupRepository ieltsReadingQuizGroupRepository;
    IeltsWritingExerciseRepository ieltsWritingExerciseRepository;
    IeltsWritingReferenceRepository ieltsWritingReferenceRepository;
    UserVocabAttemptRepository userVocabAttemptRepository;
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;
    WritingReview writingReview;

    public Page<IeltsReadingSourceResponse> getIeltsReadingSources(int page, int limit) {
        log.debug("Start service: method=getIeltsReadingSources, page={}, limit={}", page, limit);
        Page<IeltsReadingSourceResponse> sources = ieltsReadingSourceRepository
                .findAll(pageRequest(page, limit))
                .map(this::toIeltsReadingSourceResponse);
        log.info("IELTS reading sources loaded: page={}, limit={}, resultCount={}, totalElements={}",
                page, limit, sources.getNumberOfElements(), sources.getTotalElements());
        return sources;
    }

    public List<String> getIeltsReadingCategories() {
        log.debug("Start service: method=getIeltsReadingCategories");
        List<String> categories = ieltsReadingSourceRepository.findDistinctNames();
        log.info("IELTS reading categories loaded: count={}", categories.size());
        return categories;
    }

    public Page<IeltsReadingSourceResponse> getIeltsReadingSourcesByCategory(String name, int page, int limit) {
        log.debug("Start service: method=getIeltsReadingSourcesByCategory, name={}, page={}, limit={}",
                name, page, limit);
        Page<IeltsReadingSourceResponse> sources = ieltsReadingSourceRepository
                .findByName(name, pageRequest(page, limit))
                .map(this::toIeltsReadingSourceResponse);
        log.info("IELTS reading sources by category loaded: name={}, page={}, limit={}, resultCount={}, totalElements={}",
                name, page, limit, sources.getNumberOfElements(), sources.getTotalElements());
        return sources;
    }

    public IeltsReadingQuizResponse getIeltsReadingQuiz(String readingId, String userId) {
        String cacheKey = redisKeyProperties.readingQuizKey(readingId);
        IeltsReadingQuizResponse quizResponse = RedisUtil.deserialize(redisTemplate.opsForValue().get(cacheKey),
                IeltsReadingQuizResponse.class);

        if (quizResponse == null) {
            IeltsReadingSource source = ieltsReadingSourceRepository.findById(readingId)
                    .orElseThrow(() -> new AppException(ErrorCode.IELTS_READING_SOURCE_NOT_FOUND));
            List<IeltsReadingQuizRowProjection> rows = ieltsReadingQuizGroupRepository
                    .findQuizRowsByReadingSourceId(readingId);
            quizResponse = buildReadingQuizResponse(source, rows);
            redisTemplate.opsForValue().set(cacheKey, RedisUtil.serialize(quizResponse.withoutCompletedQuestionIds()));
        }

        return withCompletedQuestionIds(quizResponse, userId);
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

    public List<String> getIeltsWritingTopics(Integer taskType) {
        return ieltsWritingExerciseRepository.findDistinctTopicsByTaskType(taskType);
    }

    public List<IeltsWritingProblemSummaryResponse> getIeltsWritingProblemsByTopic(String topicName, String userId) {
        return ieltsWritingExerciseRepository.findProblemSummariesByTopic(topicName, userId).stream()
                .map(this::toWritingProblemSummaryResponse)
                .toList();
    }


    public String reviewIeltsWriting(String exerciseId, String userId, String userAnswer) {
        String review = writingReview.generateReview(exerciseId, userId, userAnswer);

        UserVocabAttempt attempt = new UserVocabAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setAttemptId(exerciseId);
        attempt.setUserId(userId);
        attempt.setExerciseType(ExerciseType.IELTS_WRITING_REVIEW);
        attempt.setUserAnswer(userAnswer);
        attempt.setReview(review);
        attempt.setCorrect(true);
        attempt.setReplayCount(0);
        userVocabAttemptRepository.save(attempt);

        return review;
    }

    public List<UserVocabAttempt> getIeltsWritingAttemptHistory(String userId, String exerciseId) {
        return userVocabAttemptRepository.findByUserIdAndAttemptIdAndExerciseTypeOrderByCreatedAtDescIdDesc(
                userId,
                exerciseId,
                ExerciseType.IELTS_WRITING_REVIEW
        );
    }
    public IeltsWritingExercise getIeltsWritingProblem(String problemId) {
        return ieltsWritingExerciseRepository.findById(problemId)
                .orElseThrow(() -> new AppException(ErrorCode.IELTS_WRITING_EXERCISE_NOT_FOUND));
    }

    public List<String> getIeltsWritingReferenceBands(String problemId) {
        return ieltsWritingReferenceRepository.findDistinctBandsByProblemId(problemId);
    }

    public List<IeltsWritingReference> getIeltsWritingReferences(String problemId, String band) {
        return ieltsWritingReferenceRepository.findByProblemIdAndBand(problemId, band);
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

    private IeltsReadingQuizResponse buildReadingQuizResponse(
            IeltsReadingSource source,
            List<IeltsReadingQuizRowProjection> rows
    ) {
        Map<String, IeltsReadingQuizResponse.QuestionGroup> groupsById = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> paragraphIdsByGroup = new LinkedHashMap<>();

        for (IeltsReadingQuizRowProjection row : rows) {
            groupsById.computeIfAbsent(row.getGroupId(), groupId -> IeltsReadingQuizResponse.QuestionGroup.builder()
                    .groupId(groupId)
                    .questionType(row.getQuestionType())
                    .instruction(blankToEmpty(row.getInstruction()))
                    .questionNumberStart(row.getQuestionNumberStart())
                    .questionNumberEnd(row.getQuestionNumberEnd())
                    .context("")
                    .allowOptionReuse(false)
                    .wordLimit(blankToEmpty(row.getWordLimit()))
                    .sourceParagraphIds(parseLooseList(row.getGroupSourceParagraphId()))
                    .sharedOptions(parseLooseList(row.getSharedOptions()))
                    .questions(new ArrayList<>())
                    .build());

            IeltsReadingQuizResponse.QuestionGroup group = groupsById.get(row.getGroupId());
            paragraphIdsByGroup.computeIfAbsent(row.getGroupId(), ignored -> new LinkedHashSet<>(group.getSourceParagraphIds()));
            if (isNotBlank(row.getQuestionSourceParagraphId())) {
                paragraphIdsByGroup.get(row.getGroupId()).add(row.getQuestionSourceParagraphId());
            }
            group.getQuestions().add(toReadingQuestion(row));
        }

        List<IeltsReadingQuizResponse.QuestionGroup> groups = groupsById.entrySet().stream()
                .map(entry -> {
                    entry.getValue().setSourceParagraphIds(new ArrayList<>(paragraphIdsByGroup.get(entry.getKey())));
                    return entry.getValue();
                })
                .toList();

        List<String> selectedQuestionTypes = groups.stream()
                .map(IeltsReadingQuizResponse.QuestionGroup::getQuestionType)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        IeltsReadingQuizResponse.Quiz quiz = IeltsReadingQuizResponse.Quiz.builder()
                .id(source.getId())
                .title(source.getTitle())
                .module(ACADEMIC_READING_MODULE)
                .passageAnalysis(IeltsReadingQuizResponse.PassageAnalysis.builder()
                        .paragraphCount(countParagraphs(source.getContent()))
                        .textType(null)
                        .writerViewPresent(selectedQuestionTypes.contains("yes_no_not_given"))
                        .processPresent(false)
                        .multiEntityPresent(false)
                        .selectedQuestionTypes(selectedQuestionTypes)
                        .build())
                .questionGroups(groups)
                .build();

        return IeltsReadingQuizResponse.builder()
                .id(source.getId())
                .quiz(quiz)
                .completedQuestionIds(List.of())
                .build();
    }

    private IeltsReadingQuizResponse.Question toReadingQuestion(IeltsReadingQuizRowProjection row) {
        return IeltsReadingQuizResponse.Question.builder()
                .questionId(row.getQuestionId())
                .number(row.getQuestionNumber())
                .stem(blankToEmpty(row.getStem()))
                .options(parseLooseList(row.getOptions()))
                .answer(parseLooseList(row.getAnswer()))
                .sourceParagraphId(row.getQuestionSourceParagraphId())
                .evidenceQuote(row.getEvidenceQuote())
                .explanation(row.getExplanation())
                .difficulty(null)
                .skill(null)
                .build();
    }

    private IeltsReadingQuizResponse withCompletedQuestionIds(IeltsReadingQuizResponse response, String userId) {
        List<String> questionIds = response.getQuiz().getQuestionGroups().stream()
                .flatMap(group -> group.getQuestions().stream())
                .map(IeltsReadingQuizResponse.Question::getQuestionId)
                .filter(Objects::nonNull)
                .toList();
        List<String> completedQuestionIds = isNotBlank(userId) && !questionIds.isEmpty()
                ? userVocabAttemptRepository.findCompletedAttemptIds(userId, questionIds)
                : List.of();

        return IeltsReadingQuizResponse.builder()
                .id(response.getId())
                .quiz(response.getQuiz())
                .completedQuestionIds(completedQuestionIds)
                .build();
    }

    private List<String> parseLooseList(String value) {
        if (!isNotBlank(value)) {
            return List.of();
        }
        String trimmed = value.trim();
        try {
            if (trimmed.startsWith("[")) {
                return OBJECT_MAPPER.readValue(trimmed, new TypeReference<List<Object>>() {})
                        .stream()
                        .map(String::valueOf)
                        .filter(this::isNotBlank)
                        .toList();
            }
            if (trimmed.startsWith("\"")) {
                return List.of(OBJECT_MAPPER.readValue(trimmed, String.class));
            }
        } catch (Exception ignored) {
            log.debug("Could not parse IELTS list as JSON, falling back to delimiters");
        }
        return Arrays.stream(trimmed.split("\\r?\\n|,"))
                .map(String::trim)
                .filter(this::isNotBlank)
                .toList();
    }

    private int countParagraphs(String content) {
        if (!isNotBlank(content)) {
            return 0;
        }
        String trimmed = content.trim();
        String[] blocks = trimmed.split("(\\r?\\n){2,}");
        return Math.max(blocks.length, 1);
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

    private IeltsWritingProblemSummaryResponse toWritingProblemSummaryResponse(
            IeltsWritingProblemSummaryProjection projection
    ) {
        return IeltsWritingProblemSummaryResponse.builder()
                .id(projection.getId())
                .problem(projection.getProblem())
                .isDone(projection.getDoneCount() != null && projection.getDoneCount() > 0)
                .build();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}