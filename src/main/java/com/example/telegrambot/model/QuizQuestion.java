package com.example.telegrambot.model;

import java.util.List;

public class QuizQuestion {

    private String question;
    private List<String> options;
    private int correctIndex;

    public QuizQuestion(String question, List<String> options, int correctIndex) {
        this.question = question;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    public String getQuestion() {
        return question;
    }

    public List<String> getOptions() {
        return options;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }
}
