package com.demo.accessiblenav.auth;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class UserAccountService {

    private final UserAccountRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserAccount register(String username, String rawPassword) {
        if (repository.existsByUsername(username)) {
            throw new IllegalArgumentException("username already exists");
        }
        UserAccount user = new UserAccount();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(UserRole.USER);
        user.setCreatedAt(Instant.now());
        user.setCreditScore(0);
        return repository.save(user);
    }

    @Transactional
    public UserAccount createAdminIfMissing(String username, String rawPassword) {
        UserAccount existing = repository.findByUsername(username).orElse(null);
        if (existing != null) {
            return existing;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(rawPassword));
        admin.setRole(UserRole.ADMIN);
        admin.setCreatedAt(Instant.now());
        admin.setCreditScore(0);
        return repository.save(admin);
    }

    @Transactional
    public UserAccount recordSuccessfulLogin(UserAccount user) {
        Integer score = user.getCreditScore();
        int current = score == null ? 0 : score;

        // 规则：游客/未登录过=最低(0)；首次登录直接提升到基础分；后续每次登录小幅增长
        if (user.getLastLoginAt() == null) {
            current = Math.max(current, 20);
        } else {
            current = Math.min(100, current + 1);
        }

        user.setCreditScore(current);
        user.setLastLoginAt(Instant.now());
        return repository.save(user);
    }

    @Transactional
    public UserAccount applyCreditDeltaByUsername(String username, int delta) {
        if (username == null || username.trim().isEmpty() || delta == 0) {
            return null;
        }
        UserAccount user = repository.findByUsername(username.trim()).orElse(null);
        if (user == null) {
            return null;
        }

        Integer score = user.getCreditScore();
        int current = score == null ? 0 : score;
        int next = current + delta;
        if (next < 0) next = 0;
        if (next > 100) next = 100;

        user.setCreditScore(next);
        return repository.save(user);
    }

    @Transactional(readOnly = true)
    public UserAccount findByUsername(String username) {
        return repository.findByUsername(username).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<UserAccount> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public UserAccount findById(Long id) {
        if (id == null) {
            return null;
        }
        return repository.findById(id).orElse(null);
    }

    @Transactional
    public UserAccount updateRole(Long userId, UserRole role) {
        if (userId == null) {
            throw new IllegalArgumentException("user id cannot be null");
        }
        UserAccount user = repository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("user not found"));
        user.setRole(role);
        return repository.save(user);
    }
}
