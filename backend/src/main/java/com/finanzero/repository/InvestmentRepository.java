package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.Investment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvestmentRepository extends JpaRepository<Investment, Long> {
    List<Investment> findByOwnerOrderByIdDesc(AppUser owner);
    Optional<Investment> findByIdAndOwner(Long id, AppUser owner);
}
