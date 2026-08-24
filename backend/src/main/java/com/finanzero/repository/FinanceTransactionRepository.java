package com.finanzero.repository;

import com.finanzero.model.AppUser;
import com.finanzero.model.FinanceTransaction;
import com.finanzero.model.ReimbursementStatus;
import com.finanzero.model.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {
    List<FinanceTransaction> findByOwnerOrderByDateDesc(AppUser owner);
    Optional<FinanceTransaction> findByIdAndOwner(Long id, AppUser owner);
    List<FinanceTransaction> findByOwnerAndDateBetweenOrderByDateDesc(AppUser owner, LocalDate start, LocalDate end);
    List<FinanceTransaction> findByOwnerAndTypeAndDateBetweenOrderByDateDesc(AppUser owner, TransactionType type, LocalDate start, LocalDate end);
    List<FinanceTransaction> findByOwnerAndReimbursableTrueOrderByDateDesc(AppUser owner);
    List<FinanceTransaction> findByOwnerAndReimbursableTrueAndReimbursementStatusOrderByDateDesc(AppUser owner, ReimbursementStatus status);
}
