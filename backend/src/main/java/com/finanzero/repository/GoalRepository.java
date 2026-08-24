package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findByOwnerOrderByIdDesc(AppUser owner);
    Optional<Goal> findByIdAndOwner(Long id, AppUser owner);
}
