package com.bachdauduc.vocab_app.service;

import com.bachdauduc.vocab_app.constant.ExerciseType;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.SubmitReviewAttemptRequest;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.UserSearchHistoryRequest;
import com.bachdauduc.vocab_app.dto.request.uservocabulary.UserVocabularyRequest;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserSearchHistoryResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabAttemptResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.UserVocabularyStatisticResponse;
import com.bachdauduc.vocab_app.dto.response.uservocabulary.WrongVocabResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordResponse;
import com.bachdauduc.vocab_app.dto.response.worddata.WordSenseResponse;
import com.bachdauduc.vocab_app.entity.UserSearchHistory;
import com.bachdauduc.vocab_app.entity.UserVocabAttempt;
import com.bachdauduc.vocab_app.entity.UserVocabulary;
import com.bachdauduc.vocab_app.entity.WordSenseLocalization;
import com.bachdauduc.vocab_app.exception.AppException;
import com.bachdauduc.vocab_app.exception.ErrorCode;
import com.bachdauduc.vocab_app.properties.RedisKeyProperties;
import com.bachdauduc.vocab_app.repository.ListenAndTypeExerciseChallengeRepository;
import com.bachdauduc.vocab_app.repository.UserInfoRepository;
import com.bachdauduc.vocab_app.repository.UserSearchHistoryRepository;
import com.bachdauduc.vocab_app.repository.UserVocabAttemptRepository;
import com.bachdauduc.vocab_app.repository.UserVocabularyRepository;
import com.bachdauduc.vocab_app.repository.WordRepository;
import com.bachdauduc.vocab_app.repository.WordSenseLocalizationRepository;
import com.bachdauduc.vocab_app.repository.WordSenseRepository;
import com.bachdauduc.vocab_app.repository.projection.UserSearchHistoryProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabAttemptProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabStatisticProjection;
import com.bachdauduc.vocab_app.repository.projection.UserVocabularyProjection;
import com.bachdauduc.vocab_app.repository.projection.WrongVocabProjection;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserVocabularyService {
    private static final long MOST_WRONG_THRESHOLD = 5L;

    UserVocabularyRepository userVocabularyRepository;
    UserVocabAttemptRepository userVocabAttemptRepository;
    UserSearchHistoryRepository userSearchHistoryRepository;
    UserInfoRepository userInfoRepository;
    WordRepository wordRepository;
    WordSenseRepository wordSenseRepository;
    WordSenseLocalizationRepository wordSenseLocalizationRepository;
    ListenAndTypeExerciseChallengeRepository listenAndTypeExerciseChallengeRepository;
    GetWordDataService getWordDataService;
    RedisTemplate<String, String> redisTemplate;
    RedisKeyProperties redisKeyProperties;

    @Transactional
    public UserVocabularyResponse addUserVocab(UserVocabularyRequest request) {
        log.debug("Start service: method=addUserVocab, userId={}, wordId={}, senseId={}, senseLocalizedId={}, requestedLevel={}",
                request.getUserId(), request.getWordId(), request.getSenseId(), request.getSenseLocalizedId(), request.getLevel());
        assertUserExists(request.getUserId());
        validateUserVocabularyRequest(request);
        validateUserVocabularyNotExists(request);

        UserVocabulary userVocabulary = new UserVocabulary();
        userVocabulary.setId(UUID.randomUUID().toString());
        userVocabulary.setUserId(request.getUserId());
        userVocabulary.setWordId(request.getWordId());
        userVocabulary.setSenseId(request.getSenseId());
        userVocabulary.setSenseLocalizedId(request.getSenseLocalizedId());
        userVocabulary.setLevel(resolveLevel(request.getLevel()));
        userVocabulary.setCurrentLevelCorrectTurns(0);
        userVocabulary.setNextReviewAt(LocalDateTime.now());

        UserVocabulary saved = userVocabularyRepository.save(userVocabulary);
        log.info("User vocabulary added: userId={}, userVocabId={}, wordId={}",
                saved.getUserId(), saved.getId(), saved.getWordId());
        return toUserVocabularyResponse(saved);
    }

    @Transactional
    public UserVocabAttemptResponse submitReviewAttempt(SubmitReviewAttemptRequest request) {
        log.debug("Start service: method=submitReviewAttempt, userId={}, userVocabId={}, attemptId={}, exerciseType={}, correct={}, replayCount={}",
                request.getUserId(), request.getUserVocabId(), request.getAttemptId(), request.getExerciseType(),
                request.getCorrect(), request.getReplayCount());
        assertUserExists(request.getUserId());
        validateExerciseTarget(request);

        UserVocabAttempt attempt = new UserVocabAttempt();
        attempt.setId(UUID.randomUUID().toString());
        attempt.setAttemptId(request.getAttemptId());
        attempt.setUserId(request.getUserId());
        attempt.setUserVocabId(request.getUserVocabId());
        attempt.setExerciseType(request.getExerciseType());
        attempt.setUserAnswer(request.getUserAnswer());
        attempt.setReview(request.getReview());
        attempt.setCorrect(request.getCorrect());
        attempt.setReplayCount(request.getReplayCount() == null ? 0 : request.getReplayCount());

        UserVocabAttempt savedAttempt = userVocabAttemptRepository.save(attempt);

        if (request.getExerciseType().isVocab()) {
            UserVocabulary userVocabulary = getRequiredUserVocabulary(request.getUserVocabId());
            if (shouldUpdateReviewSchedule(request)) {
                Integer oldLevel = userVocabulary.getLevel();
                Integer oldTurns = userVocabulary.getCurrentLevelCorrectTurns();
                updateReviewSchedule(userVocabulary, request.getCorrect());
                userVocabularyRepository.save(userVocabulary);
                log.info("User vocabulary review schedule updated: userId={}, userVocabId={}, oldLevel={}, newLevel={}, oldTurns={}, newTurns={}, correct={}, nextReviewAt={}",
                        request.getUserId(), userVocabulary.getId(), oldLevel, userVocabulary.getLevel(), oldTurns,
                        userVocabulary.getCurrentLevelCorrectTurns(), request.getCorrect(), userVocabulary.getNextReviewAt());
            } else {
                log.info("User vocabulary review schedule skipped: userId={}, userVocabId={}, correct={}, reason=wrong_attempt_already_counted",
                        request.getUserId(), userVocabulary.getId(), request.getCorrect());
            }
        }

        log.info("User vocab attempt saved: userId={}, attemptId={}, exerciseType={}, correct={}",
                request.getUserId(), savedAttempt.getId(), request.getExerciseType(), request.getCorrect());
        return toUserVocabAttemptResponse(savedAttempt);
    }

    @Transactional
    public UserSearchHistoryResponse insertUserHistory(UserSearchHistoryRequest request) {
        log.debug("Start service: method=insertUserHistory, userId={}, wordId={}",
                request.getUserId(), request.getWordId());
        assertUserExists(request.getUserId());
        assertWordExists(request.getWordId());

        UserSearchHistory history = new UserSearchHistory();
        history.setId(UUID.randomUUID().toString());
        history.setUserId(request.getUserId());
        history.setWordId(request.getWordId());

        UserSearchHistory saved = userSearchHistoryRepository.save(history);
        log.info("User search history inserted: userId={}, wordId={}, historyId={}",
                saved.getUserId(), saved.getWordId(), saved.getId());
        return UserSearchHistoryResponse.builder()
                .id(saved.getId())
                .userId(saved.getUserId())
                .wordId(saved.getWordId())
                .build();
    }

    public Page<UserSearchHistoryResponse> getUserHistory(String userId, int page, int limit) {
        log.debug("Start service: method=getUserHistory, userId={}, page={}, limit={}", userId, page, limit);
        assertUserExists(userId);
        Page<UserSearchHistoryResponse> response = userSearchHistoryRepository.findUserSearchHistory(userId, pageRequest(page, limit))
                .map(this::toUserSearchHistoryResponse);
        log.info("User search history loaded: userId={}, page={}, limit={}, resultCount={}, totalElements={}",
                userId, page, limit, response.getNumberOfElements(), response.getTotalElements());
        return response;
    }

    public Page<UserVocabAttemptResponse> getUserAttemptListByDay(
            String userId,
            LocalDate from,
            LocalDate to,
            int page,
            int limit,
            String type
    ) {
        log.debug("Start service: method=getUserAttemptListByDay, userId={}, from={}, to={}, page={}, limit={}, type={}",
                userId, from, to, page, limit, type);
        assertUserExists(userId);
        LocalDateTime fromTime = from.atStartOfDay();
        LocalDateTime toTime = to.plusDays(1).atStartOfDay();
        String exercisePrefix = resolveExercisePrefix(type);
        Page<UserVocabAttemptResponse> response = userVocabAttemptRepository.findUserAttempts(
                        userId,
                        fromTime,
                        toTime,
                        exercisePrefix,
                        pageRequest(page, limit)
                )
                .map(this::toUserVocabAttemptResponse);
        log.info("User attempts loaded: userId={}, from={}, to={}, exercisePrefix={}, resultCount={}, totalElements={}",
                userId, from, to, exercisePrefix, response.getNumberOfElements(), response.getTotalElements());
        return response;
    }

    public Page<UserVocabularyResponse> getUserVocabByLevel(String userId, int level, int page, int limit) {
        log.debug("Start service: method=getUserVocabByLevel, userId={}, level={}, page={}, limit={}",
                userId, level, page, limit);
        assertUserExists(userId);
        Page<UserVocabularyResponse> response = userVocabularyRepository
                .findUserVocabByLevelWithWord(userId, level, pageRequest(page, limit))
                .map(this::toUserVocabularyResponse);
        log.info("User vocabularies by level loaded: userId={}, level={}, resultCount={}, totalElements={}",
                userId, level, response.getNumberOfElements(), response.getTotalElements());
        return response;
    }

    public UserVocabularyStatisticResponse getUserDailyStatistic(String userId) {
        log.debug("Start service: method=getUserDailyStatistic, userId={}", userId);
        assertUserExists(userId);
        LocalDate today = LocalDate.now();
        LocalDateTime fromTime = today.atStartOfDay();
        LocalDateTime toTime = today.plusDays(1).atStartOfDay();

        UserVocabStatisticProjection statistic =
                userVocabAttemptRepository.getUserDailyStatistic(userId, fromTime, toTime);
        List<WrongVocabResponse> wrongVocabs = userVocabAttemptRepository
                .findWrongVocabs(userId, fromTime, toTime)
                .stream()
                .map(this::toWrongVocabResponse)
                .toList();

        UserVocabularyStatisticResponse response = buildStatisticResponse(userId, today, statistic, wrongVocabs, List.of());
        log.info("User daily statistic built: userId={}, date={}, totalAttempts={}, wrongVocabCount={}",
                userId, today, response.getTotalAttempts(), wrongVocabs.size());
        return response;
    }

    public UserVocabularyStatisticResponse getUserOverallStatistic(String userId) {
        log.debug("Start service: method=getUserOverallStatistic, userId={}", userId);
        assertUserExists(userId);

        UserVocabStatisticProjection statistic =
                userVocabAttemptRepository.getUserOverallStatistic(userId);
        List<WrongVocabResponse> mostWrongVocabs = userVocabAttemptRepository
                .findMostWrongVocabs(userId, MOST_WRONG_THRESHOLD)
                .stream()
                .map(this::toWrongVocabResponse)
                .toList();

        UserVocabularyStatisticResponse response = buildStatisticResponse(userId, LocalDate.now(), statistic, List.of(), mostWrongVocabs);
        log.info("User overall statistic built: userId={}, totalAttempts={}, mostWrongCount={}",
                userId, response.getTotalAttempts(), mostWrongVocabs.size());
        return response;
    }

    public WordResponse getUserVocabWord(String userVocabId) {
        log.debug("Start service: method=getUserVocabWord, userVocabId={}", userVocabId);
        UserVocabulary userVocabulary = getRequiredUserVocabulary(userVocabId);

        WordResponse wordResponse;
        if (StringUtils.hasText(userVocabulary.getSenseLocalizedId())) {
            WordSenseLocalization localization = wordSenseLocalizationRepository.findById(userVocabulary.getSenseLocalizedId())
                    .orElseThrow(() -> new AppException(ErrorCode.WORD_NOT_FOUND));
            log.debug("Load user vocab word by localized sense: userVocabId={}, wordId={}, localizationId={}, langCode={}",
                    userVocabId, userVocabulary.getWordId(), userVocabulary.getSenseLocalizedId(), localization.getLangCode());
            wordResponse = getWordDataService.getWord(userVocabulary.getWordId(), true, localization.getLangCode());
            filterSensesByLocalization(wordResponse, userVocabulary.getSenseLocalizedId(), localization.getSenseId());
        } else {
            log.debug("Load user vocab word by sense: userVocabId={}, wordId={}, senseId={}",
                    userVocabId, userVocabulary.getWordId(), userVocabulary.getSenseId());
            wordResponse = getWordDataService.getWord(userVocabulary.getWordId(), false, null);
            filterSensesBySense(wordResponse, userVocabulary.getSenseId());
        }

        log.info("User vocab word loaded: userVocabId={}, wordId={}, filteredSenseCount={}",
                userVocabId, userVocabulary.getWordId(), wordResponse.getSenses().size());
        return wordResponse;
    }

    private void validateUserVocabularyRequest(UserVocabularyRequest request) {
        boolean hasSenseId = StringUtils.hasText(request.getSenseId());
        boolean hasSenseLocalizedId = StringUtils.hasText(request.getSenseLocalizedId());
        log.debug("Validate user vocabulary request: userId={}, wordId={}, hasSenseId={}, hasSenseLocalizedId={}",
                request.getUserId(), request.getWordId(), hasSenseId, hasSenseLocalizedId);
        if (hasSenseId == hasSenseLocalizedId) {
            throw new AppException(ErrorCode.INVALID_USER_VOCABULARY_REQUEST);
        }

        assertWordExists(request.getWordId());
        if (hasSenseId && !wordSenseRepository.existsByIdAndWordId(request.getSenseId(), request.getWordId())) {
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
        if (hasSenseLocalizedId
                && !wordSenseLocalizationRepository.existsByIdAndWordId(request.getSenseLocalizedId(), request.getWordId())) {
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
    }

    private void validateUserVocabularyNotExists(UserVocabularyRequest request) {
        if (StringUtils.hasText(request.getSenseId())
                && userVocabularyRepository.existsByUserIdAndWordIdAndSenseId(
                request.getUserId(),
                request.getWordId(),
                request.getSenseId()
        )) {
            log.warn("Add user vocabulary failed: userId={}, wordId={}, senseId={}, reason=already_exists",
                    request.getUserId(), request.getWordId(), request.getSenseId());
            throw new AppException(
                    ErrorCode.USER_VOCABULARY_ALREADY_EXISTS,
                    "wordId + senseId already exists"
            );
        }

        if (StringUtils.hasText(request.getSenseLocalizedId())
                && userVocabularyRepository.existsByUserIdAndWordIdAndSenseLocalizedId(
                request.getUserId(),
                request.getWordId(),
                request.getSenseLocalizedId()
        )) {
            log.warn("Add user vocabulary failed: userId={}, wordId={}, senseLocalizedId={}, reason=already_exists",
                    request.getUserId(), request.getWordId(), request.getSenseLocalizedId());
            throw new AppException(
                    ErrorCode.USER_VOCABULARY_ALREADY_EXISTS,
                    "wordId + senseLocalizedId already exists"
            );
        }
    }

    private void validateExerciseTarget(SubmitReviewAttemptRequest request) {
        log.debug("Validate exercise target: userId={}, userVocabId={}, attemptId={}, exerciseType={}",
                request.getUserId(), request.getUserVocabId(), request.getAttemptId(), request.getExerciseType());
        if (request.getExerciseType().isVocab()) {
            if (!StringUtils.hasText(request.getUserVocabId())
                    || !userVocabularyRepository.existsById(request.getUserVocabId())) {
                throw new AppException(ErrorCode.USER_VOCABULARY_NOT_FOUND);
            }
            return;
        }

        if (request.getExerciseType().isListenAndType()) {
            if (!StringUtils.hasText(request.getAttemptId())
                    || !listenAndTypeExerciseChallengeRepository.existsById(request.getAttemptId())) {
                throw new AppException(ErrorCode.LISTEN_AND_TYPE_CHALLENGE_NOT_FOUND);
            }
            return;
        }

        if (!request.getExerciseType().isQuiz()) {
            throw new AppException(ErrorCode.INVALID_EXERCISE_TYPE);
        }
    }

    private boolean shouldUpdateReviewSchedule(SubmitReviewAttemptRequest request) {
        if (Boolean.TRUE.equals(request.getCorrect())) {
            return true;
        }

        String key = redisKeyProperties.currentReviewWrongKey(request.getUserVocabId());
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            log.debug("Wrong review update skipped by redis marker: key={}, userVocabId={}",
                    key, request.getUserVocabId());
            return false;
        }

        redisTemplate.opsForValue().set(key, "1", redisKeyProperties.currentReviewWrongTtl());
        log.debug("Wrong review redis marker saved: key={}, ttlSeconds={}",
                key, redisKeyProperties.currentReviewWrongTtl().toSeconds());
        return true;
    }

    private void updateReviewSchedule(UserVocabulary userVocabulary, boolean correct) {
        int currentLevel = normalizeLevel(userVocabulary.getLevel());
        int currentTurns = userVocabulary.getCurrentLevelCorrectTurns() == null
                ? 0
                : userVocabulary.getCurrentLevelCorrectTurns();

        ReviewUpdate reviewUpdate = correct
                ? nextCorrectReview(currentLevel, currentTurns)
                : nextWrongReview(currentLevel);

        userVocabulary.setLevel(reviewUpdate.level());
        userVocabulary.setCurrentLevelCorrectTurns(reviewUpdate.currentLevelCorrectTurns());
        userVocabulary.setNextReviewAt(reviewUpdate.nextReviewAt());
        log.debug("Review schedule calculated: userVocabId={}, correct={}, oldLevel={}, oldTurns={}, newLevel={}, newTurns={}, nextReviewAt={}",
                userVocabulary.getId(), correct, currentLevel, currentTurns, reviewUpdate.level(),
                reviewUpdate.currentLevelCorrectTurns(), reviewUpdate.nextReviewAt());
    }

    private ReviewUpdate nextCorrectReview(int level, int currentTurns) {
        LocalDateTime now = LocalDateTime.now();
        if (level == 6) {
            int newTurns = currentTurns + 1;
            return new ReviewUpdate(6, newTurns, switch (Math.min(newTurns, 4)) {
                case 1 -> now.plusDays(14);
                case 2 -> now.plusDays(30);
                case 3 -> now.plusDays(60);
                default -> now.plusDays(90);
            });
        }

        int requiredTurns = requiredCorrectTurns(level);
        int newTurns = currentTurns + 1;
        boolean levelUp = newTurns >= requiredTurns;
        int newLevel = levelUp ? Math.min(level + 1, 6) : level;
        int savedTurns = levelUp ? 0 : newTurns;

        return new ReviewUpdate(newLevel, savedTurns, correctNextReviewAt(level, levelUp, now));
    }

    private ReviewUpdate nextWrongReview(int level) {
        LocalDateTime now = LocalDateTime.now();
        if (level == 6) {
            return new ReviewUpdate(5, 0, now.plusDays(3));
        }

        return new ReviewUpdate(level, 0, switch (level) {
            case 1 -> now.plusHours(1);
            case 2 -> now.plusHours(4);
            case 3 -> now.plusHours(6);
            case 4 -> now.plusHours(12);
            case 5 -> now.plusDays(1);
            default -> now.plusHours(1);
        });
    }

    private LocalDateTime correctNextReviewAt(int level, boolean levelUp, LocalDateTime now) {
        return switch (level) {
            case 1 -> levelUp ? now.plusHours(6) : now.plusHours(2);
            case 2 -> levelUp ? now.plusHours(12) : now.plusHours(5);
            case 3 -> levelUp ? now.plusDays(1) : now.plusHours(9);
            case 4 -> levelUp ? now.plusDays(3) : now.plusHours(14);
            case 5 -> levelUp ? now.plusDays(14) : now.plusDays(1);
            default -> now.plusHours(2);
        };
    }

    private int requiredCorrectTurns(int level) {
        return level == 5 ? 4 : 2;
    }

    private int resolveLevel(Integer level) {
        int resolvedLevel = level == null ? 1 : level;
        if (resolvedLevel < 1 || resolvedLevel > 6) {
            throw new AppException(ErrorCode.INVALID_USER_VOCABULARY_REQUEST);
        }
        return resolvedLevel;
    }

    private int normalizeLevel(Integer level) {
        if (level == null || level < 1) {
            return 1;
        }
        return Math.min(level, 6);
    }

    private String resolveExercisePrefix(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        String normalizedType = type.trim().toUpperCase();
        return switch (normalizedType) {
            case "VOCAB", "QUIZ", "LAT" -> normalizedType + "_";
            default -> normalizedType;
        };
    }

    private void filterSensesBySense(WordResponse wordResponse, String senseId) {
        List<WordSenseResponse> senses = wordResponse.getSenses().stream()
                .filter(sense -> senseId.equals(sense.getSenseId()))
                .toList();
        if (senses.isEmpty()) {
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
        wordResponse.setSenses(senses);
        log.debug("Filtered word senses by senseId: wordId={}, senseId={}, resultCount={}",
                wordResponse.getWordId(), senseId, senses.size());
    }

    private void filterSensesByLocalization(WordResponse wordResponse, String localizationId, String senseId) {
        List<WordSenseResponse> senses = wordResponse.getSenses().stream()
                .filter(sense -> localizationId.equals(sense.getLocalizationId())
                        || (StringUtils.hasText(senseId) && senseId.equals(sense.getSenseId())))
                .toList();
        if (senses.isEmpty()) {
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
        wordResponse.setSenses(senses);
        log.debug("Filtered word senses by localization: wordId={}, localizationId={}, senseId={}, resultCount={}",
                wordResponse.getWordId(), localizationId, senseId, senses.size());
    }

    private UserVocabularyStatisticResponse buildStatisticResponse(
            String userId,
            LocalDate statisticDate,
            UserVocabStatisticProjection statistic,
            List<WrongVocabResponse> wrongVocabIds,
            List<WrongVocabResponse> mostWrongVocabIds
    ) {
        return UserVocabularyStatisticResponse.builder()
                .userId(userId)
                .statisticDate(statisticDate)
                .totalAttempts(valueOf(statistic.getTotalAttempts()))
                .correctQuizAttempt(valueOf(statistic.getCorrectQuizAttempt()))
                .wrongQuizAttempt(valueOf(statistic.getWrongQuizAttempt()))
                .totalUniqueVocab(valueOf(statistic.getTotalUniqueVocab()))
                .correctUniqueVocab(nullableValueOf(statistic.getCorrectUniqueVocab()))
                .wrongUniqueVocab(nullableValueOf(statistic.getWrongUniqueVocab()))
                .wrongCountVocab(nullableValueOf(statistic.getWrongCountVocab()))
                .wrongVocabIds(wrongVocabIds)
                .mostWrongVocabIds(mostWrongVocabIds)
                .build();
    }

    private long valueOf(Long value) {
        return value == null ? 0L : value;
    }

    private Long nullableValueOf(Long value) {
        return value;
    }

    private PageRequest pageRequest(int page, int limit) {
        return PageRequest.of(Math.max(page, 0), Math.max(limit, 1));
    }

    private UserVocabulary getRequiredUserVocabulary(String userVocabId) {
        return userVocabularyRepository.findById(userVocabId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_VOCABULARY_NOT_FOUND));
    }

    private void assertUserExists(String userId) {
        if (!userInfoRepository.existsById(userId)) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private void assertWordExists(String wordId) {
        if (!wordRepository.existsById(wordId)) {
            throw new AppException(ErrorCode.WORD_NOT_FOUND);
        }
    }

    private UserVocabularyResponse toUserVocabularyResponse(UserVocabulary userVocabulary) {
        return UserVocabularyResponse.builder()
                .id(userVocabulary.getId())
                .userId(userVocabulary.getUserId())
                .wordId(userVocabulary.getWordId())
                .senseId(userVocabulary.getSenseId())
                .senseLocalizedId(userVocabulary.getSenseLocalizedId())
                .level(userVocabulary.getLevel())
                .currentLevelCorrectTurns(userVocabulary.getCurrentLevelCorrectTurns())
                .nextReviewAt(userVocabulary.getNextReviewAt())
                .createdAt(userVocabulary.getCreatedAt())
                .updatedAt(userVocabulary.getUpdatedAt())
                .build();
    }

    private UserVocabularyResponse toUserVocabularyResponse(UserVocabularyProjection projection) {
        return UserVocabularyResponse.builder()
                .id(projection.getId())
                .userId(projection.getUserId())
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .senseId(projection.getSenseId())
                .senseLocalizedId(projection.getSenseLocalizedId())
                .level(projection.getLevel())
                .currentLevelCorrectTurns(projection.getCurrentLevelCorrectTurns())
                .nextReviewAt(projection.getNextReviewAt())
                .createdAt(projection.getCreatedAt())
                .updatedAt(projection.getUpdatedAt())
                .build();
    }

    private UserVocabAttemptResponse toUserVocabAttemptResponse(UserVocabAttempt attempt) {
        return UserVocabAttemptResponse.builder()
                .id(attempt.getId())
                .attemptId(attempt.getAttemptId())
                .userId(attempt.getUserId())
                .userVocabId(attempt.getUserVocabId())
                .exerciseType(attempt.getExerciseType())
                .userAnswer(attempt.getUserAnswer())
                .review(attempt.getReview())
                .correct(attempt.getCorrect())
                .replayCount(attempt.getReplayCount())
                .createdAt(attempt.getCreatedAt())
                .build();
    }

    private UserVocabAttemptResponse toUserVocabAttemptResponse(UserVocabAttemptProjection projection) {
        return UserVocabAttemptResponse.builder()
                .id(projection.getId())
                .attemptId(projection.getAttemptId())
                .userId(projection.getUserId())
                .userVocabId(projection.getUserVocabId())
                .exerciseType(ExerciseType.valueOf(projection.getExerciseType()))
                .userAnswer(projection.getUserAnswer())
                .review(projection.getReview())
                .correct(projection.getCorrect())
                .replayCount(projection.getReplayCount())
                .createdAt(projection.getCreatedAt())
                .build();
    }

    private UserSearchHistoryResponse toUserSearchHistoryResponse(UserSearchHistoryProjection projection) {
        return UserSearchHistoryResponse.builder()
                .id(projection.getId())
                .userId(projection.getUserId())
                .wordId(projection.getWordId())
                .word(projection.getWord())
                .searchedAt(projection.getSearchedAt())
                .build();
    }

    private WrongVocabResponse toWrongVocabResponse(WrongVocabProjection projection) {
        return WrongVocabResponse.builder()
                .userVocabId(projection.getUserVocabId())
                .word(projection.getWord())
                .wrongCount(valueOf(projection.getWrongCount()))
                .build();
    }

    private record ReviewUpdate(Integer level, Integer currentLevelCorrectTurns, LocalDateTime nextReviewAt) {
    }
}
