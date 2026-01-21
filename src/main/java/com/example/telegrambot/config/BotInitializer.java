package com.example.telegrambot.config;

import com.example.telegrambot.bot.QuizBot;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotInitializer {

    private final QuizBot quizBot;

    public BotInitializer(QuizBot quizBot) {
        this.quizBot = quizBot;
    }

    @PostConstruct
    public void start() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(quizBot);
            System.out.println("✅ Telegram Bot Registered Successfully!");
        } catch (Exception e) {
            System.err.println("❌ Failed to register bot:");
            e.printStackTrace();
        }
    }
}
