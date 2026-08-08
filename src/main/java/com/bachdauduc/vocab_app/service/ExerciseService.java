package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.request.exercise.UserLessonRequest;
import com.bachdauduc.vocab_app.dto.response.exercise.ListenAndTypeLessonResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.ListenExerciseSummaryResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.ListeningCategoryResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.UserLessonProgressResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.UserLessonResponse;
import com.bachdauduc.vocab_app.dto.response.exercise.VocabReviewQuizResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordExampleResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSoundResponse;
import com.bachdauduc.vocab_app.entity.ListenAndTypeExerciseChallenge;
import com.bachdauduc.vocab_app.entity.ListenAndTypeSubCategory;
import com.bachdauduc.vocab_app.entity.ListenExercise;
import com.bachdauduc.vocab_app.entity.ListeningCategory;
import com.bachdauduc.vocab_app.entity.UserLesson;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.Word;
import com.bachdauduc.vocab_app.entity.WordSense;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.entity.WordSound;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.ListenAndTypeSubCategoryRepository;
import com.bachdauduc.vocab_app.repository.ListenExerciseRepository;
import com.bachdauduc.vocab_app.repository.ListeningCategoryRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserLessonRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordExampleRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.WordSoundRepository;
import com.bachdauduc.vocab_app.repository.projection.WordExampleProjection;
import com.bachdauduc.vocab_app.service.review.BalancedReviewQuizScheduler;
import com.bachdauduc.vocab_app.service.review.ReviewProgressStore;
import com.bachdauduc.vocab_app.service.review.ReviewQuizFactory;
import com.bachdauduc.vocab_app.service.review.ReviewRequestContext;
import com.bachdauduc.vocab_app.service.review.ReviewTargetEligibility;
import com.bachdauduc.vocab_app.service.review.ReviewVocabDataLoader;
import com.bachdauduc.vocab_app.service.review.ReviewVocabSnapshot;
import com.bachdauduc.vocab_app.utils.RedisUtil;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ExerciseService {
    private static final String LESSON_TYPE_LISTEN_AND_TYPE = "LISTEN_AND_TYPE";
    private static final String SOURCE_MOCHI = "MOCHI";
    private static final Pattern SECTION_SUB_CATEGORY_PATTERN = Pattern.compile(
            "^(conversation|short_stories|toefl(?:_listening)?)_section_(\\d+)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NATURAL_SORT_TOKEN_PATTERN = Pattern.compile("\\d+|\\D+");

    UserVocabularyRepository userVocabularyRepository;
    UserLessonRepository userLessonRepository;
    UserVocabAttemptRepository userVocabAttemptRepository;
    UserInfoRepository userInfoRepository;
    ListenExerciseRepository listenExerciseRepository;
    ListeningCategoryRepository listeningCategoryRepository;
    ListenAndTypeExerciseChallengeRepository listenAndTypeExerciseChallengeRepository;
    ListenAndTypeSubCategoryRepository listenAndTypeSubCategoryRepository;
    WordRepository wordRepository;
    WordSenseRepository wordSenseRepository;
    WordSenseLocalizationRepository wordSenseLocalizationRepository;
    WordSoundRepository wordSoundRepository;
    WordExampleRepository wordExampleRepository;
    WordExampleGenerationService wordExampleGenerationService;
    ReviewVocabDataLoader reviewVocabDataLoader;
    BalancedReviewQuizScheduler balancedReviewQuizScheduler;
    ReviewQuizFactory reviewQuizFactory;
    ReviewProgressStore reviewProgressStore;

    public UserLessonResponse addUserLesson(UserLessonRequest request) {
        log.debug("Start service: method=addUserLesson, userId={}, lessonId={}, lessonType={}",
                request.getUserId(), request.getLessonId(), request.getLessonType());
        assertUserExists(request.getUserId());
        String lessonType = normalizeLessonType(request.getLessonType());
        assertLessonExists(request.getLessonId(), lessonType);

        UserLesson userLesson = new UserLesson();
        userLesson.setId(UUID.randomUUID().toString());
        userLesson.setUserId(request.getUserId());
        userLesson.setLessonId(request.getLessonId());
        userLesson.setLessonType(lessonType);

        UserLesson saved = userLessonRepository.save(userLesson);
        log.info("User lesson added: userId={}, lessonId={}, lessonType={}",
                saved.getUserId(), saved.getLessonId(), saved.getLessonType());
        return toUserLessonResponse(saved);
    }

    public UserLessonProgressResponse getUserLessonProgress(String userId, String lessonId, String lessonType) {
        log.debug("Start service: method=getUserLessonProgress, userId={}, lessonId={}, lessonType={}",
                userId, lessonId, lessonType);
        assertUserExists(userId);
        String normalizedLessonType = normalizeLessonType(lessonType);
        assertLessonExists(lessonId, normalizedLessonType);

        List<String> completedChallengeIds = getCompletedListenAndTypeChallengeIds(userId, lessonId);
        log.info("Lesson progress loaded: userId={}, lessonId={}, lessonType={}, completedChallengeCount={}",
                userId, lessonId, normalizedLessonType, completedChallengeIds.size());
        return UserLessonProgressResponse.builder()
                .userId(userId)
                .lessonId(lessonId)
                .lessonType(normalizedLessonType)
                .completedChallengeIds(completedChallengeIds)
                .build();
    }

    public ListenAndTypeLessonResponse getListenAndTypeLesson(String userId, String lessonId) {
        log.debug("Start service: method=getListenAndTypeLesson, userId={}, lessonId={}", userId, lessonId);
        assertUserExists(userId);
        ListenExercise lesson = getRequiredListenExercise(lessonId);
        List<ListenAndTypeExerciseChallenge> challenges =
                listenAndTypeExerciseChallengeRepository.findByListenExerciseIdOrderByPositionAsc(lessonId);
        List<String> completedChallengeIds = getCompletedListenAndTypeChallengeIds(userId, lessonId);
        Set<String> completedChallengeIdSet = new LinkedHashSet<>(completedChallengeIds);
        log.info("Listen-and-type lesson loaded: userId={}, lessonId={}, challengeCount={}, completedChallengeCount={}",
                userId, lessonId, challenges.size(), completedChallengeIds.size());

        return ListenAndTypeLessonResponse.builder()
                .userId(userId)
                .lessonId(lesson.getLessonId())
                .title(lesson.getTitle())
                .categoryName(resolveListeningCategoryName(lesson.getCategoryId()))
                .fullDocument(lesson.getFullDocument())
                .speechToTextLangCode(lesson.getSpeechToTextLangCode())
                .audioUrl(lesson.getAudioUrl())
                .learningResourceType(lesson.getLearningResourceType())
                .completedChallengeIds(completedChallengeIds)
                .challenges(challenges.stream()
                        .map(challenge -> toListenAndTypeChallengeResponse(challenge, completedChallengeIdSet))
                        .toList())
                .build();
    }

    public List<ListeningCategoryResponse> getListenAndTypeCategories() {
        log.debug("Start service: method=getListenAndTypeCategories");
        List<ListeningCategoryResponse> categories = listeningCategoryRepository.findAll().stream()
                .map(this::toListeningCategoryResponse)
                .toList();
        log.info("Listen-and-type categories loaded: count={}", categories.size());
        return categories;
    }

    public List<String> getListenAndTypeSubCategoryNames(String categoryId) {
        log.debug("Start service: method=getListenAndTypeSubCategoryNames, categoryId={}", categoryId);
        if (!listeningCategoryRepository.existsById(categoryId)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        List<String> subCategoryNames = listenAndTypeSubCategoryRepository
                .findByCategoryId(categoryId)
                .stream()
                .map(ListenAndTypeSubCategory::getSubCategoryName)
                .map(this::displaySubCategoryName)
                .distinct()
                .sorted(this::compareNaturalStrings)
                .toList();
        log.info("Listen-and-type sub categories loaded: categoryId={}, subCategoryCount={}",
                categoryId, subCategoryNames.size());
        return subCategoryNames;
    }

    public List<ListenExerciseSummaryResponse> getListenAndTypeLessonsBySubCategory(String subCategoryName, String userId) {
        log.debug("Start service: method=getListenAndTypeLessonsBySubCategory, subCategoryName={}, userId={}",
                subCategoryName, userId);
        assertUserExists(userId);
        if (!StringUtils.hasText(subCategoryName)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_FOUND);
        }

        List<String> resolvedSubCategories = resolveRawSubCategoryNames(subCategoryName.trim());
        List<ListenExercise> listenExercises = resolvedSubCategories.size() == 1
                ? listenExerciseRepository.findBySubCategory(resolvedSubCategories.getFirst())
                : listenExerciseRepository.findBySubCategoryIn(resolvedSubCategories);

        List<ListenExerciseSummaryResponse> lessons = listenExercises
                .stream()
                .sorted(this::compareListenExercisesNaturally)
                .map(lesson -> toListenExerciseSummaryResponse(lesson, userId))
                .toList();
        log.info("Listen-and-type lessons loaded: subCategoryName={}, userId={}, resolvedSubCategories={}, lessonCount={}",
                subCategoryName, userId, resolvedSubCategories, lessons.size());
        return lessons;
    }

    public List<VocabReviewQuizResponse> getReviewVocabs(String userId, int totalReviewVocab, String langCode) {
        log.debug("Start service: method=getReviewVocabs, userId={}, totalReviewVocab={}, langCode={}",
                userId, totalReviewVocab, langCode);
        assertUserExists(userId);

        List<UserVocabulary> selectedVocabs = selectReviewVocabs(userId, totalReviewVocab);
        wordExampleGenerationService.ensureExamples(selectedVocabs);
        List<VocabReviewQuizResponse> quizzes =
                generateReviewQuizzes(userId, selectedVocabs, langCode);

        log.info("Review vocab quizzes generated: userId={}, requested={}, resultCount={}",
                userId, totalReviewVocab, quizzes.size());
        return quizzes;
    }

    public List<VocabReviewQuizResponse> getReviewVocab(String userId, String userVocabId, String langCode) {
        log.debug("Start service: method=getReviewVocab, userId={}, userVocabId={}, langCode={}",
                userId, userVocabId, langCode);
        assertUserExists(userId);
        UserVocabulary userVocabulary = getRequiredUserVocabulary(userVocabId);
        if (!userId.equals(userVocabulary.getUserId())) {
            throw new AppException(ErrorCode.USER_VOCABULARY_NOT_FOUND);
        }

        wordExampleGenerationService.ensureExamples(List.of(userVocabulary));
        List<UserVocabulary> contextVocabularies = new ArrayList<>(userVocabularyRepository
                .findDueReviewVocabs(userId, LocalDateTime.now(), PageRequest.of(0, 32)));
        if (contextVocabularies.stream().noneMatch(vocabulary -> userVocabId.equals(vocabulary.getId()))) {
            contextVocabularies.add(userVocabulary);
        }
        Optional<VocabReviewQuizResponse> quiz = generateReviewQuizzes(
                userId,
                contextVocabularies,
                langCode,
                Set.of(userVocabId)
        ).stream().findFirst();
        log.info("Single review vocab quiz generated: userId={}, userVocabId={}, resultCount={}",
                userId, userVocabId, quiz.isPresent() ? 1 : 0);
        return quiz.map(List::of).orElseGet(List::of);
    }

    private List<VocabReviewQuizResponse> generateReviewQuizzes(
            String userId,
            List<UserVocabulary> contextVocabularies,
            String langCode
    ) {
        Set<String> targetIds = contextVocabularies.stream()
                .map(UserVocabulary::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        return generateReviewQuizzes(userId, contextVocabularies, langCode, targetIds);
    }

    private List<VocabReviewQuizResponse> generateReviewQuizzes(
            String userId,
            List<UserVocabulary> contextVocabularies,
            String langCode,
            Set<String> targetIds
    ) {
        if (contextVocabularies.isEmpty() || targetIds.isEmpty()) {
            return List.of();
        }
        Map<String, ReviewVocabSnapshot> snapshots =
                reviewVocabDataLoader.load(contextVocabularies, langCode);
        ReviewRequestContext context = ReviewRequestContext.create(contextVocabularies, snapshots);
        List<ReviewTargetEligibility> targets = contextVocabularies.stream()
                .filter(vocabulary -> targetIds.contains(vocabulary.getId()))
                .filter(vocabulary -> snapshots.containsKey(vocabulary.getId()))
                .map(vocabulary -> new ReviewTargetEligibility(
                        vocabulary.getId(),
                        reviewQuizFactory.eligibleTypes(
                                vocabulary,
                                snapshots.get(vocabulary.getId()),
                                context
                        )
                ))
                .toList();
        Map<String, ExerciseType> assignments = balancedReviewQuizScheduler.schedule(targets);
        Map<String, Set<ExerciseType>> eligibleById = targets.stream()
                .collect(Collectors.toMap(
                        ReviewTargetEligibility::userVocabId,
                        ReviewTargetEligibility::eligibleTypes
                ));

        List<VocabReviewQuizResponse> quizzes = new ArrayList<>();
        Map<ExerciseType, Integer> emittedCounts = new java.util.EnumMap<>(ExerciseType.class);
        for (UserVocabulary vocabulary : contextVocabularies) {
            if (!targetIds.contains(vocabulary.getId())) {
                continue;
            }
            ReviewVocabSnapshot snapshot = snapshots.get(vocabulary.getId());
            ExerciseType assigned = assignments.get(vocabulary.getId());
            if (snapshot == null || assigned == null) {
                continue;
            }
            List<ExerciseType> candidates = orderedCandidates(
                    assigned,
                    eligibleById.getOrDefault(vocabulary.getId(), Set.of()),
                    emittedCounts
            );
            Optional<ExerciseType> reservedType =
                    reviewProgressStore.reserveFirstAvailable(userId, snapshot.wordId(), candidates);
            if (reservedType.isEmpty()) {
                continue;
            }
            createReservedQuiz(
                    userId, vocabulary, snapshot, context, reservedType.get()
            ).ifPresent(quiz -> {
                quizzes.add(quiz);
                emittedCounts.merge(reservedType.get(), 1, Integer::sum);
            });
        }
        return List.copyOf(quizzes);
    }

    private List<ExerciseType> orderedCandidates(
            ExerciseType assigned,
            Set<ExerciseType> eligibleTypes,
            Map<ExerciseType, Integer> emittedCounts
    ) {
        List<ExerciseType> candidates = new ArrayList<>();
        candidates.add(assigned);
        eligibleTypes.stream()
                .filter(type -> !type.equals(assigned))
                .sorted(Comparator
                        .comparingInt((ExerciseType type) -> emittedCounts.getOrDefault(type, 0))
                        .thenComparing(Enum::name))
                .forEach(candidates::add);
        return candidates;
    }

    private Optional<VocabReviewQuizResponse> createReservedQuiz(
            String userId,
            UserVocabulary vocabulary,
            ReviewVocabSnapshot snapshot,
            ReviewRequestContext context,
            ExerciseType type
    ) {
        try {
            return Optional.of(reviewQuizFactory.create(vocabulary, snapshot, context, type));
        } catch (AppException exception) {
            reviewProgressStore.release(userId, snapshot.wordId(), type);
            log.warn(
                    "Review quiz creation skipped: userVocabId={}, wordId={}, type={}, errorCode={}",
                    vocabulary.getId(),
                    snapshot.wordId(),
                    type,
                    exception.getErrorCode()
            );
            return Optional.empty();
        }
    }

    public VocabReviewQuizResponse generateWordToMeaningQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateWordToMeaningQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        List<VocabContext> contexts = buildVocabContexts(reviewUserVocabIds, langCode);
        VocabContext current = getRequiredContext(contexts, userVocabId);

        List<String> answers = distinctShuffledValues(contexts.stream()
                .filter(context -> !context.userVocabulary().getId().equals(userVocabId))
                .map(VocabContext::meaning)
                .filter(StringUtils::hasText)
                .toList());
        answers = takeWithCorrectAnswer(answers, current.meaning(), 4);

        log.info("Word-to-meaning quiz generated: userVocabId={}, wordId={}, answerCount={}",
                userVocabId, current.word().getId(), answers.size());
        return baseResponse(current, ExerciseType.VOCAB_WORD_TO_MEANING, langCode)
                .correctAnswer(current.meaning())
                .listAnswers(answers)
                .build();
    }

    public VocabReviewQuizResponse generateFillMissingWordPartQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateFillMissingWordPartQuiz, userVocabId={}, reviewSize={}",
                userVocabId, reviewUserVocabIds.size());
        VocabContext current = getRequiredContext(buildVocabContexts(reviewUserVocabIds, langCode), userVocabId);
        String word = current.word().getWord();
        if (normalizedLetterCount(word) <= 2) {
            throw new AppException(ErrorCode.INVALID_EXERCISE_TYPE);
        }

        Map<Integer, String> missingCharacters = randomMissingCharacters(word, current.userVocabulary().getLevel());
        String maskedWord = maskMissingCharacters(word, missingCharacters);
        log.info("Fill-missing-word-part quiz generated: userVocabId={}, wordId={}, missingCount={}, wordLength={}",
                userVocabId, current.word().getId(), missingCharacters.size(), word.length());
        return baseResponse(current, ExerciseType.VOCAB_FILL_MISSING_WORD_PART, langCode)
                .correctAnswer(word)
                .metadata(missingCharacters)
                .maskedWord(maskedWord)
                .build();
    }

    public VocabReviewQuizResponse generateListenAndTypeWordQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateListenAndTypeWordQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        VocabContext current = getRequiredContext(buildVocabContexts(reviewUserVocabIds, langCode), userVocabId);
        String audioUrl = resolveAudioUrl(current.word(), langCode);

        log.info("Listen-and-type-word quiz generated: userVocabId={}, wordId={}, hasAudio={}",
                userVocabId, current.word().getId(), StringUtils.hasText(audioUrl));
        return baseResponse(current, ExerciseType.VOCAB_LISTEN_AND_TYPE_WORD, langCode)
                .correctAnswer(current.word().getWord())
                .audioUrl(audioUrl)
                .build();
    }

    public VocabReviewQuizResponse generateChooseWordInSentenceBlankQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateChooseWordInSentenceBlankQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        List<VocabContext> contexts = buildVocabContexts(reviewUserVocabIds, langCode);
        VocabContext current = getRequiredContext(contexts, userVocabId);
        SentenceBlank sentenceBlank = randomSentenceBlank(current, langCode);

        List<String> answers = distinctShuffledValues(contexts.stream()
                .filter(context -> !context.userVocabulary().getId().equals(userVocabId))
                .map(context -> context.word().getWord())
                .filter(StringUtils::hasText)
                .toList());
        answers = takeWithCorrectAnswer(answers, current.word().getWord(), 4);

        log.info("Choose-word-in-sentence quiz generated: userVocabId={}, wordId={}, answerCount={}, missIndex={}",
                userVocabId, current.word().getId(), answers.size(), sentenceBlank.missIndex());
        return baseResponse(current, ExerciseType.VOCAB_CHOOSE_WORD_IN_SENTENCE_BLANK, langCode)
                .correctAnswer(current.word().getWord())
                .missIndex(sentenceBlank.missIndex())
                .sentence(sentenceBlank.sentence())
                .trans(sentenceBlank.trans())
                .listAnswers(answers)
                .build();
    }

    public VocabReviewQuizResponse generateFillWordInSentenceBlankQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateFillWordInSentenceBlankQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        VocabContext current = getRequiredContext(buildVocabContexts(reviewUserVocabIds, langCode), userVocabId);
        String word = current.word().getWord();
        if (normalizedLetterCount(word) <= 2) {
            throw new AppException(ErrorCode.INVALID_EXERCISE_TYPE);
        }
        Map<Integer, String> missingCharacters = randomMissingCharacters(word, current.userVocabulary().getLevel());
        String maskedWord = maskMissingCharacters(word, missingCharacters);
        SentenceBlank sentenceBlank = randomHintedSentenceBlank(current, langCode, maskedWord);

        log.info("Fill-word-in-sentence quiz generated: userVocabId={}, wordId={}, missIndex={}, missingCount={}",
                userVocabId, current.word().getId(), sentenceBlank.missIndex(), missingCharacters.size());
        return baseResponse(current, ExerciseType.VOCAB_FILL_WORD_IN_SENTENCE_BLANK, langCode)
                .correctAnswer(word)
                .metadata(missingCharacters)
                .maskedWord(maskedWord)
                .missIndex(sentenceBlank.missIndex())
                .sentence(sentenceBlank.sentence())
                .trans(sentenceBlank.trans())
                .build();
    }

    public VocabReviewQuizResponse generateMeaningToSoundQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateMeaningToSoundQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        List<VocabContext> contexts = buildVocabContexts(reviewUserVocabIds, langCode);
        VocabContext current = getRequiredContext(contexts, userVocabId);
        IndexedOptions soundOptions = soundOptions(current, contexts);
        SentenceText sentenceText = reviewSentenceText(current, langCode);

        log.info("Meaning-to-sound quiz generated: userVocabId={}, wordId={}, optionCount={}",
                userVocabId, current.word().getId(), soundOptions.metadata().size());
        return baseResponse(current, ExerciseType.VOCAB_MEANING_TO_SOUND, langCode)
                .correctAnswer(soundOptions.correctAnswer())
                .metadata(soundOptions.metadata())
                .sentence(sentenceText.sentence())
                .trans(sentenceText.trans())
                .build();
    }

    public VocabReviewQuizResponse generateSentenceToMeaningQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateSentenceToMeaningQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        List<VocabContext> contexts = buildVocabContexts(reviewUserVocabIds, langCode);
        VocabContext current = getRequiredContext(contexts, userVocabId);
        SentenceBlank sentence = randomUnderlinedSentence(current, langCode);
        IndexedOptions meaningOptions = meaningOptions(current, contexts);

        log.info("Sentence-to-meaning quiz generated: userVocabId={}, wordId={}, optionCount={}, missIndex={}",
                userVocabId, current.word().getId(), meaningOptions.metadata().size(), sentence.missIndex());
        return baseResponse(current, ExerciseType.VOCAB_SENTENCE_TO_MEANING, langCode)
                .correctAnswer(meaningOptions.correctAnswer())
                .metadata(meaningOptions.metadata())
                .missIndex(sentence.missIndex())
                .sentence(sentence.sentence())
                .trans(sentence.trans())
                .build();
    }

    public VocabReviewQuizResponse generateSentenceBlankToSoundQuiz(
            String userVocabId,
            List<String> reviewUserVocabIds,
            String langCode
    ) {
        log.debug("Start service: method=generateSentenceBlankToSoundQuiz, userVocabId={}, reviewSize={}, langCode={}",
                userVocabId, reviewUserVocabIds.size(), langCode);
        List<VocabContext> contexts = buildVocabContexts(reviewUserVocabIds, langCode);
        VocabContext current = getRequiredContext(contexts, userVocabId);
        SentenceBlank sentenceBlank = randomSentenceBlank(current, langCode);
        IndexedOptions soundOptions = soundOptions(current, contexts);

        log.info("Sentence-blank-to-sound quiz generated: userVocabId={}, wordId={}, optionCount={}, missIndex={}",
                userVocabId, current.word().getId(), soundOptions.metadata().size(), sentenceBlank.missIndex());
        return baseResponse(current, ExerciseType.VOCAB_SENTENCE_BLANK_TO_SOUND, langCode)
                .correctAnswer(soundOptions.correctAnswer())
                .metadata(soundOptions.metadata())
                .missIndex(sentenceBlank.missIndex())
                .sentence(sentenceBlank.sentence())
                .trans(sentenceBlank.trans())
                .build();
    }

    private List<UserVocabulary> selectReviewVocabs(String userId, int totalReviewVocab) {
        Map<Integer, Integer> quotas = reviewQuotas(totalReviewVocab);
        List<UserVocabulary> dueVocabs = userVocabularyRepository.findDueReviewVocabs(userId, LocalDateTime.now());
        log.debug("Due review vocabs loaded: userId={}, dueCount={}, requested={}, quotas={}",
                userId, dueVocabs.size(), totalReviewVocab, quotas);
        if (dueVocabs.isEmpty()) {
            return List.of();
        }

        Map<Integer, List<UserVocabulary>> byLevel = dueVocabs.stream()
                .collect(Collectors.groupingBy(UserVocabulary::getLevel, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
        byLevel.values().forEach(Collections::shuffle);

        if (byLevel.size() == 1 && byLevel.containsKey(1)) {
            List<UserVocabulary> selectedLevelOne = byLevel.get(1).stream()
                    .limit(totalReviewVocab)
                    .toList();
            log.info("Review vocab selected from level 1 only: userId={}, selectedCount={}",
                    userId, selectedLevelOne.size());
            return selectedLevelOne;
        }

        List<UserVocabulary> selected = new ArrayList<>();
        Set<String> selectedIds = new LinkedHashSet<>();
        takeByQuota(byLevel, quotas, selected, selectedIds, totalReviewVocab);

        boolean changed;
        do {
            int before = selected.size();
            takeByQuota(byLevel, quotas, selected, selectedIds, totalReviewVocab);
            changed = selected.size() > before;
        } while (selected.size() < totalReviewVocab && changed);

        log.info("Review vocab selected: userId={}, requested={}, selectedCount={}, selectedByLevel={}",
                userId,
                totalReviewVocab,
                selected.size(),
                selected.stream().collect(Collectors.groupingBy(UserVocabulary::getLevel, Collectors.counting())));
        return selected;
    }

    private void takeByQuota(
            Map<Integer, List<UserVocabulary>> byLevel,
            Map<Integer, Integer> quotas,
            List<UserVocabulary> selected,
            Set<String> selectedIds,
            int totalReviewVocab
    ) {
        for (int level = 1; level <= 6 && selected.size() < totalReviewVocab; level++) {
            List<UserVocabulary> levelVocabs = byLevel.getOrDefault(level, List.of());
            int quota = quotas.getOrDefault(level, 0);
            int taken = 0;
            for (UserVocabulary userVocabulary : levelVocabs) {
                if (taken >= quota || selected.size() >= totalReviewVocab) {
                    break;
                }
                if (selectedIds.add(userVocabulary.getId())) {
                    selected.add(userVocabulary);
                    taken++;
                }
            }
        }
    }

    private Map<Integer, Integer> reviewQuotas(int totalReviewVocab) {
        return switch (totalReviewVocab) {
            case 30 -> Map.of(1, 9, 2, 8, 3, 5, 4, 3, 5, 3, 6, 2);
            case 60 -> Map.of(1, 15, 2, 15, 3, 10, 4, 10, 5, 5, 6, 5);
            case 90 -> Map.of(1, 25, 2, 25, 3, 14, 4, 13, 5, 10, 6, 8);
            default -> throw new AppException(ErrorCode.INVALID_REVIEW_VOCAB_TOTAL);
        };
    }

    private List<VocabContext> buildVocabContexts(List<String> userVocabIds, String langCode) {
        List<String> requestedOrder = userVocabIds.stream()
                .distinct()
                .toList();
        List<UserVocabulary> vocabularies = userVocabularyRepository.findAllById(userVocabIds);
        Map<String, UserVocabulary> vocabById = vocabularies.stream()
                .collect(Collectors.toMap(UserVocabulary::getId, Function.identity()));

        List<VocabContext> contexts = requestedOrder.stream()
                .map(vocabById::get)
                .filter(vocab -> vocab != null)
                .map(vocab -> buildVocabContext(vocab, langCode))
                .toList();
        log.debug("Vocab contexts built: requestedCount={}, foundCount={}, langCode={}",
                userVocabIds.size(), contexts.size(), langCode);
        return contexts;
    }

    private List<String> reviewContextIdsForSingleVocab(String userId, String userVocabId) {
        LinkedHashSet<String> reviewIds = userVocabularyRepository
                .findDueReviewVocabs(userId, LocalDateTime.now())
                .stream()
                .map(UserVocabulary::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        reviewIds.add(userVocabId);
        return List.copyOf(reviewIds);
    }

    private VocabContext buildVocabContext(UserVocabulary userVocabulary, String langCode) {
        Word word = wordRepository.findById(userVocabulary.getWordId())
                .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
        String meaning = resolveMeaning(userVocabulary, langCode);
        return new VocabContext(userVocabulary, word, meaning);
    }

    private String resolveMeaning(UserVocabulary userVocabulary, String langCode) {
        if (StringUtils.hasText(userVocabulary.getSenseLocalizedId())) {
            WordSenseLocalization localization = wordSenseLocalizationRepository.findById(userVocabulary.getSenseLocalizedId())
                    .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
            String meaning = firstText(localization.getShortMeaning(), localization.getFullLocalizedDefinition());
            log.debug("Meaning resolved from localized sense: userVocabId={}, localizationId={}, hasMeaning={}",
                    userVocabulary.getId(), userVocabulary.getSenseLocalizedId(), StringUtils.hasText(meaning));
            return meaning;
        }

        if (StringUtils.hasText(userVocabulary.getSenseId()) && StringUtils.hasText(langCode)) {
            Optional<WordSenseLocalization> localization =
                    wordSenseLocalizationRepository.findFirstBySenseIdAndLangCode(userVocabulary.getSenseId(), langCode);
            if (localization.isPresent()) {
                String meaning = firstText(localization.get().getShortMeaning(), localization.get().getFullLocalizedDefinition());
                log.debug("Meaning resolved from sense localization: userVocabId={}, senseId={}, langCode={}, hasMeaning={}",
                        userVocabulary.getId(), userVocabulary.getSenseId(), langCode, StringUtils.hasText(meaning));
                return meaning;
            }
        }

        WordSense sense = wordSenseRepository.findById(userVocabulary.getSenseId())
                .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
        log.debug("Meaning resolved from word sense definition: userVocabId={}, senseId={}, hasDefinition={}",
                userVocabulary.getId(), userVocabulary.getSenseId(), StringUtils.hasText(sense.getDefinition()));
        return sense.getDefinition();
    }

    private VocabContext getRequiredContext(List<VocabContext> contexts, String userVocabId) {
        return contexts.stream()
                .filter(context -> context.userVocabulary().getId().equals(userVocabId))
                .findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.USER_VOCABULARY_NOT_FOUND));
    }

    private List<String> distinctShuffledValues(List<String> values) {
        List<String> shuffled = values.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(shuffled);
        return shuffled;
    }

    private List<String> takeWithCorrectAnswer(List<String> distractors, String correctAnswer, int totalAnswer) {
        List<String> answers = distractors.stream()
                .filter(answer -> !answer.equals(correctAnswer))
                .limit(Math.max(totalAnswer - 1, 0))
                .collect(Collectors.toCollection(ArrayList::new));
        answers.add(correctAnswer);
        Collections.shuffle(answers);
        return answers;
    }

    private Map<Integer, String> randomMissingCharacters(String word, Integer level) {
        List<Integer> candidateIndexes = new ArrayList<>();
        for (int i = 1; i < word.length(); i++) {
            if (!Character.isWhitespace(word.charAt(i))) {
                candidateIndexes.add(i);
            }
        }
        Collections.shuffle(candidateIndexes);

        int missingCount = resolveMissingCount(candidateIndexes.size() + 1, level);
        Map<Integer, String> metadata = new LinkedHashMap<>();
        candidateIndexes.stream()
                .limit(Math.min(missingCount, candidateIndexes.size()))
                .sorted(Comparator.naturalOrder())
                .forEach(index -> metadata.put(index, String.valueOf(word.charAt(index))));
        return metadata;
    }

    private String maskMissingCharacters(String word, Map<Integer, String> missingCharacters) {
        StringBuilder maskedWord = new StringBuilder(word);
        missingCharacters.keySet().forEach(index -> {
            if (index >= 0 && index < maskedWord.length()) {
                maskedWord.setCharAt(index, '_');
            }
        });
        return maskedWord.toString();
    }

    private int resolveMissingCount(int length, Integer level) {
        int normalizedLevel = level == null ? 1 : level;
        boolean highLevel = normalizedLevel >= 4;
        if (length == 3) {
            return randomBetween(1, 2);
        }
        if (length == 4) {
            return highLevel ? 3 : randomBetween(1, 2);
        }
        return highLevel ? randomBetween(3, Math.max(3, length - 1)) : randomBetween(1, 2);
    }

    private int randomBetween(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private IndexedOptions meaningOptions(VocabContext current, List<VocabContext> contexts) {
        List<String> distractors = distinctShuffledValues(contexts.stream()
                .filter(context -> !context.userVocabulary().getId().equals(current.userVocabulary().getId()))
                .map(VocabContext::meaning)
                .filter(StringUtils::hasText)
                .toList());
        return indexedOptions(distractors, current.meaning(), ErrorCode.INVALID_EXERCISE_TYPE);
    }

    private IndexedOptions soundOptions(VocabContext current, List<VocabContext> contexts) {
        String correctSound = playableSoundUrl(current.word())
                .orElseThrow(() -> new AppException(ErrorCode.WORD_SOUND_NOT_FOUND));

        List<String> distractors = contexts.stream()
                .filter(context -> !context.userVocabulary().getId().equals(current.userVocabulary().getId()))
                .map(context -> playableSoundUrl(context.word()))
                .flatMap(Optional::stream)
                .filter(sound -> !sound.equals(correctSound))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(distractors);

        return indexedOptions(distractors, correctSound, ErrorCode.WORD_SOUND_NOT_FOUND);
    }

    private IndexedOptions indexedOptions(List<String> distractors, String correctValue, ErrorCode errorCode) {
        if (!StringUtils.hasText(correctValue)) {
            throw new AppException(errorCode);
        }

        List<String> options = distractors.stream()
                .filter(StringUtils::hasText)
                .filter(value -> !value.equals(correctValue))
                .distinct()
                .limit(3)
                .collect(Collectors.toCollection(ArrayList::new));
        options.add(correctValue);
        if (options.size() < 4) {
            throw new AppException(errorCode);
        }
        Collections.shuffle(options);

        Map<Integer, String> metadata = new LinkedHashMap<>();
        String correctAnswer = null;
        for (int i = 0; i < options.size(); i++) {
            int key = i + 1;
            String value = options.get(i);
            metadata.put(key, value);
            if (value.equals(correctValue)) {
                correctAnswer = String.valueOf(key);
            }
        }
        return new IndexedOptions(metadata, correctAnswer);
    }

    private String resolveAudioUrl(Word word, String langCode) {
        return playableSoundUrl(word)
                .orElseThrow(() -> new AppException(ErrorCode.WORD_SOUND_NOT_FOUND));
    }

    private Optional<String> playableSoundUrl(Word word) {
        return preferredPlayableSound(word)
                .map(sound -> firstText(sound.getMp3Url(), sound.getOggUrl()))
                .filter(StringUtils::hasText);
    }

    private Optional<WordSound> preferredSound(Word word) {
        List<WordSound> mochiSounds = wordSoundRepository.findByWordIdAndSoundSource(word.getId(), SOURCE_MOCHI);
        if (!mochiSounds.isEmpty()) {
            log.debug("Preferred MOCHI sound selected for review: wordId={}", word.getId());
            return Optional.of(mochiSounds.getFirst());
        }

        List<WordSound> sounds = wordSoundRepository.findByWordId(word.getId());
        if (sounds.isEmpty()) {
            log.debug("No sound found for review: wordId={}", word.getId());
            return Optional.empty();
        }
        return Optional.of(sounds.getFirst());
    }

    private Optional<WordSound> preferredPlayableSound(Word word) {
        List<WordSound> mochiSounds = wordSoundRepository.findByWordIdAndSoundSource(word.getId(), SOURCE_MOCHI);
        Optional<WordSound> mochiPlayableSound = firstPlayableSound(mochiSounds);
        if (mochiPlayableSound.isPresent()) {
            return mochiPlayableSound;
        }
        return firstPlayableSound(wordSoundRepository.findByWordId(word.getId()));
    }

    private Optional<WordSound> firstPlayableSound(List<WordSound> sounds) {
        return sounds.stream()
                .filter(sound -> StringUtils.hasText(sound.getMp3Url()) || StringUtils.hasText(sound.getOggUrl()))
                .findFirst();
    }

    private Optional<WordSoundResponse> resolveReviewSound(Word word) {
        return preferredSound(word).map(this::toWordSoundResponse);
    }

    private Optional<WordSenseResponse> resolveReviewSense(VocabContext context, String langCode) {
        UserVocabulary userVocabulary = context.userVocabulary();
        Word word = context.word();

        if (StringUtils.hasText(userVocabulary.getSenseLocalizedId())) {
            return wordSenseLocalizationRepository.findById(userVocabulary.getSenseLocalizedId())
                    .map(localization -> toLocalizedWordSenseResponse(word, localization));
        }

        if (!StringUtils.hasText(userVocabulary.getSenseId())) {
            log.debug("Review sense skipped: userVocabId={}, wordId={}, reason=no_sense_id",
                    userVocabulary.getId(), word.getId());
            return Optional.empty();
        }

        WordSense sense = wordSenseRepository.findById(userVocabulary.getSenseId())
                .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
        Optional<WordSenseLocalization> localization = StringUtils.hasText(langCode)
                ? wordSenseLocalizationRepository.findFirstBySenseIdAndLangCode(sense.getId(), langCode)
                : Optional.empty();
        return Optional.of(toWordSenseResponse(word, sense, localization.orElse(null)));
    }

    private Optional<WordExampleResponse> resolveReviewExample(VocabContext context, String langCode) {
        return firstExampleWithTranslationPreferred(loadReviewExamples(context, langCode))
                .map(this::toWordExampleResponse);
    }

    private SentenceText reviewSentenceText(VocabContext context, String langCode) {
        return resolveReviewExample(context, langCode)
                .map(example -> new SentenceText(example.getSentence(), example.getTrans()))
                .orElseGet(() -> new SentenceText(null, null));
    }

    private Optional<WordExampleProjection> firstExampleWithTranslationPreferred(List<WordExampleProjection> examples) {
        Optional<WordExampleProjection> translatedExample = examples.stream()
                .filter(example -> StringUtils.hasText(example.getTrans()))
                .findFirst();
        return translatedExample.isPresent() ? translatedExample : examples.stream().findFirst();
    }

    private SentenceBlank randomSentenceBlank(VocabContext context, String langCode) {
        List<WordExampleProjection> filteredExamples = loadReviewExamples(context, langCode);

        if (filteredExamples.isEmpty()) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        WordExampleProjection example = filteredExamples.get(ThreadLocalRandom.current().nextInt(filteredExamples.size()));
        return blankWordInSentence(example.getSentence(), example.getTrans(), context.word().getWord());
    }

    private SentenceBlank randomHintedSentenceBlank(VocabContext context, String langCode, String maskedWord) {
        List<WordExampleProjection> filteredExamples = loadReviewExamples(context, langCode);

        if (filteredExamples.isEmpty()) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        WordExampleProjection example = filteredExamples.get(ThreadLocalRandom.current().nextInt(filteredExamples.size()));
        return replaceWordInSentence(example.getSentence(), example.getTrans(), context.word().getWord(), maskedWord);
    }

    private SentenceBlank randomUnderlinedSentence(VocabContext context, String langCode) {
        List<WordExampleProjection> filteredExamples = loadReviewExamples(context, langCode);

        if (filteredExamples.isEmpty()) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        WordExampleProjection example = filteredExamples.get(ThreadLocalRandom.current().nextInt(filteredExamples.size()));
        return underlineWordInSentence(example.getSentence(), example.getTrans(), context.word().getWord());
    }

    private List<WordExampleProjection> loadReviewExamples(VocabContext context, String langCode) {
        UserVocabulary userVocabulary = context.userVocabulary();
        Word word = context.word();
        List<WordExampleProjection> examples;

        if (StringUtils.hasText(userVocabulary.getSenseLocalizedId())) {
            examples = wordExampleRepository.findMochiWordExamples(word.getId(), langCode);
            List<WordExampleProjection> matchedExamples = examples.stream()
                    .filter(example -> userVocabulary.getSenseLocalizedId().equals(example.getWordSenseLocalizationId()))
                    .toList();
            log.debug("Review examples loaded by localized sense: userVocabId={}, wordId={}, localizationId={}, totalExamples={}, matchedExamples={}",
                    userVocabulary.getId(), word.getId(), userVocabulary.getSenseLocalizedId(), examples.size(), matchedExamples.size());
            return matchedExamples;
        }

        if (StringUtils.hasText(userVocabulary.getSenseId())) {
            examples = wordExampleRepository.findWordExamplesWithTrans(word.getId(), langCode);
            List<WordExampleProjection> matchedExamples = examples.stream()
                    .filter(example -> userVocabulary.getSenseId().equals(example.getSenseId()))
                    .toList();
            log.debug("Review examples loaded by sense: userVocabId={}, wordId={}, senseId={}, totalExamples={}, matchedExamples={}",
                    userVocabulary.getId(), word.getId(), userVocabulary.getSenseId(), examples.size(), matchedExamples.size());
            return matchedExamples;
        }

        log.debug("Review examples skipped: userVocabId={}, wordId={}, reason=no_sense",
                userVocabulary.getId(), word.getId());
        return List.of();
    }

    private SentenceBlank blankWordInSentence(String sentence, String trans, String word) {
        if (!StringUtils.hasText(sentence)) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(sentence);
        if (!matcher.find()) {
            return new SentenceBlank(sentence, trans, -1);
        }

        int missIndex = matcher.start();
        String blankSentence = sentence.substring(0, matcher.start())
                + "_".repeat(matcher.end() - matcher.start())
                + sentence.substring(matcher.end());
        return new SentenceBlank(blankSentence, trans, missIndex);
    }

    private SentenceBlank replaceWordInSentence(String sentence, String trans, String word, String replacement) {
        if (!StringUtils.hasText(sentence)) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(sentence);
        if (!matcher.find()) {
            return new SentenceBlank(sentence, trans, -1);
        }

        int missIndex = matcher.start();
        String replacedSentence = sentence.substring(0, matcher.start())
                + replacement
                + sentence.substring(matcher.end());
        return new SentenceBlank(replacedSentence, trans, missIndex);
    }

    private SentenceBlank underlineWordInSentence(String sentence, String trans, String word) {
        if (!StringUtils.hasText(sentence)) {
            throw new AppException(ErrorCode.WORD_EXAMPLE_NOT_FOUND);
        }

        Pattern pattern = Pattern.compile(Pattern.quote(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(sentence);
        if (!matcher.find()) {
            return new SentenceBlank(sentence, trans, -1);
        }

        int missIndex = matcher.start();
        String underlinedSentence = sentence.substring(0, matcher.start())
                + "<u>"
                + sentence.substring(matcher.start(), matcher.end())
                + "</u>"
                + sentence.substring(matcher.end());
        return new SentenceBlank(underlinedSentence, trans, missIndex);
    }

    private VocabReviewQuizResponse.VocabReviewQuizResponseBuilder baseResponse(
            VocabContext context,
            ExerciseType exerciseType,
            String langCode
    ) {
        WordSenseResponse wordSense = resolveReviewSense(context, langCode).orElse(null);
        return VocabReviewQuizResponse.builder()
                .wordId(context.word().getId())
                .userVocabId(context.userVocabulary().getId())
                .word(context.word().getWord())
                .pos(context.word().getPos())
                .sound(resolveReviewSound(context.word()).orElse(null))
                .example(resolveReviewExample(context, langCode).orElse(null))
                .sense(wordSense)
                .wordSense(wordSense)
                .exerciseType(exerciseType);
    }

    private WordSenseResponse toLocalizedWordSenseResponse(Word word, WordSenseLocalization localization) {
        return WordSenseResponse.builder()
                .senseId(localization.getSenseId())
                .localizationId(localization.getId())
                .wordId(word.getId())
                .word(word.getWord())
                .pos(word.getPos())
                .certLevel(word.getCertLevel())
                .shortMeaning(localization.getShortMeaning())
                .definition(null)
                .synonyms(List.of())
                .antonyms(List.of())
                .examples(List.of())
                .trans(toSenseTranslation(localization))
                .derived(List.of())
                .coordinateTerms(List.of())
                .formOf(List.of())
                .altOf(List.of())
                .build();
    }

    private WordSenseResponse toWordSenseResponse(
            Word word,
            WordSense sense,
            WordSenseLocalization localization
    ) {
        return WordSenseResponse.builder()
                .senseId(sense.getId())
                .localizationId(localization != null ? localization.getId() : null)
                .wordId(word.getId())
                .word(word.getWord())
                .pos(word.getPos())
                .certLevel(word.getCertLevel())
                .shortMeaning(localization != null ? localization.getShortMeaning() : null)
                .definition(sense.getDefinition())
                .synonyms(RedisUtil.deserializeList(sense.getSynonyms(), String.class))
                .antonyms(RedisUtil.deserializeList(sense.getAntonyms(), String.class))
                .examples(List.of())
                .trans(localization != null ? toSenseTranslation(localization) : null)
                .derived(RedisUtil.deserializeList(sense.getDerived(), String.class))
                .coordinateTerms(RedisUtil.deserializeList(sense.getCoordinateTerms(), String.class))
                .formOf(RedisUtil.deserializeList(sense.getFormOf(), String.class))
                .altOf(RedisUtil.deserializeList(sense.getAltOf(), String.class))
                .build();
    }

    private WordSenseResponse.Translation toSenseTranslation(WordSenseLocalization localization) {
        if (localization == null
                || (!StringUtils.hasText(localization.getShortMeaning())
                && !StringUtils.hasText(localization.getFullLocalizedDefinition()))) {
            return null;
        }
        return WordSenseResponse.Translation.builder()
                .langCode(localization.getLangCode())
                .shortMeaning(localization.getShortMeaning())
                .definition(localization.getFullLocalizedDefinition())
                .build();
    }

    private WordSoundResponse toWordSoundResponse(WordSound sound) {
        return WordSoundResponse.builder()
                .wordId(sound.getWordId())
                .ipa(sound.getIpa())
                .tags(RedisUtil.deserializeList(sound.getTags(), String.class))
                .soundSource(sound.getSoundSource())
                .oggUrl(sound.getOggUrl())
                .mp3Url(sound.getMp3Url())
                .enpr(sound.getEnpr())
                .build();
    }

    private WordExampleResponse toWordExampleResponse(WordExampleProjection projection) {
        return WordExampleResponse.builder()
                .wordExampleId(projection.getWordExampleId())
                .senseId(projection.getSenseId())
                .wordSenseLocalizationId(projection.getWordSenseLocalizationId())
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .pos(projection.getPos())
                .certLevel(projection.getCertLevel())
                .sentence(projection.getSentence())
                .trans(projection.getTrans())
                .build();
    }

    private int normalizedLetterCount(String word) {
        if (!StringUtils.hasText(word)) {
            return 0;
        }
        return Normalizer.normalize(word, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .length();
    }

    private String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private List<String> getCompletedListenAndTypeChallengeIds(String userId, String lessonId) {
        List<String> completedIds = userVocabAttemptRepository
                .findCompletedListenAndTypeChallengeIds(userId, lessonId);
        log.debug("Completed listen-and-type challenges resolved: userId={}, lessonId={}, completedCount={}",
                userId, lessonId, completedIds.size());
        return completedIds;
    }

    private void assertLessonExists(String lessonId, String lessonType) {
        if (LESSON_TYPE_LISTEN_AND_TYPE.equals(lessonType)) {
            getRequiredListenExercise(lessonId);
            return;
        }
        throw new AppException(ErrorCode.INVALID_LESSON_TYPE);
    }

    private ListenExercise getRequiredListenExercise(String lessonId) {
        return listenExerciseRepository.findById(lessonId)
                .orElseThrow(() -> new AppException(ErrorCode.LESSON_NOT_FOUND));
    }

    private UserVocabulary getRequiredUserVocabulary(String userVocabId) {
        return userVocabularyRepository.findById(userVocabId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_VOCABULARY_NOT_FOUND));
    }

    private String normalizeLessonType(String lessonType) {
        if (!StringUtils.hasText(lessonType)) {
            throw new AppException(ErrorCode.INVALID_LESSON_TYPE);
        }
        String normalized = lessonType.trim()
                .replace('-', '_')
                .toUpperCase();
        if ("LAT".equals(normalized)) {
            return LESSON_TYPE_LISTEN_AND_TYPE;
        }
        if (LESSON_TYPE_LISTEN_AND_TYPE.equals(normalized)) {
            return normalized;
        }
        throw new AppException(ErrorCode.INVALID_LESSON_TYPE);
    }

    private String resolveListeningCategoryName(String categoryId) {
        if (!StringUtils.hasText(categoryId)) {
            return null;
        }
        return listeningCategoryRepository.findById(categoryId)
                .map(ListeningCategory::getCategoryName)
                .orElse(null);
    }

    private UserLessonResponse toUserLessonResponse(UserLesson userLesson) {
        return UserLessonResponse.builder()
                .id(userLesson.getId())
                .userId(userLesson.getUserId())
                .lessonId(userLesson.getLessonId())
                .lessonType(userLesson.getLessonType())
                .createdAt(userLesson.getCreatedAt())
                .updatedAt(userLesson.getUpdatedAt())
                .build();
    }

    private ListenAndTypeLessonResponse.ChallengeResponse toListenAndTypeChallengeResponse(
            ListenAndTypeExerciseChallenge challenge,
            Set<String> completedChallengeIds
    ) {
        return ListenAndTypeLessonResponse.ChallengeResponse.builder()
                .id(challenge.getId())
                .position(challenge.getPosition())
                .content(challenge.getContent())
                .jsonContent(challenge.getJsonContent())
                .solution(challenge.getSolution())
                .timeStart(challenge.getTimeStart())
                .timeEnd(challenge.getTimeEnd())
                .hints(challenge.getHints())
                .audioSrc(challenge.getAudioSrc())
                .isDone(completedChallengeIds.contains(challenge.getId()))
                .build();
    }

    private ListeningCategoryResponse toListeningCategoryResponse(ListeningCategory category) {
        return ListeningCategoryResponse.builder()
                .id(category.getId())
                .categoryName(category.getCategoryName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .build();
    }

    private ListenExerciseSummaryResponse toListenExerciseSummaryResponse(ListenExercise lesson, String userId) {
        return ListenExerciseSummaryResponse.builder()
                .id(lesson.getLessonId())
                .title(lesson.getTitle())
                .speechToTextLangCode(lesson.getSpeechToTextLangCode())
                .totalPart(listenAndTypeExerciseChallengeRepository.countByListenExerciseId(lesson.getLessonId()))
                .completedPart(userVocabAttemptRepository.countCompletedListenAndTypeChallenges(userId, lesson.getLessonId()))
                .build();
    }

    private List<String> resolveRawSubCategoryNames(String subCategoryName) {
        List<String> allSubCategoryNames = listenAndTypeSubCategoryRepository.findAll()
                .stream()
                .map(ListenAndTypeSubCategory::getSubCategoryName)
                .filter(StringUtils::hasText)
                .toList();

        List<String> exactMatches = allSubCategoryNames.stream()
                .filter(rawName -> rawName.equalsIgnoreCase(subCategoryName))
                .distinct()
                .toList();
        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        List<String> displayMatches = allSubCategoryNames.stream()
                .filter(rawName -> displaySubCategoryName(rawName).equalsIgnoreCase(subCategoryName))
                .distinct()
                .toList();
        return displayMatches.isEmpty() ? List.of(subCategoryName) : displayMatches;
    }

    private String displaySubCategoryName(String subCategoryName) {
        if (!StringUtils.hasText(subCategoryName)) {
            return subCategoryName;
        }

        Matcher matcher = SECTION_SUB_CATEGORY_PATTERN.matcher(subCategoryName.trim());
        if (matcher.matches()) {
            return "Section " + matcher.group(2);
        }
        return subCategoryName;
    }

    private int compareListenExercisesNaturally(ListenExercise first, ListenExercise second) {
        return compareNaturalStrings(
                firstText(first.getTitle(), first.getLessonId()),
                firstText(second.getTitle(), second.getLessonId())
        );
    }

    private int compareNaturalStrings(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        List<String> leftTokens = naturalSortTokens(left);
        List<String> rightTokens = naturalSortTokens(right);
        int commonLength = Math.min(leftTokens.size(), rightTokens.size());

        for (int i = 0; i < commonLength; i++) {
            String leftToken = leftTokens.get(i);
            String rightToken = rightTokens.get(i);
            int tokenCompare = compareNaturalToken(leftToken, rightToken);
            if (tokenCompare != 0) {
                return tokenCompare;
            }
        }

        int lengthCompare = Integer.compare(leftTokens.size(), rightTokens.size());
        return lengthCompare != 0 ? lengthCompare : left.compareToIgnoreCase(right);
    }

    private List<String> naturalSortTokens(String value) {
        Matcher matcher = NATURAL_SORT_TOKEN_PATTERN.matcher(value);
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }

    private int compareNaturalToken(String first, String second) {
        boolean firstNumber = first.chars().allMatch(Character::isDigit);
        boolean secondNumber = second.chars().allMatch(Character::isDigit);
        if (firstNumber && secondNumber) {
            String normalizedFirst = stripLeadingZeros(first);
            String normalizedSecond = stripLeadingZeros(second);
            int lengthCompare = Integer.compare(normalizedFirst.length(), normalizedSecond.length());
            if (lengthCompare != 0) {
                return lengthCompare;
            }
            int valueCompare = normalizedFirst.compareTo(normalizedSecond);
            return valueCompare != 0 ? valueCompare : Integer.compare(first.length(), second.length());
        }
        return first.compareToIgnoreCase(second);
    }

    private String stripLeadingZeros(String value) {
        String stripped = value.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private void assertUserExists(String userId) {
        if (!userInfoRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private record VocabContext(UserVocabulary userVocabulary, Word word, String meaning) {
    }

    private record SentenceBlank(String sentence, String trans, Integer missIndex) {
    }

    private record SentenceText(String sentence, String trans) {
    }

    private record IndexedOptions(Map<Integer, String> metadata, String correctAnswer) {
    }
}
