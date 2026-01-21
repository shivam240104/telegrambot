package com.example.telegrambot.repository;

import com.example.telegrambot.entity.QuizEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuizRepository extends JpaRepository<QuizEntity, Long> {
    List<QuizEntity> findByActiveTrue();
}
