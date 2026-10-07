package com.finanzero.repository;

import com.finanzero.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findLockedById(@org.springframework.data.repository.query.Param("id") Long id);
    Optional<AppUser> findByEmailIgnoreCase(String email);
    Optional<AppUser> findByAuthToken(String authToken);
    boolean existsByEmailIgnoreCase(String email);
}
