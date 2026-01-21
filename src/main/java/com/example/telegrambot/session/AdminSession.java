package com.example.telegrambot.session;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AdminSession {

    private Long quizId;

    private boolean waitingForTitle;

    private boolean addingSingleQuestion;
    private boolean addingBulkQuestions;

    // Temporary fields for single-question flow
    private String tempQuestion;
    private String tempOptions;
}
