package com.example.telegrambot.bot;

import com.example.telegrambot.entity.QuestionEntity;
import com.example.telegrambot.entity.QuizEntity;
import com.example.telegrambot.exception.BadRequestException;
import com.example.telegrambot.exception.QuizStateException;
import com.example.telegrambot.service.AdminService;
import com.example.telegrambot.service.QuizService;
import com.example.telegrambot.session.AdminSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class QuizBot extends TelegramLongPollingBot {

    private final QuizService quizService;
    private final AdminService adminService;

    private final Set<Long> answeringUsers = ConcurrentHashMap.newKeySet();
    private final Map<Long, AdminSession> adminSessions = new ConcurrentHashMap<>();
    private final Map<Long, Integer> userPages = new ConcurrentHashMap<>();

    private static final int PAGE_SIZE = 5;

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.bot.username}")
    private String username;

    public QuizBot(QuizService quizService, AdminService adminService) {
        this.quizService = quizService;
        this.adminService = adminService;
        System.out.println("✅ QuizBot initialized");
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    // ==========================================================
    // ================= UPDATE HANDLER =========================
    // ==========================================================

    @Override
    public void onUpdateReceived(Update update) {

        if (update.hasMessage() && update.getMessage().hasText()) {
            handleText(update.getMessage());
            return;
        }

        if (update.hasCallbackQuery()) {
            handleCallback(update.getCallbackQuery());
        }
    }

    // ==========================================================
    // ================= TEXT HANDLING ==========================
    // ==========================================================

    private void handleText(Message message) {

        String text = message.getText().trim();
        Long chatId = message.getChatId();

        // START
        if (text.equalsIgnoreCase("/start")) {
            sendStartMenu(chatId);
            return;
        }

        // ================= ROOT ADMIN COMMANDS =================
        if (adminService.isRootAdmin(chatId)) {

            if (text.startsWith("/addadmin")) {
                String[] parts = text.split(" ");
                if (parts.length != 2) {
                    sendMessage(chatId, "Usage: /addadmin <chatId>");
                    return;
                }
                Long newAdminId = Long.parseLong(parts[1]);
                adminService.addAdmin(newAdminId);
                sendMessage(chatId, "✅ Admin added successfully!");
                return;
            }

            if (text.startsWith("/removeadmin")) {
                String[] parts = text.split(" ");
                if (parts.length != 2) {
                    sendMessage(chatId, "Usage: /removeadmin <chatId>");
                    return;
                }
                Long removeId = Long.parseLong(parts[1]);
                adminService.removeAdmin(removeId);
                sendMessage(chatId, "🗑️ Admin removed successfully!");
                return;
            }

            if (text.equals("/listadmins")) {
                var admins = adminService.getAllAdmins();
                StringBuilder sb = new StringBuilder("👥 Admins:\n");
                admins.forEach(a -> sb.append(a.getChatId()).append("\n"));
                sendMessage(chatId, sb.toString());
                return;
            }
        }

        // NORMAL ADMIN FLOW
        if (adminService.isAdmin(chatId)) {
            handleAdminText(chatId, text);
        }
    }

    // ==========================================================
    // ================= CALLBACK HANDLING ======================
    // ==========================================================

    private void handleCallback(CallbackQuery callback) {

        String data = callback.getData();
        Long chatId = callback.getMessage().getChatId();

        // SHOW QUIZ LIST
        if (data.equals("TAKE_QUIZ")) {
            userPages.put(chatId, 0);
            showQuizSelection(chatId, 0);
            return;
        }

        // PAGINATION
        if (data.startsWith("PAGE_")) {
            int page = Integer.parseInt(data.replace("PAGE_", ""));
            userPages.put(chatId, page);
            showQuizSelection(chatId, page);
            return;
        }

        // DELETE QUIZ (ADMIN ONLY)
        if (data.startsWith("DEL_")) {

            if (!adminService.isAdmin(chatId)) {
                sendMessage(chatId, "⛔ Unauthorized action.");
                return;
            }

            Long quizId = Long.parseLong(data.replace("DEL_", ""));
            quizService.deleteQuiz(quizId);

            sendMessage(chatId, "🗑️ Quiz deleted successfully!");
            int page = userPages.getOrDefault(chatId, 0);
            showQuizSelection(chatId, page);
            return;
        }

        // USER QUIZ FLOW
        if (data.startsWith("QUIZ_")) {
            Long quizId = Long.parseLong(data.replace("QUIZ_", ""));
            quizService.startQuiz(chatId, quizId);
            sendNextQuestion(chatId);
            return;
        }

        if (data.startsWith("ANS_")) {
            int selected = Integer.parseInt(data.replace("ANS_", ""));
            handleAnswer(chatId, callback.getMessage(), selected);
            return;
        }

        // ADMIN MENU FLOW
        if (adminService.isAdmin(chatId)) {

            if (data.equals("CREATE_QUIZ")) {
                AdminSession session = new AdminSession();
                session.setWaitingForTitle(true);
                adminSessions.put(chatId, session);
                sendMessage(chatId, "📝 Send quiz title:");
                return;
            }

            if (data.equals("ADD_ONE")) {
                AdminSession session = adminSessions.get(chatId);
                session.setAddingSingleQuestion(true);
                sendMessage(chatId, "❓ Send question text:");
                return;
            }

            if (data.equals("ADD_BULK")) {
                AdminSession session = adminSessions.get(chatId);
                session.setAddingBulkQuestions(true);
                sendMessage(chatId,
                        "📦 Send questions in format:\n" +
                                "Question?|A,B,C,D|0\n" +
                                "One question per line.");
                return;
            }

            if (data.equals("FINISH")) {
                adminSessions.remove(chatId);
                sendMessage(chatId, "✅ Quiz saved successfully!");
            }
        }
    }

    // ==========================================================
    // ================= ADMIN TEXT FLOW ========================
    // ==========================================================

    private void handleAdminText(Long chatId, String text) {

        AdminSession session = adminSessions.get(chatId);
        if (session == null) return;

        try {

            if (session.isWaitingForTitle()) {
                QuizEntity quiz = quizService.createQuiz(text, chatId);
                session.setQuizId(quiz.getId());
                session.setWaitingForTitle(false);
                sendAdminMenu(chatId);
                return;
            }

            if (session.isAddingSingleQuestion()) {

                if (session.getTempQuestion() == null) {
                    session.setTempQuestion(text);
                    sendMessage(chatId, "✏️ Send options: A,B,C,D");
                    return;
                }

                if (session.getTempOptions() == null) {
                    session.setTempOptions(text);
                    sendMessage(chatId, "✅ Send correct index (0-3)");
                    return;
                }

                int correct = Integer.parseInt(text);
                String[] options = session.getTempOptions().split(",");

                quizService.addQuestion(
                        session.getQuizId(),
                        session.getTempQuestion(),
                        options[0], options[1], options[2], options[3],
                        correct
                );

                session.setTempQuestion(null);
                session.setTempOptions(null);
                session.setAddingSingleQuestion(false);

                sendMessage(chatId, "✅ Question added!");
                sendAdminMenu(chatId);
                return;
            }

            if (session.isAddingBulkQuestions()) {

                String[] lines = text.split("\n");
                int count = 0;

                for (String line : lines) {
                    String[] parts = line.split("\\|");
                    String[] opts = parts[1].split(",");

                    quizService.addQuestion(
                            session.getQuizId(),
                            parts[0],
                            opts[0], opts[1], opts[2], opts[3],
                            Integer.parseInt(parts[2])
                    );
                    count++;
                }

                session.setAddingBulkQuestions(false);
                sendMessage(chatId, "✅ " + count + " questions added!");
                sendAdminMenu(chatId);
            }

        } catch (Exception e) {
            sendMessage(chatId, "❌ Invalid input. Try again.");
        }
    }

    // ==========================================================
    // ================= USER QUIZ FLOW =========================
    // ==========================================================

    private void sendNextQuestion(Long chatId) {

        QuestionEntity q = quizService.getNextQuestion(chatId);

        if (q == null) {
            int score = quizService.getScore(chatId);
            quizService.reset(chatId);
            sendMessage(chatId, "🏁 Quiz Finished!\nScore: " + score);
            return;
        }

        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(btn(q.getOption1(), "ANS_0")),
                List.of(btn(q.getOption2(), "ANS_1")),
                List.of(btn(q.getOption3(), "ANS_2")),
                List.of(btn(q.getOption4(), "ANS_3"))
        );

        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text("❓ " + q.getQuestion())
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build());
    }

    private void handleAnswer(Long chatId, Message message, int selected) {

        if (!answeringUsers.add(chatId)) return;

        try {
            boolean correct = quizService.checkAnswer(chatId, selected);
            String text = correct ? "✅ Correct!" : "❌ Wrong!";

            executeSafe(EditMessageText.builder()
                    .chatId(chatId.toString())
                    .messageId(message.getMessageId())
                    .text(text)
                    .build());

            sendNextQuestion(chatId);

        } catch (BadRequestException | QuizStateException ex) {
            sendMessage(chatId, "⚠️ " + ex.getMessage());
        } catch (Exception ex) {
            ex.printStackTrace();
            sendMessage(chatId, "🚨 Something went wrong.");
        } finally {
            answeringUsers.remove(chatId);
        }
    }

    // ==========================================================
    // ================= MENUS ==================================
    // ==========================================================

    private void sendStartMenu(Long chatId) {

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(btn("🎯 Take Quiz", "TAKE_QUIZ")));

        if (adminService.isAdmin(chatId)) {
            rows.add(List.of(btn("⚙️ Create Quiz", "CREATE_QUIZ")));
        }

        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Welcome! Choose option:")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build());
    }

    private void sendAdminMenu(Long chatId) {

        List<List<InlineKeyboardButton>> rows = List.of(
                List.of(btn("➕ Add One Question", "ADD_ONE")),
                List.of(btn("📦 Bulk Upload", "ADD_BULK")),
                List.of(btn("✅ Finish Quiz", "FINISH"))
        );

        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text("Admin Menu:")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build());
    }

    // ==========================================================
    // ================= QUIZ LIST ==============================
    // ==========================================================

    private void showQuizSelection(Long chatId, int page) {

        Page<QuizEntity> quizPage = quizService.getQuizPage(page, PAGE_SIZE);

        if (quizPage.isEmpty()) {
            sendMessage(chatId, "⚠️ No quizzes available.");
            return;
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (QuizEntity quiz : quizPage.getContent()) {

            InlineKeyboardButton playBtn =
                    btn("📘 " + quiz.getTitle(), "QUIZ_" + quiz.getId());

            if (adminService.isAdmin(chatId)) {
                rows.add(List.of(playBtn, btn("❌ Delete", "DEL_" + quiz.getId())));
            } else {
                rows.add(List.of(playBtn));
            }
        }

        List<InlineKeyboardButton> nav = new ArrayList<>();

        if (quizPage.hasPrevious()) {
            nav.add(btn("◀️ Prev", "PAGE_" + (page - 1)));
        }

        if (quizPage.hasNext()) {
            nav.add(btn("▶️ Next", "PAGE_" + (page + 1)));
        }

        if (!nav.isEmpty()) {
            rows.add(nav);
        }

        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text("📚 Select a quiz (Page " + (page + 1) + ")")
                .replyMarkup(new InlineKeyboardMarkup(rows))
                .build());
    }

    // ==========================================================
    // ================= UTIL ===================================
    // ==========================================================

    private InlineKeyboardButton btn(String text, String data) {
        InlineKeyboardButton b = new InlineKeyboardButton(text);
        b.setCallbackData(data);
        return b;
    }

    private void executeSafe(Object method) {
        try {
            if (method instanceof SendMessage m) execute(m);
            if (method instanceof EditMessageText m) execute(m);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(Long chatId, String text) {
        executeSafe(SendMessage.builder()
                .chatId(chatId.toString())
                .text(text)
                .build());
    }
}
