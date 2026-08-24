package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.Debt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DebtRepository extends JpaRepository<Debt, Long> {
    List<Debt> findByOwnerOrderByIdDesc(AppUser owner);
    Optional<Debt> findByIdAndOwner(Long id, AppUser owner);
}
