package com.speedlink.app.repository;

import com.speedlink.app.entity.ConversationSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationSessionRepository extends JpaRepository<ConversationSession, String> {
    List<ConversationSession> findTop50ByOrderByStartedAtDesc();
}
