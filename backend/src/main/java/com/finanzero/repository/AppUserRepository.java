package com.finanzero.repository;

import com.finanzero.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByAuthToken(String authToken);
    boolean existsByEmailIgnoreCase(String email);
}
