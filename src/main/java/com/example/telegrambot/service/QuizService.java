package com.example.telegrambot.service;

import com.example.telegrambot.entity.QuestionEntity;
import com.example.telegrambot.entity.QuizEntity;
import com.example.telegrambot.exception.BadRequestException;
import com.example.telegrambot.exception.QuizStateException;
import com.example.telegrambot.repository.QuestionRepository;
import com.example.telegrambot.repository.QuizRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class QuizService {

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;

    // Runtime user sessions (in-memory)
    private final Map<Long, List<QuestionEntity>> userQuestions = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userIndex = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userScore = new ConcurrentHashMap<>();

    // Track last activity per user
    private final Map<Long, Long> lastActivity = new ConcurrentHashMap<>();

    // Session timeout (10 minutes)
    private static final long SESSION_TIMEOUT_MS = 10 * 60 * 1000;

    public QuizService(QuizRepository quizRepository,
                       QuestionRepository questionRepository) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
    }

    // ======================================================
    // ================= SESSION TRACKING ===================
    // ======================================================

    private void touch(Long chatId) {
        lastActivity.put(chatId, System.currentTimeMillis());
    }

    private void clearSession(Long chatId) {
        userQuestions.remove(chatId);
        userIndex.remove(chatId);
        userScore.remove(chatId);
        lastActivity.remove(chatId);
    }

    // Auto cleanup every 2 minutes
    @Scheduled(fixedDelay = 120000)
    public void cleanupStaleSessions() {

        long now = System.currentTimeMillis();

        for (Long chatId : new HashSet<>(lastActivity.keySet())) {

            long lastSeen = lastActivity.get(chatId);

            if (now - lastSeen > SESSION_TIMEOUT_MS) {
                System.out.println("🧹 Cleaning stale session: " + chatId);
                clearSession(chatId);
            }
        }
    }

    // ======================================================
    // ================= PAGINATION =========================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "quizPageFallback")
    public Page<QuizEntity> getQuizPage(int page, int size) {
        return quizRepository.findAll(PageRequest.of(page, size));
    }

    public Page<QuizEntity> quizPageFallback(int page, int size, Throwable ex) {
        System.err.println("⚠️ CircuitBreaker[getQuizPage]: " + ex.getMessage());
        return Page.empty();
    }

    // ======================================================
    // ================= CREATE QUIZ ========================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "createQuizFallback")
    public QuizEntity createQuiz(String title, Long adminId) {

        QuizEntity quiz = new QuizEntity();
        quiz.setTitle(title);
        quiz.setCreatedBy(adminId);
        quiz.setActive(true);

        return quizRepository.save(quiz);
    }

    public QuizEntity createQuizFallback(String title, Long adminId, Throwable ex) {
        throw new RuntimeException("🚨 Unable to create quiz right now.");
    }

    // ======================================================
    // ================= ADD QUESTION =======================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "addQuestionFallback")
    public void addQuestion(Long quizId,
                            String question,
                            String o1,
                            String o2,
                            String o3,
                            String o4,
                            int correctIndex) {

        QuestionEntity q = new QuestionEntity();
        q.setQuizId(quizId);
        q.setQuestion(question);
        q.setOption1(o1);
        q.setOption2(o2);
        q.setOption3(o3);
        q.setOption4(o4);
        q.setCorrectIndex(correctIndex);

        questionRepository.save(q);
    }

    public void addQuestionFallback(Long quizId,
                                    String question,
                                    String o1,
                                    String o2,
                                    String o3,
                                    String o4,
                                    int correctIndex,
                                    Throwable ex) {
        throw new RuntimeException("🚨 Unable to save question right now.");
    }

    // ======================================================
    // ================= DELETE QUIZ ========================
    // ======================================================

    @Transactional
    @CircuitBreaker(name = "quizService", fallbackMethod = "deleteQuizFallback")
    public void deleteQuiz(Long quizId) {
        questionRepository.deleteByQuizId(quizId);
        quizRepository.deleteById(quizId);
    }

    public void deleteQuizFallback(Long quizId, Throwable ex) {
        throw new RuntimeException("🚨 Unable to delete quiz right now.");
    }

    // ======================================================
    // ================= START QUIZ =========================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "startQuizFallback")
    public void startQuiz(Long chatId, Long quizId) {

        List<QuestionEntity> questions =
                questionRepository.findByQuizIdOrderByIdAsc(quizId);

        if (questions.isEmpty()) {
            throw new BadRequestException("Quiz has no questions.");
        }

        userQuestions.put(chatId, questions);
        userIndex.put(chatId, 0);
        userScore.put(chatId, 0);
        touch(chatId);
    }

    public void startQuizFallback(Long chatId, Long quizId, Throwable ex) {
        throw new RuntimeException("🚨 Quiz service temporarily unavailable.");
    }

    // ======================================================
    // ================= NEXT QUESTION ======================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "getNextQuestionFallback")
    public synchronized QuestionEntity getNextQuestion(Long chatId) {

        touch(chatId);

        Integer index = userIndex.get(chatId);
        List<QuestionEntity> questions = userQuestions.get(chatId);

        if (index == null || questions == null) {
            return null;
        }

        if (index >= questions.size()) {
            return null;
        }

        userIndex.put(chatId, index + 1);
        return questions.get(index);
    }

    public QuestionEntity getNextQuestionFallback(Long chatId, Throwable ex) {
        return null;
    }

    // ======================================================
    // ================= CHECK ANSWER =======================
    // ======================================================

    @CircuitBreaker(name = "quizService", fallbackMethod = "checkAnswerFallback")
    public synchronized boolean checkAnswer(Long chatId, int selected) {

        touch(chatId);

        Integer index = userIndex.get(chatId);

        if (index == null) {
            throw new QuizStateException("No active quiz session.");
        }

        List<QuestionEntity> questions = userQuestions.get(chatId);

        if (questions == null || questions.isEmpty()) {
            throw new QuizStateException("No questions loaded.");
        }

        int currentIndex = index - 1;

        if (currentIndex < 0 || currentIndex >= questions.size()) {
            throw new QuizStateException("Invalid question state.");
        }

        QuestionEntity question = questions.get(currentIndex);
        boolean correct = question.getCorrectIndex() == selected;

        if (correct) {
            userScore.put(chatId, userScore.getOrDefault(chatId, 0) + 1);
        }

        return correct;
    }

    public boolean checkAnswerFallback(Long chatId, int selected, Throwable ex) {
        throw new RuntimeException("🚨 Answer validation temporarily unavailable.");
    }

    // ======================================================
    // ================= SCORE / RESET ======================
    // ======================================================

    public int getScore(Long chatId) {
        return userScore.getOrDefault(chatId, 0);
    }

    public void reset(Long chatId) {
        clearSession(chatId);
    }
}
