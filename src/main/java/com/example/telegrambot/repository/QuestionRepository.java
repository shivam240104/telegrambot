package com.example.telegrambot.repository;

import com.example.telegrambot.entity.QuestionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<QuestionEntity, Long> {
    List<QuestionEntity> findByQuizId(Long quizId);
    List<QuestionEntity> findByQuizIdOrderByIdAsc(Long quizId);

    void deleteByQuizId(Long quizId);

}
