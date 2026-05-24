package com.speedlink.app.repository;

import com.speedlink.app.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, String> {
    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findBySupabaseUserId(String supabaseUserId);
}
