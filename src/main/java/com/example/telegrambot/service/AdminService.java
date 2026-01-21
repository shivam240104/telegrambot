package com.example.telegrambot.service;

import com.example.telegrambot.entity.AdminEntity;
import com.example.telegrambot.repository.AdminRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class AdminService {

    private final AdminRepository repo;

    @Value("${telegram.bot.root-admin-ids}")
    private String rootAdmins;

    private Set<Long> rootAdminSet;

    public AdminService(AdminRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void init() {
        rootAdminSet = Arrays.stream(rootAdmins.split(","))
                .map(String::trim)
                .map(Long::parseLong)
                .collect(Collectors.toSet());
    }

    // Anyone who is root OR in DB is admin
    public boolean isAdmin(Long chatId) {
        return rootAdminSet.contains(chatId) || repo.existsById(chatId);
    }

    // Only hardcoded admins
    public boolean isRootAdmin(Long chatId) {
        return rootAdminSet.contains(chatId);
    }

    // Only root admins should call this
    public void addAdmin(Long chatId) {
        repo.save(new AdminEntity(chatId));
    }

    public void removeAdmin(Long chatId) {
        repo.deleteById(chatId);
    }

    public List<AdminEntity> getAllAdmins() {
        return repo.findAll();
    }
}
