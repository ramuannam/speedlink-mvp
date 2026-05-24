package com.speedlink.app.repository;

import com.speedlink.app.entity.UserSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface UserSuggestionRepository extends JpaRepository<UserSuggestion, String> {
    List<UserSuggestion> findTop100ByOrderByCreatedAtDesc();

    List<UserSuggestion> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);
}
